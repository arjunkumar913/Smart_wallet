import { useEffect, useRef, useState } from 'react';
import { services, wakeAll } from './wake.js';

// Show the panel only if the servers are not ready almost at once, so a warm visit never sees it flash.
const SHOW_AFTER_MS = 1200;
// After this long hidden, the services may have gone back to sleep: wake them again when the tab returns.
const ASLEEP_AFTER_MS = 14 * 60 * 1000;

export default function ServerStatus({ onReadyChange }) {
  const list = useRef(services()).current;
  const [status, setStatus] = useState({});
  const [visible, setVisible] = useState(false);
  const [round, setRound] = useState(0);
  const lastReady = useRef(0);
  const readyRef = useRef(false);

  const keys = Object.keys(status);
  const ready = keys.length > 0 && keys.every((k) => status[k] === 'up');
  const failed = keys.some((k) => status[k] === 'failed');

  useEffect(() => {
    if (!list.length) return undefined;
    const stop = wakeAll(list, setStatus);
    readyRef.current = false;
    const timer = setTimeout(() => { if (!readyRef.current) setVisible(true); }, SHOW_AFTER_MS);
    return () => { stop(); clearTimeout(timer); };
  }, [list, round]);

  useEffect(() => {
    onReadyChange?.(list.length === 0 || ready);
    readyRef.current = ready;
    if (!ready) return undefined;
    lastReady.current = Date.now();
    const timer = setTimeout(() => setVisible(false), 2500);   // let "ready" show for a moment, then step aside
    return () => clearTimeout(timer);
  }, [ready, list.length, onReadyChange]);

  useEffect(() => {
    const onVisible = () => {
      if (document.visibilityState === 'visible' && lastReady.current && Date.now() - lastReady.current > ASLEEP_AFTER_MS) {
        lastReady.current = 0;
        setVisible(false);
        setStatus({});
        setRound((r) => r + 1);
      }
    };
    document.addEventListener('visibilitychange', onVisible);
    return () => document.removeEventListener('visibilitychange', onVisible);
  }, []);

  if (!list.length || !visible || !keys.length) return null;

  const upCount = keys.filter((k) => status[k] === 'up').length;
  let title = 'Starting the servers';
  let note = 'This demo runs on free hosting, which puts the servers to sleep when nobody is using them. They are all starting now, together; this usually takes under a minute.';
  if (ready) {
    title = 'All servers are ready';
    note = 'Everything will respond instantly now.';
  } else if (failed) {
    title = 'A server did not start';
    note = 'Reload the page to try again.';
  }

  return (
    <section className={`servers${ready ? ' ready' : ''}`} role="status" aria-live="polite">
      <div className="servers-head">
        <strong>{title}</strong>
        <span className="servers-count">{upCount} of {keys.length} ready</span>
      </div>
      <p className="servers-note">{note}</p>
      <ul className="servers-list">
        {list.map((s) => (
          <li key={s.key} className={`is-${status[s.key]}`}>
            <span className="dot" aria-hidden="true" />
            {s.label}
            <span className="visually-hidden">: {status[s.key] === 'up' ? 'ready' : status[s.key] === 'failed' ? 'did not start' : 'starting'}</span>
          </li>
        ))}
      </ul>
    </section>
  );
}
