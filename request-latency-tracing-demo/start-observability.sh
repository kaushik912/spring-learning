#!/usr/bin/env bash
# Starts Zipkin + Prometheus + Grafana, pointing Prometheus at the app's
# actual host IP instead of host.docker.internal.
#
# Why: host.docker.internal doesn't always route to the host from inside a
# container - notably on WSL2 + Docker Desktop, it resolves to Docker
# Desktop's internal gateway, which doesn't reach the WSL2 distro where the
# app actually runs (connection refused). This script sidesteps the whole
# question by resolving the real host IP with `ip route` and baking it into
# prometheus.yml before starting the stack, so it works the same way
# regardless of which host.docker.internal quirk (if any) your setup has.
set -euo pipefail
cd "$(dirname "$0")"

HOST_IP="$(ip route get 1.1.1.1 2>/dev/null | sed -n 's/.* src \([0-9.]*\).*/\1/p')"
if [ -z "$HOST_IP" ]; then
    echo "Could not auto-detect host IP (needs Linux iproute2) - falling back to host.docker.internal." >&2
    HOST_IP="host.docker.internal"
fi

echo "Prometheus will scrape the app at: $HOST_IP:8080"
sed "s/\${HOST_IP}/$HOST_IP/" observability/prometheus/prometheus.yml.template > observability/prometheus/prometheus.yml

docker compose up -d

cat <<EOF

Zipkin:     http://localhost:9411
Prometheus: http://localhost:9090
Grafana:    http://localhost:3000

Now run the app (./mvnw spring-boot:run) and hit:
  curl http://localhost:8080/api/buggy/orders/1
  curl http://localhost:8080/api/fixed/orders/1
EOF
