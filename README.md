# Jackpot Service

A small backend that accepts bets, contributes them to a jackpot pool, and evaluates whether a bet wins. Kafka is used to ingest bets; an in-memory H2 DB stores configs, jackpots, contributions and rewards (configs and 4 different jackpots are preloaded via `data.sql`).

---

## API (minimal)

### POST `/api/bets`
Accepts a bet and publishes it to Kafka for ingestion. Body:
```json
{
  "betId": 123,
  "userId": 45,
  "jackpotId": 1,
  "betAmount": 250.00
}
```
Returns HTTP 2xx on accept.

### GET `/api/evaluations/{betId}`
Evaluates the bet identified by `betId` and returns the result.
- **WIN** → JSON includes a positive `payout`.
- **LOSE / already evaluated** → `payout` is 0 and a message explains the outcome.
  

#### Quick cURL examples (fill in as needed)
```bash
# POST a bet
curl -X POST "http://localhost:8181/api/bets"   -H "Content-Type: application/json"   -d '{"betId": 101, "userId": 50, "jackpotId": 1, "betAmount": 250.00}'

# Evaluate a bet
curl -X GET "http://localhost:8181/api/evaluations/101" -H "Accept: application/json"
```

---

## How to Run

There are **two** ways to start the system from the project root.

### A) DEV — Kafka only (run the app locally in your IDE)
```bash
docker compose -f docker-compose.infra-only.yml up -d --build
```
**What starts**
- **broker** — Kafka (exposes host port **19092** for clients)
- **schema-registry** — Confluent Schema Registry (HTTP on **8081**)

Then run the Spring Boot app from your IDE on **http://localhost:8181**.

### B) PROD — Full stack (Kafka + app)
```bash
docker compose up -d --build
```
**What starts**
- **broker** — Kafka (internal to the compose network)
- **schema-registry** — Confluent Schema Registry (HTTP on **8081**)
- **jackpot-service** — Spring Boot application (HTTP on **8181**)

> The first run may take time while Docker pulls images and builds the service.

#### Ports summary
| Service            | Port(s)                      |
|--------------------|------------------------------|
| jackpot-service    | http://localhost:8181        |
| schema-registry    | http://localhost:8081        |
| Kafka (DEV only)   | localhost:19092 (bootstrap)  |

---

## H2 Database (browser console)

Open: **http://localhost:8181/h2-console**

Use the following connection settings (both DEV & PROD):
- **JDBC URL:** `jdbc:h2:mem:jackpotdb`
- **User Name:** `sa`
- **Password:** *(leave empty)*
- **Driver Class:** `org.h2.Driver`

**Note:** The schema is created and **`data.sql`** is applied automatically on application startup.

---

## Postman Pre-request Script (load/play)

Paste this script into the **Pre-request Script** tab of any request in Postman (the script drives the flow). It will send `BET_COUNT` bets and, for each bet, call the evaluation endpoint with bounded retries. Adjust constants as needed.

```javascript
// =========================
// POST /bets then ensure /evaluations/{betId}
// =========================

const baseUrl = "http://localhost:8181/api";
const jackpotId = 1;
const betAmount = 1000.00;

const BET_COUNT = 100;
const START_BET_ID = 1;
const usersPool = Array.from({ length: 20 }, (_, i) => 41 + i); // [41..60]

// Throttling & delays
const POST_THROTTLE_MS      = 20;   // send bets quickly
const AFTER_POST_DELAY_MS   = 1000;  // initial gap before first evaluation try
const EVAL_MAX_RETRIES      = 8;    
const EVAL_BASE_DELAY_MS    = 150;  // exponential backoff base

function sep(title) { console.log(`\n========== ${title} ==========\n`); }

function sendBet(bet) {
  const body = { betId: bet.betId, userId: bet.userId, jackpotId, betAmount };
  pm.sendRequest({
    url: `${baseUrl}/bets`,
    method: "POST",
    header: { "Content-Type": "application/json" },
    body: { mode: "raw", raw: JSON.stringify(body) }
  }, (err, res) => {
    const ok = res && res.code >= 200 && res.code < 300;
    console.log(`${ok ? "✅" : "❌"} BET betId=${bet.betId}, userId=${bet.userId}, status=${res && res.code}, err=${err || "-"}`);

    // Always schedule evaluation; even if POST failed, we try — in case another process wrote the contribution.
    setTimeout(() => evaluateWithRetry(bet.betId, 0), AFTER_POST_DELAY_MS);
  });
}

function evaluateWithRetry(betId, attempt) {
  pm.sendRequest({
    url: `${baseUrl}/evaluations/${betId}`,
    method: "GET",
    header: { "Accept": "application/json" }
  }, (err, res) => {
    const attemptInfo = `(attempt ${attempt + 1}/${EVAL_MAX_RETRIES + 1})`;

    // Network/transport errors → retry
    if (err || !res) {
      console.log(`❌ EVAL transport betId=${betId} ${attemptInfo} err=${err}`);
      return scheduleRetry(betId, attempt);
    }

    let body;
    try { body = typeof res.json === "function" ? res.json() : JSON.parse(res.text()); } catch (_) {}

    const status = res.code;
    const payout = body && typeof body.payout === "number" ? body.payout : undefined;
    const msg = body && body.message;

    console.log(`EVAL betId=${betId} status=${status} ${attemptInfo}${msg ? " msg="+msg : ""}${payout !== undefined ? " payout="+payout : ""}`);

    // Retry criteria:
    // - 404 (contribution not yet visible / routed)
    // - 200 with a known "ingestion/please retry" style message (bounded-await ZERO)
    // - any 5xx
    const shouldRetry =
      status === 404 ||
      (status >= 500) ||
      (status === 200 && msg && /ingest|retry|still being ingested/i.test(msg) && attempt < EVAL_MAX_RETRIES);

    if (shouldRetry) return scheduleRetry(betId, attempt);
    // Otherwise, we accept the result (WIN or LOSE or cycle closed).
  });
}

function scheduleRetry(betId, attempt) {
  if (attempt < EVAL_MAX_RETRIES) {
    const delay = Math.min(EVAL_BASE_DELAY_MS * Math.pow(2, attempt), 800);
    setTimeout(() => evaluateWithRetry(betId, attempt + 1), delay);
  } else {
    console.log(`↪️  No more retries for betId=${betId}`);
  }
}

(function run() {
  sep(`Sending ${BET_COUNT} bets; each will be evaluated with up to ${EVAL_MAX_RETRIES+1} tries`);
  for (let i = 0; i < BET_COUNT; i++) {
    const betId = START_BET_ID + i;
    const userId = usersPool[i % usersPool.length];
    setTimeout(() => sendBet({ betId, userId }), i * POST_THROTTLE_MS);
  }
})();
```

---

## Notes
- On first run, Docker will pull images and build the service — this may take a while.
