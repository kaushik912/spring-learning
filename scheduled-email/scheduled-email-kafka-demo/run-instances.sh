#!/usr/bin/env bash
# Kafka + Postgres (docker compose) + 2 app instances (separate JVMs).
#  1. cron on both instances, ShedLock lets one publish 1000 messages
#  2. both instances consume (consumer group splits the partitions)
#  3. kill -9 instance 2 mid-run: Kafka rebalances, instance 1 finishes the job
#  4. publish the whole run AGAIN by hand: every message is skipped as a duplicate, nothing is re-sent
#  5. support "resend" of one dead-lettered user
# NO_KILL=1 skips step 3.
set -euo pipefail
cd "$(dirname "$0")"
RUN_ID="$(date +%F)"
P1=8095
P2=8096

docker compose up -d --wait
./mvnw -q -DskipTests package
JAR=$(ls target/*.jar | grep -v original | head -1)
mkdir -p logs
rm -f logs/*.log

wait_up() { until curl -sf "localhost:$1/admin/emails/report" > /dev/null; do sleep 1; done; }
report() { curl -s "localhost:$P1/admin/emails/report"; }
field() { python3 -c "import sys,json; print(json.load(sys.stdin)['$1'])"; }
PIDS=()
cleanup() { kill "${PIDS[@]}" 2>/dev/null || true; }
trap cleanup EXIT

java -jar "$JAR" --server.port=$P1 > logs/instance-$P1.log 2>&1 &
PIDS+=($!)
wait_up $P1            # first instance creates the schema and the topics
java -jar "$JAR" --server.port=$P2 > logs/instance-$P2.log 2>&1 &
PIDS+=($!)
wait_up $P2

echo "== 1/2. waiting for the cron to publish, and for consumers to start working =="
until [ "$(report | field usersSent)" -ge 150 ]; do sleep 1; done
echo "   usersSent=$(report | field usersSent) so far"

if [ "${NO_KILL:-0}" != "1" ]; then
  echo "== 3. kill -9 instance-$P2 (pid ${PIDS[1]}) mid-run; Kafka rebalances after the 10s session timeout =="
  kill -9 "${PIDS[1]}"
fi

echo "== waiting for every user to be SENT or dead-lettered =="
for _ in $(seq 1 180); do
  [ "$(report | field completed)" = "True" ] && break
  sleep 2
done
sleep 3   # let in-flight dead-letters settle
report | python3 -m json.tool

DEAD=$(report | python3 -c 'import sys,json; d=json.load(sys.stdin)["deadUsers(first 20)"]; print(d[0] if d else "")')
if [ -n "$DEAD" ]; then
  echo; echo "== 4. support: customer $DEAD never got their email -> POST resend =="
  curl -s -X POST "localhost:$P1/admin/emails/$RUN_ID/$DEAD/resend"; echo
  sleep 6
  echo "   usersSent=$(report | field usersSent) usersDead=$(report | field usersDead)"
fi

echo; echo "== 5. publish the whole run AGAIN by hand (1000 duplicate messages) =="
echo "   before: usersSent=$(report | field usersSent) deliveries=$(report | field providerUniqueDeliveries)"
curl -s -X POST "localhost:$P1/admin/emails/$RUN_ID/publish"; echo
sleep 15
echo "   after:  usersSent=$(report | field usersSent) deliveries=$(report | field providerUniqueDeliveries)"
echo "   Already-sent users are skipped (no new provider calls for them). Only users who were still dead-lettered"
echo "   get a fresh set of attempts, so usersSent and deliveries rise together and stay equal: nobody got two emails."
report | python3 -m json.tool

echo; echo "== who did what =="
for f in logs/*.log; do echo "$f: $(grep -c 'published run' "$f") publishes, $(grep -c 'dead-lettered' "$f") dead-letters"; done
