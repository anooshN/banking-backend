# 13. Monitoring — Knowing When Things Break

## The Simple Version

Without monitoring: a customer calls and says "I can't log in." You had no idea. You start looking.

With monitoring: an alert fires at 3am: "auth-service error rate exceeded 5% for the last 2 minutes." You fix it before most customers even notice.

Monitoring is your app's health system. It watches everything and tells you before small problems become big ones.

---

## The Four Pillars

### 1. Metrics — Numbers Over Time

Numbers describing system health, collected every 15 seconds.

Examples: requests/second, error rate, p99 latency, JVM heap used, active DB connections, Kafka consumer lag.

**Technology: Prometheus** scrapes metrics from every service's /actuator/prometheus endpoint. Spring Boot + Micrometer expose hundreds of metrics automatically.

Our custom business metrics:
- banking.transaction.total — total transactions processed
- banking.fraud.detected — fraud detections by risk level
- banking.insufficient.funds — how often customers lacked funds
- banking.active.sessions — concurrent logged-in users

### 2. Logs — What Happened

Every service writes a log for every significant event:
```
[10:23:45] [correlationId=abc-123] [auth-service] User logged in: john@bank.com
[10:23:45] [correlationId=abc-123] [transaction-service] ERROR: Insufficient funds
```

**Technology: ELK Stack**
- Elasticsearch: stores and indexes logs (searchable)
- Logstash: collects, parses, and enriches logs with geo-IP, slow-request tags, error tags
- Kibana: web UI to search and visualize

Search example: correlationId:abc-123 AND level:ERROR — see all errors for one user request across all 14 services.

### 3. Traces — Following a Request

A single user action touches multiple services. Distributed tracing gives a visual timeline of the entire journey, showing exactly how long each service took and where errors occurred.

**Technology: Zipkin / Tempo**

### 4. Alerts — Wake Me Up When Things Break

| Alert | Condition | Severity |
|---|---|---|
| High Error Rate | error_rate > 5% for 2 min | Critical |
| Slow Responses | p99_latency > 2s for 5 min | Warning |
| Service Down | up == 0 for 1 min | Critical |
| High Memory | heap_used > 85% | Warning |
| Kafka Lag | consumer_lag > 10000 | Warning |
| Fraud Spike | fraud_detected > 10/min | Critical |

Critical alerts page engineers via PagerDuty. Warnings send Slack messages.

---

## Grafana Dashboards

**Banking Overview:** RPS, error rate %, p99 latency by service, JVM heap, DB connections, Kafka lag, Redis hit rate.

**Transaction Deep Dive:** TPS, USD volume, failed transactions, insufficient funds rate, circuit breaker state timeline.

**Fraud Real-Time:** Fraud alerts last 24h, risk level pie chart, score histogram, top flagged accounts.

---

## Health Checks

Every service exposes:
- /actuator/health/liveness — "am I running?" → if fails, Kubernetes restarts the pod
- /actuator/health/readiness — "am I ready?" → if fails, Kubernetes stops sending traffic

This means: a pod that is starting up won't receive traffic until it is ready. A pod that crashes is automatically restarted.
