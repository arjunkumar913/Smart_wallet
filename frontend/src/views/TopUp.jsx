import { useCallback, useEffect, useRef, useState } from 'react';
import { api } from '../api.js';
import { dateTime, money } from '../format.js';
import { isTestKey, openRazorpay } from '../razorpay.js';
import { celebrateTransfer } from '../Starfield.jsx';

const POLL_MS = 2000;
// If the bank's confirmation has not reached the server after this long, ask the bank directly.
const ASK_BANK_AFTER_MS = 8000;

const FINISHED = ['SUCCESS', 'FAILED', 'EXPIRED'];

export default function TopUp({ id, email, go }) {
  const [topUp, setTopUp] = useState(null);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  // "watching" = the customer has paid (or says so) and we are waiting for the server to hear from the bank
  const [watching, setWatching] = useState(false);
  const [askingBank, setAskingBank] = useState(false);
  const [balance, setBalance] = useState(null);
  const openedCheckout = useRef(false);

  const finished = topUp && FINISHED.includes(topUp.status);
  const viaRazorpay = topUp?.provider === 'RAZORPAY';

  useEffect(() => {
    let current = true;
    api.topUp(id)
      .then((result) => current && setTopUp(result))
      .catch((e) => current && setError(e.message));
    return () => { current = false; };
  }, [id]);

  // The browser never decides that a payment succeeded. It keeps asking the server, and the server
  // answers SUCCESS only after the bank's signed confirmation has credited the wallet.
  useEffect(() => {
    if (!watching || finished) return undefined;
    const startedAt = Date.now();
    let current = true;
    const timer = setInterval(async () => {
      try {
        const lateEnough = Date.now() - startedAt > ASK_BANK_AFTER_MS;
        if (lateEnough) setAskingBank(true);
        const result = lateEnough ? await api.syncTopUp(id) : await api.topUp(id);
        if (current) setTopUp(result);
      } catch {
        // a failed poll is not a failed payment: keep asking
      }
    }, POLL_MS);
    return () => { current = false; clearInterval(timer); };
  }, [watching, finished, id]);

  useEffect(() => {
    if (topUp?.status !== 'SUCCESS') return;
    celebrateTransfer();
    let current = true;
    api.wallet({ quiet: true }).then((wallet) => current && setBalance(wallet.balance)).catch(() => {});
    return () => { current = false; };
  }, [topUp?.status]);

  const payWithRazorpay = useCallback(async () => {
    if (!topUp?.checkout) return;
    setError(null);
    setBusy(true);
    try {
      await openRazorpay({
        checkout: topUp.checkout,
        email,
        onPaid: async ({ paymentId, signature }) => {
          setWatching(true);
          try {
            setTopUp(await api.confirmTopUp(id, paymentId, signature));
          } catch {
            // the webhook or the reconciliation job will still complete it; polling shows the result
          }
        },
        onDismiss: () => setBusy(false),
      });
    } catch (e) {
      setError(e.message);
      setBusy(false);
    }
  }, [topUp, email, id]);

  // Arriving from "Add money" with a Razorpay order: open the payment window straight away, once.
  useEffect(() => {
    if (viaRazorpay && topUp.status === 'PENDING' && !openedCheckout.current) {
      openedCheckout.current = true;
      payWithRazorpay();
    }
  }, [viaRazorpay, topUp?.status, payWithRazorpay]);

  async function payAtDemoBank(outcome) {
    setBusy(true);
    setError(null);
    try {
      const result = await api.demoBankPay(id, outcome);
      setTopUp(result);
      setWatching(true);
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }

  if (error && !topUp) {
    return (
      <div className="panel narrow">
        <p className="message problem" role="alert">{error}</p>
        <button type="button" className="button secondary" onClick={() => go('home')}>Back to passbook</button>
      </div>
    );
  }
  if (!topUp) return <p className="quiet">Loading the payment…</p>;

  const paying = topUp.status === 'PENDING' && !watching;
  const bankStep = topUp.status === 'SUCCESS' ? 'done'
    : topUp.status === 'FAILED' || topUp.status === 'EXPIRED' ? 'failed'
    : watching ? 'active' : 'waiting';

  return (
    <div className="panel narrow">
      <button type="button" className="link back" onClick={() => go('home')}>Back to passbook</button>
      <h1>
        {topUp.status === 'SUCCESS' && `${money(topUp.amount)} added`}
        {topUp.status === 'FAILED' && 'Payment failed'}
        {topUp.status === 'EXPIRED' && 'Payment expired'}
        {topUp.status === 'PENDING' && (watching ? 'Confirming your payment' : `Pay ${money(topUp.amount)}`)}
        {topUp.status === 'CREATED' && 'Creating the payment'}
      </h1>

      {topUp.status === 'SUCCESS' && (
        <p className="message good" role="status">
          The bank confirmed your payment and the money is in your wallet.
          {balance !== null && ` Your balance is ${money(balance)}.`}
        </p>
      )}
      {(topUp.status === 'FAILED' || topUp.status === 'EXPIRED') && (
        <p className="message problem" role="alert">
          {topUp.failureReason || 'The payment did not go through.'} Nothing was added to your wallet.
        </p>
      )}
      {topUp.status === 'PENDING' && watching && (
        <p className="message waiting" role="status">
          {askingBank
            ? 'The bank\'s confirmation is late, so we are asking the bank directly. Keep this page open.'
            : 'Waiting for the bank to confirm. This usually takes a few seconds.'}
        </p>
      )}

      {paying && viaRazorpay && (
        <div className="pay">
          {isTestKey(topUp.checkout?.keyId) && (
            <p className="message waiting">
              Test mode: no real money moves. In the payment window choose UPI and enter <strong>success@razorpay</strong>.
            </p>
          )}
          <button type="button" className="button primary wide" onClick={payWithRazorpay} disabled={busy}>
            {busy ? 'Payment window is open…' : 'Open the payment window'}
          </button>
        </div>
      )}

      {paying && !viaRazorpay && (
        <div className="bank">
          <div className="bank-head">
            <strong>Demo Bank</strong>
            <span>Practice payment page. No real money moves.</span>
          </div>
          <p>Smart Wallet is asking for <strong>{money(topUp.amount)}</strong>. What should the bank do?</p>
          <div className="bank-choices">
            <button type="button" className="button primary" onClick={() => payAtDemoBank('SUCCESS')} disabled={busy}>
              Approve payment
            </button>
            <button type="button" className="button secondary" onClick={() => payAtDemoBank('FAILURE')} disabled={busy}>
              Decline payment
            </button>
          </div>
          <button type="button" className="link" onClick={() => payAtDemoBank('SUCCESS_WEBHOOK_LOST')} disabled={busy}>
            Approve, but lose the bank's confirmation message
          </button>
          <p className="hint">
            The last option takes the money without telling the wallet. The wallet notices on its own
            by asking the bank, and still credits you exactly once.
          </p>
        </div>
      )}

      {error && <p className="message problem" role="alert">{error}</p>}

      <ol className="steps">
        <li className="done">
          <span className="step-title">Payment order created</span>
          <span className="step-note">{dateTime(topUp.createdAt)}</span>
        </li>
        <li className={bankStep}>
          <span className="step-title">Bank confirms the payment</span>
          <span className="step-note">
            {bankStep === 'done' && (topUp.paymentMethod ? `Paid by ${topUp.paymentMethod.toUpperCase()}` : 'Confirmed')}
            {bankStep === 'failed' && 'Not confirmed'}
            {bankStep === 'active' && 'Waiting…'}
            {bankStep === 'waiting' && 'After you pay'}
          </span>
        </li>
        <li className={topUp.status === 'SUCCESS' ? 'done' : bankStep === 'failed' ? 'skipped' : 'waiting'}>
          <span className="step-title">Wallet credited</span>
          <span className="step-note">
            {topUp.status === 'SUCCESS' ? dateTime(topUp.completedAt) : bankStep === 'failed' ? 'Not credited' : 'Only after the bank confirms'}
          </span>
        </li>
      </ol>

      <dl className="details">
        <div><dt>Amount</dt><dd>{money(topUp.amount)}</dd></div>
        <div><dt>Reference</dt><dd>{topUp.topUpId}</dd></div>
        {topUp.transactionId && <div><dt>Passbook entry</dt><dd>{topUp.transactionId}</dd></div>}
      </dl>

      {topUp.status === 'SUCCESS' && (
        <button type="button" className="button primary wide" onClick={() => go('home')}>See it in the passbook</button>
      )}
      {(topUp.status === 'FAILED' || topUp.status === 'EXPIRED') && (
        <button type="button" className="button primary wide" onClick={() => go('add')}>Try again</button>
      )}
    </div>
  );
}
