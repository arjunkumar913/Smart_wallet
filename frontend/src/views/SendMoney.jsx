import { useRef, useState } from 'react';
import { api } from '../api.js';
import { celebrateTransfer } from '../Starfield.jsx';
import { money, newKey } from '../format.js';

export default function SendMoney({ go }) {
  const [toEmail, setToEmail] = useState('');
  const [amount, setAmount] = useState('');
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [sent, setSent] = useState(null);
  // The key stays the same for as long as the form does, so pressing Send twice moves money once.
  const attempt = useRef({ fingerprint: null, key: null });

  const value = Number(amount);
  const valid = /\S+@\S+\.\S+/.test(toEmail) && /^\d+(\.\d{1,2})?$/.test(amount) && value >= 0.01;

  async function submit(event) {
    event.preventDefault();
    if (!valid || busy) return;
    const fingerprint = `${toEmail.trim().toLowerCase()}|${amount}|${note}`;
    if (attempt.current.fingerprint !== fingerprint) {
      attempt.current = { fingerprint, key: newKey() };
    }
    setBusy(true);
    setError(null);
    try {
      setSent(await api.transfer(toEmail.trim(), value, note.trim(), attempt.current.key));
      celebrateTransfer();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }

  if (sent) {
    return (
      <div className="panel narrow">
        <h1>Sent {money(sent.amount)}</h1>
        <p>{sent.toEmail} has the money now. Your balance is {money(sent.balanceAfter)}.</p>
        <dl className="details">
          <div><dt>Reference</dt><dd>{sent.transactionId}</dd></div>
        </dl>
        <button type="button" className="button primary wide" onClick={() => go('home')}>Back to passbook</button>
      </div>
    );
  }

  return (
    <form className="panel narrow" onSubmit={submit} noValidate>
      <button type="button" className="link back" onClick={() => go('home')}>Back to passbook</button>
      <h1>Send money</h1>
      <label className="field">
        <span>Recipient's email</span>
        <input type="email" autoFocus value={toEmail} onChange={(e) => setToEmail(e.target.value)}
               aria-describedby="recipient-help" />
        <small id="recipient-help">They need a Smart Wallet account. To try it, open a second demo account in another tab.</small>
      </label>
      <label className="field">
        <span>Amount in rupees</span>
        <div className="amount-input">
          <span aria-hidden="true">₹</span>
          <input inputMode="decimal" placeholder="0" value={amount}
                 onChange={(e) => setAmount(e.target.value.replace(/[^\d.]/g, ''))} />
        </div>
      </label>
      <label className="field">
        <span>Note (optional)</span>
        <input maxLength={140} value={note} onChange={(e) => setNote(e.target.value)} />
      </label>
      {error && <p className="message problem" role="alert">{error}</p>}
      <button type="submit" className="button primary wide" disabled={!valid || busy}>
        {busy ? 'Sending…' : valid ? `Send ${money(value)}` : 'Send'}
      </button>
    </form>
  );
}
