import { useCallback, useEffect, useRef, useState } from 'react';
import { api } from '../api.js';
import { clock, figure, money, shortDay } from '../format.js';

const PAGE_SIZE = 10;

export default function Home({ go }) {
  const [wallet, setWallet] = useState(null);
  const [walletState, setWalletState] = useState('loading'); // loading | creating | ready | error
  const [error, setError] = useState(null);
  const [entries, setEntries] = useState([]);
  const [paging, setPaging] = useState({ page: 0, totalPages: 0, loading: true });
  const [tab, setTab] = useState('passbook');
  const [alerts, setAlerts] = useState(null);
  const alive = useRef(true);

  const loadEntries = useCallback(async (page) => {
    setPaging((p) => ({ ...p, loading: true }));
    try {
      const result = await api.transactions(page, PAGE_SIZE);
      if (!alive.current) return;
      setEntries((current) => (page === 0 ? result.content : [...current, ...result.content]));
      setPaging({ page, totalPages: result.totalPages, loading: false });
    } catch (e) {
      if (alive.current) setPaging((p) => ({ ...p, loading: false }));
    }
  }, []);

  // A wallet is created a moment after registration (the auth service announces the new user and
  // the wallet service reacts), so a brand-new account may need a few tries.
  const loadWallet = useCallback(async () => {
    setError(null);
    for (let attempt = 0; attempt < 30 && alive.current; attempt += 1) {
      try {
        const result = await api.wallet({ quiet: attempt > 0 });
        if (!alive.current) return;
        setWallet(result);
        setWalletState('ready');
        loadEntries(0);
        return;
      } catch (e) {
        if (!alive.current) return;
        if (e.code === 'WALLET_NOT_FOUND') {
          setWalletState('creating');
          await new Promise((resolve) => setTimeout(resolve, 2000));
        } else {
          setError(e.message);
          setWalletState('error');
          return;
        }
      }
    }
    if (alive.current) {
      setError('Your wallet is taking longer than usual to set up.');
      setWalletState('error');
    }
  }, [loadEntries]);

  useEffect(() => {
    alive.current = true;
    loadWallet();
    return () => { alive.current = false; };
  }, [loadWallet]);

  useEffect(() => {
    if (tab !== 'alerts') return;
    let current = true;
    api.notifications()
      .then((result) => current && setAlerts(result.content))
      .catch(() => current && setAlerts([]));
    return () => { current = false; };
  }, [tab]);

  if (walletState === 'loading') return <p className="quiet">Opening your wallet…</p>;
  if (walletState === 'creating') return <p className="quiet" role="status">Setting up your wallet. This takes a few seconds.</p>;
  if (walletState === 'error') {
    return (
      <div className="panel">
        <p className="message problem" role="alert">{error}</p>
        <button type="button" className="button secondary" onClick={() => { setWalletState('loading'); loadWallet(); }}>Try again</button>
      </div>
    );
  }

  const frozen = wallet.status !== 'ACTIVE';

  return (
    <>
      <section className="balance" aria-label="Balance">
        <p className="balance-label">Available balance</p>
        <p className="balance-figure">{money(wallet.balance)}</p>
        {frozen && <p className="message problem">This wallet is frozen. Money cannot be added or sent until it is unfrozen.</p>}
        <div className="actions">
          <button type="button" className="button primary" onClick={() => go('add')} disabled={frozen}>Add money</button>
          <button type="button" className="button secondary" onClick={() => go('send')}
                  disabled={frozen || Number(wallet.balance) <= 0}>Send money</button>
        </div>
      </section>

      <section className="book">
        <div className="tabs" role="tablist">
          <button type="button" role="tab" aria-selected={tab === 'passbook'} onClick={() => setTab('passbook')}>Passbook</button>
          <button type="button" role="tab" aria-selected={tab === 'alerts'} onClick={() => setTab('alerts')}>Alerts</button>
        </div>

        {tab === 'passbook' && (
          <div role="tabpanel">
            {entries.length === 0 && !paging.loading && (
              <p className="empty">No entries yet. Add money to write the first line.</p>
            )}
            {entries.length > 0 && (
              <table className="passbook">
                <thead>
                  <tr>
                    <th scope="col">Date</th>
                    <th scope="col">Particulars</th>
                    <th scope="col" className="num">Withdrawn</th>
                    <th scope="col" className="num">Deposited</th>
                    <th scope="col" className="num">Balance</th>
                  </tr>
                </thead>
                <tbody>
                  {entries.map((entry, index) => {
                    const out = entry.direction === 'DEBIT';
                    return (
                      <tr key={`${entry.transactionId}-${entry.direction}-${index}`}>
                        <td className="when">
                          <span>{shortDay(entry.createdAt)}</span>
                          <span className="time">{clock(entry.createdAt)}</span>
                        </td>
                        <td className="what">{entry.description}</td>
                        <td className="num withdrawn" data-label="Withdrawn">{out ? figure(entry.amount) : ''}</td>
                        <td className="num deposited" data-label="Deposited">{out ? '' : figure(entry.amount)}</td>
                        <td className="num running" data-label="Balance">{figure(entry.balanceAfter)}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            )}
            {paging.loading && <p className="quiet">Reading the passbook…</p>}
            {!paging.loading && paging.page + 1 < paging.totalPages && (
              <button type="button" className="button secondary more" onClick={() => loadEntries(paging.page + 1)}>
                Show older entries
              </button>
            )}
          </div>
        )}

        {tab === 'alerts' && (
          <div role="tabpanel">
            {alerts === null && <p className="quiet">Loading alerts…</p>}
            {alerts?.length === 0 && <p className="empty">No alerts yet. You get one whenever money arrives or leaves.</p>}
            {alerts?.length > 0 && (
              <ul className="alerts">
                {alerts.map((alert) => (
                  <li key={alert.id}>
                    <span>{alert.message}</span>
                    <time dateTime={alert.createdAt}>{shortDay(alert.createdAt)}, {clock(alert.createdAt)}</time>
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}
      </section>
    </>
  );
}
