// One small client for the API gateway. Every call goes through request(), which adds the token,
// turns the backend's { code, message } errors into ApiError, and reports slow calls
// (the free hosting tier sleeps when idle, so the first call can take up to a minute).

const BASE = (import.meta.env.VITE_API_URL || '').replace(/\/$/, '');
const SLOW_AFTER_MS = 4000;

let token = null;
let onUnauthorized = () => {};
let onSlowChange = () => {};
let slowCalls = 0;

export class ApiError extends Error {
  constructor(status, code, message, fieldErrors) {
    super(message);
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}

export function configure({ unauthorized, slowChange }) {
  onUnauthorized = unauthorized;
  onSlowChange = slowChange;
}

export function setToken(value) {
  token = value;
}

async function request(method, path, { body, headers = {}, quiet = false } = {}) {
  let slow = false;
  const slowTimer = quiet ? null : setTimeout(() => {
    slow = true;
    slowCalls += 1;
    onSlowChange(true);
  }, SLOW_AFTER_MS);

  try {
    let response;
    try {
      response = await fetch(BASE + path, {
        method,
        headers: {
          ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
          ...headers,
        },
        body: body !== undefined ? JSON.stringify(body) : undefined,
      });
    } catch {
      throw new ApiError(0, 'NETWORK', 'Could not reach the server. Check your connection and try again.');
    }

    const text = await response.text();
    let data = null;
    try {
      data = text ? JSON.parse(text) : null;
    } catch {
      data = null;
    }

    if (!response.ok) {
      if (response.status === 401 && token) {
        onUnauthorized();
      }
      const waking = response.status === 502 || response.status === 503 || response.status === 504;
      throw new ApiError(
        response.status,
        data?.code || (waking ? 'UNAVAILABLE' : 'ERROR'),
        data?.message || (waking
          ? 'The server is still starting. Wait a few seconds and try again.'
          : 'Something went wrong. Try again.'),
        data?.fieldErrors,
      );
    }
    return data;
  } finally {
    clearTimeout(slowTimer);
    if (slow) {
      slowCalls -= 1;
      if (slowCalls === 0) onSlowChange(false);
    }
  }
}

export const api = {
  register: (email, password) => request('POST', '/api/auth/register', { body: { email, password } }),
  login: (email, password) => request('POST', '/api/auth/login', { body: { email, password } }),

  wallet: (options) => request('GET', '/api/wallets/me', options),
  transactions: (page = 0, size = 10) => request('GET', `/api/wallets/me/transactions?page=${page}&size=${size}`),

  // The same key is sent again if the request is retried, so a retry can never create a second order.
  createTopUp: (amount, idempotencyKey) =>
    request('POST', '/api/topups', { body: { amount }, headers: { 'Idempotency-Key': idempotencyKey } }),
  topUp: (id) => request('GET', `/api/topups/${id}`, { quiet: true }),
  confirmTopUp: (id, paymentId, signature) =>
    request('POST', `/api/topups/${id}/confirm`, { body: { paymentId, signature } }),
  syncTopUp: (id) => request('POST', `/api/topups/${id}/sync`, { quiet: true }),
  demoBankPay: (id, outcome) => request('POST', `/api/topups/${id}/mock-pay`, { body: { outcome } }),

  transfer: (toEmail, amount, note, idempotencyKey) =>
    request('POST', '/api/transfers', {
      body: { toEmail, amount, note: note || null },
      headers: { 'Idempotency-Key': idempotencyKey },
    }),

  notifications: () => request('GET', '/api/notifications?size=10'),
};
