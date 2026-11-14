const rupees = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' });
const plain = new Intl.NumberFormat('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const day = new Intl.DateTimeFormat('en-IN', { day: '2-digit', month: 'short' });
const time = new Intl.DateTimeFormat('en-IN', { hour: '2-digit', minute: '2-digit' });
const full = new Intl.DateTimeFormat('en-IN', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });

/** ₹1,23,456.00 */
export const money = (amount) => rupees.format(Number(amount));
/** 1,23,456.00 (for ledger columns, where the currency is implied) */
export const figure = (amount) => plain.format(Number(amount));
export const shortDay = (iso) => day.format(new Date(iso));
export const clock = (iso) => time.format(new Date(iso));
export const dateTime = (iso) => full.format(new Date(iso));

/** A fresh Idempotency-Key for one logical action (one top-up, one transfer). */
export const newKey = () => crypto.randomUUID();
