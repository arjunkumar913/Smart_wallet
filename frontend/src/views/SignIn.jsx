import { useState } from 'react';
import { api } from '../api.js';

export default function SignIn({ onSignedIn, notice }) {
  const [mode, setMode] = useState('signin');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(null);
  const [error, setError] = useState(null);

  const creating = mode === 'create';

  async function submit(event) {
    event.preventDefault();
    setBusy('form');
    setError(null);
    try {
      const auth = creating ? await api.register(email.trim(), password) : await api.login(email.trim(), password);
      onSignedIn(auth);
    } catch (e) {
      setError(e.fieldErrors?.password ? `Password ${e.fieldErrors.password}.` : e.message);
    } finally {
      setBusy(null);
    }
  }

  // One click for visitors who only want to look around: a throwaway account with a random password.
  async function openDemoAccount() {
    setBusy('demo');
    setError(null);
    try {
      const id = crypto.randomUUID().slice(0, 8);
      const auth = await api.register(`guest-${id}@demo.smartwallet.dev`, crypto.randomUUID());
      onSignedIn(auth);
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(null);
    }
  }

  return (
    <section className="signin">
      <div className="signin-intro">
        <h1>Money in, money out, every entry on the page.</h1>
        <p>
          Add money through a payment gateway, send it to another wallet, and watch each
          movement land in your passbook the moment the bank confirms it.
        </p>
        <button type="button" className="button primary" onClick={openDemoAccount} disabled={busy !== null}>
          {busy === 'demo' ? 'Opening a demo account…' : 'Open a demo account'}
        </button>
        <p className="hint">No sign-up needed. You get a fresh wallet with a zero balance.</p>
      </div>

      <form className="panel" onSubmit={submit} noValidate>
        <h2>{creating ? 'Create your account' : 'Sign in'}</h2>
        {notice && <p className="message waiting" role="status">{notice}</p>}
        <label className="field">
          <span>Email</span>
          <input type="email" autoComplete="email" required value={email}
                 onChange={(e) => setEmail(e.target.value)} />
        </label>
        <label className="field">
          <span>Password</span>
          <input type="password" autoComplete={creating ? 'new-password' : 'current-password'} required
                 minLength={creating ? 8 : undefined} value={password}
                 onChange={(e) => setPassword(e.target.value)} />
          {creating && <small>At least 8 characters.</small>}
        </label>
        {error && <p className="message problem" role="alert">{error}</p>}
        <button type="submit" className="button secondary" disabled={busy !== null || !email || !password}>
          {busy === 'form' ? 'One moment…' : creating ? 'Create account' : 'Sign in'}
        </button>
        <p className="switch">
          {creating ? 'Already have an account?' : 'New here?'}{' '}
          <button type="button" className="link" onClick={() => { setMode(creating ? 'signin' : 'create'); setError(null); }}>
            {creating ? 'Sign in' : 'Create an account'}
          </button>
        </p>
      </form>
    </section>
  );
}
