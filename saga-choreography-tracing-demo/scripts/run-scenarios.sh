#!/usr/bin/env bash
# Places the 4 scenario orders and prints each flow (status, trace_id, timeline). Requires curl + jq.
set -euo pipefail
ORDER=${ORDER:-http://localhost:8080}
TRACKER=${TRACKER:-http://localhost:8083}

place() { # name sku qty amount
  local id
  id=$(curl -s -X POST "$ORDER/orders" -H 'Content-Type: application/json' \
        -d "{\"sku\":\"$2\",\"qty\":$3,\"amount\":$4}" | jq -r .orderId)
  echo "$1 -> orderId=$id" >&2
  echo "$id"
}

show() { # orderId
  curl -s "$TRACKER/flows/$1" | jq -c '{status, traceId, events: [.timeline[] | "\(.service):\(.eventType)"]}'
}

echo "== placing orders"
HAPPY=$(place happy SKU-1 1 100)
OOS=$(place out-of-stock SKU-2 1 100)
FAIL=$(place payment-fail SKU-1 1 1500)
SLOW=$(place slow-payment SKU-1 1 777)

echo "== waiting 4s; slow payment (10s sleep) should show as stuck (olderThanSeconds=3)"
sleep 4
curl -s "$TRACKER/flows/stuck?olderThanSeconds=3" | jq -c .

echo "== waiting 12s for the slow payment to finish"
sleep 12
for n in HAPPY OOS FAIL SLOW; do echo "-- $n"; show "${!n}"; done
echo "== stuck now (expect [])"
curl -s "$TRACKER/flows/stuck?olderThanSeconds=3" | jq -c .
