#!/usr/bin/env bash
# Starts Postgres (docker compose) + N app instances (separate JVMs), races publishers, then
# `kill -9`s one instance mid-run to prove the rest finish the job with no duplicates and no losses.
# Usage: ./run-instances.sh [N=3]      NO_KILL=1 to skip the kill -9
set -euo pipefail
cd "$(dirname "$0")"
N="${1:-3}"
RUN_ID="demo-$(date +%H%M%S)"

docker compose up -d --wait postgres
./mvnw -q -DskipTests package
JAR=$(ls target/*.jar | grep -v original | head -1)
mkdir -p logs
rm -f logs/*.log

wait_up() { until curl -sf "localhost:$1/api/report?runId=x" > /dev/null; do sleep 1; done; }

PIDS=()
cleanup() { kill "${PIDS[@]}" 2>/dev/null || true; }
trap cleanup EXIT

for i in $(seq 1 "$N"); do
  PORT=$((8080 + i))
  # Publisher cron every 15s on every instance: ShedLock lets one win per tick.
  java -jar "$JAR" --server.port="$PORT" --demo.publish-cron='*/15 * * * * *' > "logs/instance-$PORT.log" 2>&1 &
  PIDS+=($!)
  # first instance creates the schema; wait until it is up before starting the rest (avoids CREATE TABLE race)
  [ "$i" -eq 1 ] && wait_up "$PORT"
done
for i in $(seq 2 "$N"); do wait_up $((8080 + i)); done
echo "== $N instances up. Racing manual publishers (NO ShedLock) for run=$RUN_ID =="
CURLS=()
for i in $(seq 1 "$N"); do
  curl -s -X POST "localhost:$((8080 + i))/api/publish?runId=$RUN_ID" > /dev/null &
  CURLS+=($!)
done
wait "${CURLS[@]}"   # wait only for the curls, not the JVMs

if [ "${NO_KILL:-0}" != "1" ] && [ "$N" -ge 2 ]; then
  sleep 2
  echo "== kill -9 instance-8082 (pid ${PIDS[1]}) mid-run; its CLAIMED rows are orphaned =="
  kill -9 "${PIDS[1]}"
fi

echo "== waiting for all 1000 users to be SENT (reaper must recover the orphans) =="
R=""
for _ in $(seq 1 180); do
  R=$(curl -s "localhost:8081/api/report?runId=$RUN_ID" || true)
  echo "$R" | grep -q '"exactlyOnceForAllUsers":true' && break
  sleep 1
done
echo "$R" | python3 -m json.tool 2>/dev/null || echo "$R"
echo
echo "== ShedLock evidence: publisher runs (scheduled ticks are the run=2026... ones; one winner per tick) =="
grep -h "publisher run=" logs/*.log | sed -E 's/^[^ ]+ +INFO [0-9]+ --- \[[^]]*\] \[[^]]*\] [^ ]+ +: //' | sort | uniq -c | head -20
echo "== simulated crashes per instance / reaper activity =="
grep -c "SIMULATED CRASH" logs/*.log || true
grep -h "reaper re-queued" logs/*.log | head -8 || true
