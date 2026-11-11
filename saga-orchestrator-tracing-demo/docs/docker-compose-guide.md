# Docker Compose cheat sheet (overlays + profiles)

Run from `saga-orchestrator-tracing-demo/`.

## Basics

| Command | What it does |
|---|---|
| `docker compose up` | Create + start all services, logs in foreground (Ctrl+C stops them) |
| `docker compose up -d` | Same, detached (background) |
| `docker compose up --build` | Rebuild images first (needed after code/Dockerfile changes) |
| `docker compose up -d kafka` | Start/recreate only that service (+ its `depends_on`) |
| `docker compose ps` | List this project's containers |
| `docker compose logs -f order-service` | Follow logs of one service (omit name = all) |
| `docker compose stop [svc]` | Stop containers, keep them (can `start` again) |
| `docker compose start [svc]` | Start stopped containers |
| `docker compose restart [svc]` | Restart (does not pick up config/image changes) |
| `docker compose down` | Stop + remove containers and network (images stay) |
| `docker compose down -v` | Also remove volumes (wipes DB data if volumes are used) |
| `docker compose exec kafka <cmd>` | Run a command inside a running container |
| `docker compose config` | Print the final merged config (great for checking overlays) |

## Overlays: `-f` (add or change things without touching the main file)

`-f` can be repeated. Files merge left to right; later files override/extend earlier ones.
```bash
docker compose -f docker-compose.yml -f docker-compose.debug.yml up --build
```
Use an overlay for optional extras (debug ports, host Kafka listener, UI) so the main file stays clean.

In this project:
| Overlay | Adds |
|---|---|
| `docker-compose.debug.yml` | JVM debug ports 5005-5007, long timeouts |
| `docker-compose.kafka-host.yml` | `localhost:9094` Kafka listener for host `kcat` (create it from `docs/kafka-messages-guide.md`) |
| `docker-compose.kafka-ui.yml` | Kafka UI on :8090 (profile `ui`) |

**Gotcha:** use the same `-f` list for `down`/`logs`/`stop` too, otherwise compose doesn't know the overlay's services.

## Profiles (optional services)

A service with `profiles: ["ui"]` is **skipped** by default. It starts only when its profile is turned on.

```yaml
kafka-ui:
  profiles: ["ui"]
```

| Command | What it does |
|---|---|
| `docker compose up -d` | Starts everything **without** profiled services |
| `docker compose --profile ui up -d` | Starts normal services **plus** services tagged `ui` |
| `docker compose up -d kafka-ui` | Naming a profiled service turns its profile on automatically |
| `docker compose --profile ui down` | Also stops/removes the `ui` services |
| `COMPOSE_PROFILES=ui docker compose up -d` | Same as `--profile ui`, via env var |

Rules of thumb:
- No `profiles:` on a service = always starts.
- `down` without `--profile ui` leaves profiled services running (they look like orphans). Add the profile, or `down --remove-orphans`.
- Profiles hide a service that already exists in a file. Overlays add a service that isn't in the main file. Here we use both: the UI is defined in an overlay **and** gated by a profile.

## Our common recipes

```bash
# normal
docker compose up --build

# add Kafka UI to a running stack (only starts kafka-ui)
docker compose -f docker-compose.yml -f docker-compose.kafka-ui.yml --profile ui up -d kafka-ui

# debug session
docker compose -f docker-compose.yml -f docker-compose.debug.yml up --build

# stop everything incl. UI
docker compose -f docker-compose.yml -f docker-compose.kafka-ui.yml --profile ui down
```
