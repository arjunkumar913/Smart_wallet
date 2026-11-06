# Deploying Smart Wallet (free tier)

| Piece | Provider (free plan) | Why |
|---|---|---|
| 4 Spring Boot services | **Render** — Docker web services, defined in [`render.yaml`](render.yaml) | Deploys straight from GitHub; one Blueprint creates all services |
| PostgreSQL (3 databases) | **Neon** | Free Postgres that does not expire; scales to zero when idle |
| RabbitMQ | **CloudAMQP** — Little Lemur | Free managed RabbitMQ, no card required |

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

3. Apply. The first build takes several minutes per service.
4. Check each service's URL on its dashboard page. If Render added a suffix (e.g. `smartwallet-auth-x1y2.onrender.com`),
   update the three URLs on the gateway's **Environment** tab.

## Free-tier behaviour to know

- A free Render service **sleeps after 15 minutes without traffic** and takes about a minute to wake, so the first request
  after a pause is slow. Requests through the gateway may need to wake several services.
- While a consumer sleeps, its events wait safely in RabbitMQ (durable queues) and are processed as soon as it wakes.
  Example: right after registering, `GET /api/wallets/me` can return `WALLET_NOT_FOUND` until wallet-service has woken
  and consumed the `UserRegistered` event — retry after a few seconds.
- Neon suspends compute after 5 minutes idle; the first query afterwards takes a moment longer.
