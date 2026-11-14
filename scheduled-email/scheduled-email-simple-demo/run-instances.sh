#!/usr/bin/env bash
# Postgres (docker compose) + 2 app instances (separate JVMs). Both run the cron; ShedLock lets one win per tick.
# Waits until everything is SENT or gave up, then demos the support "resend" on a dead user.
set -euo pipefail
cd "$(dirname "$0")"
RUN_ID="$(date +%F)"

docker compose up -d --wait postgres
./mvnw -q -DskipTests package
JAR=$(ls target/*.jar | grep -v original | head -1)
mkdir -p logs
rm -f logs/*.log

wait_up() { until curl -sf "localhost:$1/admin/emails/report" > /dev/null; do sleep 1; done; }
PIDS=()
cleanup() { kill "${PIDS[@]}" 2>/dev/null || true; }
trap cleanup EXIT

java -jar "$JAR" --server.port=8091 > logs/instance-8091.log 2>&1 &
PIDS+=($!)
wait_up 8091            # first instance creates the schema
java -jar "$JAR" --server.port=8092 > logs/instance-8092.log 2>&1 &
PIDS+=($!)
wait_up 8092

echo "== waiting for the cron: everything SENT or given up =="
for _ in $(seq 1 180); do
  R=$(curl -s "localhost:8091/admin/emails/report")
  # done = rows exist (SENT appears), none PENDING, none still retrying. (Empty report = cron hasn't ticked yet.)
  if echo "$R" | grep -q '"SENT"' && ! echo "$R" | grep -q '"PENDING"' \
     && echo "$R" | grep -q '"failedButStillRetrying":0'; then break; fi
  sleep 2
done
echo "$R" | python3 -m json.tool

DEAD=$(echo "$R" | python3 -c 'import sys,json; d=json.load(sys.stdin)["deadUsers(gave up, first 20)"]; print(d[0] if d else "")')
if [ -n "$DEAD" ]; then
  echo; echo "== support: customer $DEAD never got their email -> POST resend =="
  curl -s -X POST "localhost:8091/admin/emails/$RUN_ID/$DEAD/resend?by=alice"; echo
  for _ in $(seq 1 60); do
    R=$(curl -s "localhost:8091/admin/emails/report")
    echo "$R" | grep -q '"failedButStillRetrying":0' && break
    sleep 2
  done
  echo "$R" | python3 -m json.tool
fi

echo; echo "== which instance ran which job (ShedLock: one winner per tick) =="
for f in logs/*.log; do echo "$f: $(grep -c 'PENDING pass' "$f") send ticks, $(grep -c 'FAILED pass' "$f") sweep ticks"; done
