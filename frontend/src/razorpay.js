// Razorpay Checkout: the gateway's own payment window (UPI, cards, netbanking).
// The script is loaded only when a Razorpay payment is actually started.

const SCRIPT_URL = 'https://checkout.razorpay.com/v1/checkout.js';
let loading = null;

function loadScript() {
  if (window.Razorpay) return Promise.resolve();
  if (!loading) {
    loading = new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src = SCRIPT_URL;
      script.onload = resolve;
      script.onerror = () => {
        loading = null;
        reject(new Error('The payment window could not be loaded. Check your connection and try again.'));
      };
      document.head.appendChild(script);
    });
  }
  return loading;
}

/**
 * Opens the checkout for one gateway order.
 * onPaid receives the payment id and Razorpay's signature; the server verifies that signature
 * before it believes the payment happened.
 */
export async function openRazorpay({ checkout, email, onPaid, onDismiss }) {
  await loadScript();
  const checkoutWindow = new window.Razorpay({
    key: checkout.keyId,
    order_id: checkout.orderId,
    amount: checkout.amountInPaise,
    currency: 'INR',
    name: 'Smart Wallet',
    description: 'Add money to your wallet',
    prefill: { email },
    theme: { color: '#2443C9' },
    handler: (result) => onPaid({ paymentId: result.razorpay_payment_id, signature: result.razorpay_signature }),
    modal: { ondismiss: onDismiss },
  });
  checkoutWindow.open();
}

export const isTestKey = (keyId) => typeof keyId === 'string' && keyId.startsWith('rzp_test_');
