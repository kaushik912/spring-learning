# Debugging the saga with breakpoints (remote debug in Docker Compose)

Attach your IDE to the JVMs running in the containers and step through the flow.
Uses the overlay `docker-compose.debug.yml` (repo root of this demo).

## 1. Start with the debug overlay

```bash
docker compose -f docker-compose.yml -f docker-compose.debug.yml up --build
```

- `--build`: rebuilds images so the running code matches your source (else breakpoints won't line up).
- Already running without the overlay? `docker compose down` first, then run the command above.
- Ready when `curl -s localhost:8080/actuator/health` returns UP (also 8081, 8082).

## 2. Attach the IDE (Remote JVM Debug)

| Service | Host:Port |
|---|---|
| order-service | `localhost:5005` |
| inventory-service | `localhost:5006` |
| payment-service | `localhost:5007` |

VS Code `launch.json` entry (one per service, change port):
```json
{ "type": "java", "request": "attach", "name": "order-service", "hostName": "localhost", "port": 5005 }
```

Open the source from the repo, so line numbers match the built image.

## 3. Set breakpoints

Set suspend policy to **Thread** (not All) so Kafka heartbeats and the scheduler keep running.

| Where | Why |
|---|---|
| `SagaOrchestrator.java:45` `start` | saga begins |
| `inventory/CommandListener.java:31` `onCommand` | inventory receives command |
| `SagaOrchestrator.java:63` `onReply` / `SagaEngine.java:70` `applyReply` | reply arrives / state machine |
| `payment/CommandListener.java` `onCommand` | payment receives command |

## 4. Trigger and step

```bash
order() { curl -s -X POST localhost:8080/orders -H 'Content-Type: application/json' -d "{\"sku\":\"$1\",\"qty\":1,\"amount\":$2}"; echo; }
order SKU-1 100
```

Breakpoints hit in order: `start` -> inventory `onCommand` -> `onReply`/`applyReply` -> payment `onCommand` -> `onReply`/`applyReply` again.
Compare with the trace: `docs/jaeger-happy-path-guide.md`. A paused step shows as a very long bar in Jaeger.

## 5. What the overlay changes (so long pauses don't alter the flow)

| Risk while paused | Overlay setting |
|---|---|
| Step timeout (5s) fires, saga compensates | `SAGA_STEP_TIMEOUT=24h` (order-service) |
| Compensation resend timer | `SAGA_RETRY_BASE=24h` (order-service) |
| Kafka evicts consumer after 5 min without poll, rebalance + redelivery | `max.poll.interval.ms` = 24h (all services) |

## Caveats

- **Timeout scenario (`order SKU-1 777`) does not work with the overlay.** It relies on the 5s step timeout; with 24h the saga just completes after the 10s payment delay (no `saga.timeout`, no compensation). Run plain `docker compose up --build` for it.
  To debug the timeout path: in the overlay set `SAGA_STEP_TIMEOUT: 5s` (or remove the line), keep the debug ports.
- A pause inside `applyReply` holds an open DB transaction + row lock on that saga. `GET /orders/<id>/saga` for that order blocks until you resume.
- If breakpoints still get rebalanced after 5 min: the `SPRING_KAFKA_CONSUMER_PROPERTIES_MAX_POLL_INTERVAL_MS` env var relies on Spring relaxed binding for map keys. Set `max.poll.interval.ms` in the service yml instead.
- Back to normal: `docker compose down`, then `docker compose up --build` (no overlay).
