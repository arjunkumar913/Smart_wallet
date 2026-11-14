# Deploying Smart Wallet (free tier)

| Piece | Provider (free plan) | Why |
|---|---|---|
| 4 Spring Boot services | **Render** — Docker web services, defined in [`render.yaml`](render.yaml) | Deploys straight from GitHub; one Blueprint creates all services |
| PostgreSQL (3 databases) | **Neon** | Free Postgres that does not expire; scales to zero when idle |
| RabbitMQ | **CloudAMQP** — Little Lemur | Free managed RabbitMQ, no card required |
| React frontend | **Render** — static site, also in `render.yaml` | Free, served from a CDN, never sleeps |
| Email (optional) | **Brevo** — free plan, 300 emails a day | Sends over HTTPS; Render's free plan blocks SMTP ports |
| Payments (optional) | **Razorpay** test mode | Real checkout with UPI, cards and netbanking; no real money, no KYC for test keys |

## 1. Neon — databases

1. Sign up at neon.com with GitHub and create a project `smart-wallet`, region **AWS Asia Pacific (Singapore)**.
2. In **Databases**, create `auth_db`, `wallet_db` and `notification_db`.
3. Open **Connect**, turn **connection pooling off** (Liquibase needs a direct connection), and note the
   **host** (`ep-...neon.tech`), **role** and **password**.
4. JDBC URLs used below: `jdbc:postgresql://<host>/<database>?sslmode=require`

## 2. CloudAMQP — RabbitMQ

1. Sign up at cloudamqp.com and create an instance on the free **Little Lemur** plan, region close to Singapore.
2. Copy its **AMQP URL** (`amqps://user:password@host/vhost`).

## 3. Render — services

1. Sign up at render.com with GitHub, then **New → Blueprint**, and pick this repository.
2. Render reads `render.yaml` and asks for every secret:

| Variable | Value |
|---|---|
| `DB_URL` | the JDBC URL of that service's database (`auth_db`, `wallet_db` or `notification_db`) |
| `DB_USERNAME` / `DB_PASSWORD` | Neon role and password |
| `SPRING_RABBITMQ_ADDRESSES` | the CloudAMQP AMQP URL |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | your admin login (use a strong password) |
| `AUTH_SERVICE_URL` etc. (gateway) | `https://smartwallet-auth.onrender.com`, `...-wallet...`, `...-notification...` |

| `VITE_API_URL` (smartwallet-web) | the gateway's public URL, e.g. `https://smartwallet-gateway.onrender.com` (no trailing slash) |
| `CORS_ALLOWED_ORIGINS` (gateway) | the static site's URL, e.g. `https://smartwallet-web.onrender.com` |
| `PAYMENT_PROVIDER` (wallet) | leave as `mock` for the built-in demo bank, or `razorpay` (see section 4) |
| `RAZORPAY_KEY_ID` / `RAZORPAY_KEY_SECRET` / `RAZORPAY_WEBHOOK_SECRET` (wallet) | leave empty unless `PAYMENT_PROVIDER=razorpay` |
| `EMAIL_PROVIDER` (notification) | leave as `log` (emails go to the service log), or `brevo` (see section 5) |
| `APP_URL` (notification) | the frontend's public URL; every email links to it |
| `BREVO_API_KEY` / `EMAIL_FROM_ADDRESS` (notification) | leave empty unless `EMAIL_PROVIDER=brevo` |

3. Apply. The first build takes several minutes per service.
4. Check each service's URL on its dashboard page. If Render added a suffix (e.g. `smartwallet-auth-x1y2.onrender.com`),
   update the three URLs on the gateway's **Environment** tab.

5. `VITE_API_URL` is read when the frontend is **built**. After changing it, use **Manual Deploy → Clear build cache & deploy**
   on the static site.

## 4. Razorpay — real checkout in test mode (optional)

Without this step, **Add money** uses the built-in demo bank, which needs no account.

1. Sign up at razorpay.com and stay in **Test Mode** (the switch at the top of the dashboard).
2. **Account & Settings → API Keys → Generate Test Key**. Copy the key id (`rzp_test_...`) and the key secret. The secret
   is shown once.
3. **Account & Settings → Webhooks → Add New Webhook**:
   - URL: `https://<your-gateway>.onrender.com/api/webhooks/razorpay`
   - Secret: any long random string you choose
   - Events: `payment.captured`, `payment.failed`, `order.paid`
4. On Render, open **smartwallet-wallet → Environment** and set `PAYMENT_PROVIDER=razorpay`, `RAZORPAY_KEY_ID`,
   `RAZORPAY_KEY_SECRET` and `RAZORPAY_WEBHOOK_SECRET` (the string from step 3). Save; the service redeploys.
5. Test a payment: in the checkout window choose UPI and enter `success@razorpay` (or `failure@razorpay` to see a failed
   payment).

Never put the key secret or webhook secret in git or in the frontend. Only the key id is sent to the browser.

## 5. Brevo — real emails (optional)

Without this step, emails are printed to the notification-service log instead of being sent.

1. Sign up at brevo.com (free plan: 300 emails a day).
2. **Senders, Domains & Dedicated IPs → Senders → Add a sender**, using an address you own (your Gmail works),
   and confirm it from the email Brevo sends you. Brevo only sends from verified senders.
3. **SMTP & API → API Keys → Generate a new API key**. Copy it; it is shown once.
4. On Render, open **smartwallet-notification → Environment** and set `EMAIL_PROVIDER=brevo`, `BREVO_API_KEY`,
   `EMAIL_FROM_ADDRESS` (the verified sender) and `APP_URL` (your frontend URL). Save; the service redeploys.
5. Register in the app with a real email address and make a transfer. Demo accounts (`@demo.smartwallet.dev`)
   never get emails.

Free-plan emails from a Gmail sender often land in spam until you verify a domain of your own in Brevo.

## Free-tier behaviour to know

- A free Render service **sleeps after 15 minutes without traffic** and takes about a minute to wake, so the first request
  after a pause is slow. Requests through the gateway may need to wake several services.
- While a consumer sleeps, its events wait safely in RabbitMQ (durable queues) and are processed as soon as it wakes.
  Example: right after registering, `GET /api/wallets/me` can return `WALLET_NOT_FOUND` until wallet-service has woken
  and consumed the `UserRegistered` event — retry after a few seconds.
- A webhook that arrives while wallet-service is asleep can time out. Razorpay retries it, and the reconciliation job
  (and the browser callback) credit the wallet anyway, so a sleeping service never loses a paid top-up.
- Neon suspends compute after 5 minutes idle; the first query afterwards takes a moment longer.
