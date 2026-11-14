/*
 * Free hosting puts each backend service to sleep after 15 minutes without traffic, and a sleeping service only
 * starts booting when a request reaches it. Through the gateway that happens one service at a time:
 * gateway, then auth, then wallet, each waiting for the one before it.
 *
 * Instead, as soon as the website opens, ping every service's /actuator/health directly and at the same time,
 * so they all boot in parallel, and keep polling until each answers UP.
 *
 * Which services: VITE_SERVICE_URLS (comma-separated base URLs) if set; otherwise, when the gateway's host name
 * contains "-gateway" (as on Render: smartwallet-gateway-xxxx), the sibling services are found by swapping that word.
 * Locally nothing sleeps, so nothing is pinged.
 */

const API = (import.meta.env.VITE_API_URL || '').replace(/\/$/, '');
const POLL_MS = 3000;
const REQUEST_TIMEOUT_MS = 8000;
const GIVE_UP_MS = 5 * 60 * 1000;

const SERVICES = [
  { key: 'gateway', label: 'Gateway' },
  { key: 'auth', label: 'Accounts' },
  { key: 'wallet', label: 'Wallet' },
  { key: 'notification', label: 'Notifications' },
];

export function services() {
  const configured = (import.meta.env.VITE_SERVICE_URLS || '').split(',').map((u) => u.trim().replace(/\/$/, '')).filter(Boolean);
  if (configured.length) {
    return configured.map((url, i) => ({ ...(SERVICES[i] || { key: url, label: new URL(url).hostname }), url }));
  }
  if (!API || !API.includes('-gateway')) return [];
  return SERVICES.map((s) => ({ ...s, url: API.replace('-gateway', `-${s.key}`) }));
}

async function isUp(url) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
  try {
    const response = await fetch(`${url}/actuator/health`, { cache: 'no-store', signal: controller.signal });
    if (!response.ok) return false;                 // Render answers 503 with a "waking up" page meanwhile
    const body = await response.json();
    return body.status === 'UP';
  } catch {
    return false;                                   // still asleep, booting, or the request timed out
  } finally {
    clearTimeout(timer);
  }
}

/**
 * Wakes every service in parallel. Calls onChange({ key: 'waking' | 'up' | 'failed' }) whenever a status changes.
 * Returns a function that stops polling.
 */
export function wakeAll(list, onChange) {
  let stopped = false;
  const started = Date.now();
  const state = Object.fromEntries(list.map((s) => [s.key, 'waking']));
  const timers = [];

  const poll = async (service) => {
    if (stopped) return;
    if (await isUp(service.url)) {
      state[service.key] = 'up';
    } else if (Date.now() - started > GIVE_UP_MS) {
      state[service.key] = 'failed';
    } else {
      timers.push(setTimeout(() => poll(service), POLL_MS));
      return;
    }
    if (!stopped) onChange({ ...state });
  };

  onChange({ ...state });
  list.forEach((service) => poll(service));
  return () => {
    stopped = true;
    timers.forEach(clearTimeout);
  };
}
