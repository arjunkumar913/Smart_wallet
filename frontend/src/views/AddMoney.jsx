import { useRef, useState } from 'react';
import { api } from '../api.js';
import { money, newKey } from '../format.js';

const QUICK_AMOUNTS = [100, 500, 1000, 2000];
const MAX = 50000;

export default function AddMoney({ go }) {
  const [amount, setAmount] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  // One key per amount the user tries to pay. If the request has to be retried (slow network,
  // double click), the same key goes out again and the server returns the same payment order.
  const attempt = useRef({ amount: null, key: null });

  const value = Number(amount);
  const valid = amount !== '' && Number.isFinite(value) && value >= 1 && value <= MAX
    && /^\d+(\.\d{1,2})?$/.test(amount);

  async function submit(event) {
    event.preventDefault();
    if (!valid || busy) return;
    if (attempt.current.amount !== amount) {
      attempt.current = { amount, key: newKey() };
    }
    setBusy(true);
    setError(null);
    try {
      const topUp = await api.createTopUp(value, attempt.current.key);
      go('topup', { id: topUp.topUpId });
    } catch (e) {
      setError(e.message);
      setBusy(false);
    }
  }

  return (
    <form className="panel narrow" onSubmit={submit} noValidate>
      <button type="button" className="link back" onClick={() => go('home')}>Back to passbook</button>
      <h1>Add money</h1>
      <label className="field">
        <span>Amount in rupees</span>
        <div className="amount-input">
          <span aria-hidden="true">₹</span>
          <input inputMode="decimal" autoFocus placeholder="0" value={amount} aria-describedby="amount-help"
                 onChange={(e) => setAmount(e.target.value.replace(/[^\d.]/g, ''))} />
        </div>
        <small id="amount-help">Between {money(1)} and {money(MAX)}.</small>
      </label>
      <div className="chips" aria-label="Quick amounts">
        {QUICK_AMOUNTS.map((quick) => (
          <button type="button" key={quick} className="chip" onClick={() => setAmount(String(quick))}>
            {money(quick).replace('.00', '')}
          </button>
        ))}
      </div>
      {error && <p className="message problem" role="alert">{error}</p>}
      <button type="submit" className="button primary wide" disabled={!valid || busy}>
        {busy ? 'Creating the payment…' : valid ? `Continue to pay ${money(value)}` : 'Continue to pay'}
      </button>
      <p className="hint">Your balance changes only after the bank confirms the payment.</p>
    </form>
  );
}
