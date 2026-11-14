#!/usr/bin/env bash
# Postgres (docker compose) + 2 app instances (separate JVMs). Both run the cron; ShedLock lets one win per tick.
# Waits until everything is SENT or given up, then demos the support "resend" on a dead user.
set -euo pipefail
cd "$(dirname "$0")"
RUN_ID="$(date +%F)"
P1=8093
P2=8094

docker compose up -d --wait postgres
./mvnw -q -DskipTests package
JAR=$(ls target/*.jar | grep -v original | head -1)
mkdir -p logs
rm -f logs/*.log

wait_up() { until curl -sf "localhost:$1/admin/emails/report" > /dev/null; do sleep 1; done; }
PIDS=()
cleanup() { kill "${PIDS[@]}" 2>/dev/null || true; }
trap cleanup EXIT

java -jar "$JAR" --server.port=$P1 > logs/instance-$P1.log 2>&1 &
PIDS+=($!)
wait_up $P1            # first instance creates the schema
java -jar "$JAR" --server.port=$P2 > logs/instance-$P2.log 2>&1 &
PIDS+=($!)
wait_up $P2

echo "== waiting for the cron: everything SENT or given up =="
for _ in $(seq 1 180); do
  R=$(curl -s "localhost:$P1/admin/emails/report")
  # done = rows exist (SENT appears), none PENDING, none still retrying. (Empty report = cron hasn't ticked yet.)
  if echo "$R" | grep -q '"SENT"' && ! echo "$R" | grep -q '"PENDING"' \
     && echo "$R" | grep -q '"failedButStillRetrying":0'; then break; fi
  sleep 2
done
echo "$R" | python3 -m json.tool

DEAD=$(echo "$R" | python3 -c 'import sys,json; d=json.load(sys.stdin)["deadUsers(gave up, first 20)"]; print(d[0] if d else "")')
if [ -n "$DEAD" ]; then
  echo; echo "== support: customer $DEAD never got their email -> POST resend =="
  curl -s -X POST "localhost:$P1/admin/emails/$RUN_ID/$DEAD/resend?by=alice"; echo
  for _ in $(seq 1 60); do
    R=$(curl -s "localhost:$P1/admin/emails/report")
    echo "$R" | grep -q '"failedButStillRetrying":0' && break
    sleep 2
  done
  echo "$R" | python3 -m json.tool
fi

echo; echo "== which instance ran which job (ShedLock: one winner per tick) =="
for f in logs/*.log; do echo "$f: $(grep -c 'PENDING pass' "$f") send ticks, $(grep -c 'FAILED pass' "$f") sweep ticks"; done
