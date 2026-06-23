# JMeter Load Tests

## Tests Included
- **Auth Flow** — 100 concurrent users, login/register/refresh (target: p99 < 200ms)
- **Account Operations** — 50 concurrent users, get accounts/balance (target: p99 < 150ms)
- **Transaction Processing** — 200 TPS sustained, debit/credit (target: p99 < 500ms)
- **Payment Rails** — 20 concurrent, SWIFT/ACH/FED (target: p99 < 2s)

## Running
```bash
# Install JMeter
brew install jmeter  # macOS
apt install jmeter   # Ubuntu

# Run non-GUI (for CI)
jmeter -n -t banking-load-test.jmx \
  -JBASE_URL=localhost \
  -JPORT=8080 \
  -JUSERS=100 \
  -JRAMP_UP=60 \
  -l results.jtl \
  -e -o html-report/

# View report
open html-report/index.html
```

## Acceptance Criteria (Production SLAs)
| Endpoint | p50 | p95 | p99 | Error Rate |
|---|---|---|---|---|
| POST /auth/login | <50ms | <150ms | <200ms | <0.1% |
| GET /accounts | <30ms | <100ms | <150ms | <0.1% |
| POST /transactions | <100ms | <300ms | <500ms | <0.5% |
| POST /payments | <200ms | <1s | <2s | <0.5% |

## Scenarios
- **Spike Test**: 10 → 500 users in 30s
- **Soak Test**: 100 users for 2 hours
- **Stress Test**: ramp to breaking point
