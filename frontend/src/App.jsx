import { useCallback, useEffect, useState } from 'react';
import { configure, setToken } from './api.js';
import SignIn from './views/SignIn.jsx';
import Home from './views/Home.jsx';
import AddMoney from './views/AddMoney.jsx';
import TopUp from './views/TopUp.jsx';
import SendMoney from './views/SendMoney.jsx';
import Starfield from './Starfield.jsx';
import ServerStatus from './ServerStatus.jsx';

const SESSION_KEY = 'smart-wallet-session';

function readSession() {
  try {
    const saved = JSON.parse(sessionStorage.getItem(SESSION_KEY));
    return saved?.token ? saved : null;
  } catch {
    return null;
  }
}

function writeSession(session) {
  try {
    if (session) sessionStorage.setItem(SESSION_KEY, JSON.stringify(session));
    else sessionStorage.removeItem(SESSION_KEY);
  } catch {
    // storage unavailable (private window): the session simply lasts until the tab is reloaded
  }
}

export default function App() {
  const [session, setSession] = useState(() => {
    const saved = readSession();
    if (saved) setToken(saved.token);
    return saved;
  });
  const [view, setView] = useState({ name: 'home' });
  const [notice, setNotice] = useState(null);
  const [slow, setSlow] = useState(false);
  const [serversReady, setServersReady] = useState(true);

  const signOut = useCallback((message = null) => {
    setToken(null);
    writeSession(null);
    setSession(null);
    setView({ name: 'home' });
    setNotice(message);
  }, []);

  useEffect(() => {
    configure({
      unauthorized: () => signOut('Your session ended. Sign in again to continue.'),
      slowChange: setSlow,
    });
  }, [signOut]);

  const signIn = (auth) => {
    const next = { token: auth.accessToken, email: auth.email };
    setToken(next.token);
    writeSession(next);
    setSession(next);
    setNotice(null);
  };

  const go = (name, params = {}) => {
    setView({ name, ...params });
    window.scrollTo(0, 0);
  };

  return (
    <>
    <Starfield />
    <div className="page">
      <header className="masthead">
        <button type="button" className="wordmark" onClick={() => session && go('home')}>
          Smart Wallet
        </button>
        {session && (
          <div className="account">
            <span className="account-email">{session.email}</span>
            <button type="button" className="link" onClick={() => signOut()}>Sign out</button>
          </div>
        )}
      </header>

      <ServerStatus onReadyChange={setServersReady} />

      {slow && serversReady && (
        <p className="waking" role="status">
          Waking the server. It sleeps when nobody is using it, so the first request can take up to a minute.
        </p>
      )}

      <main>
        {!session && <SignIn onSignedIn={signIn} notice={notice} />}
        {session && view.name === 'home' && <Home go={go} />}
        {session && view.name === 'add' && <AddMoney go={go} />}
        {session && view.name === 'topup' && <TopUp key={view.id} id={view.id} email={session.email} go={go} />}
        {session && view.name === 'send' && <SendMoney go={go} />}
      </main>

      <footer className="colophon">
        A demo project by Vardhankumar Mekala. Java 21 and Spring Boot microservices with a double-entry ledger.{' '}
        <a href="https://github.com/arjunkumar913/Smart_wallet" target="_blank" rel="noreferrer">Source on GitHub</a>
      </footer>
    </div>
    </>
  );
}

