# Correctly reasons about save-before-notify ordering from indirect evidence

User independently reasoned "payment probably saved before the Kafka error happened" without being told — correct, and provable from the DB evidence already gathered (orphaned `order_id` in the `payment` table) rather than needing a log line. Didn't need to see an explicit stack trace to trust the conclusion once shown the DB-level proof.

**Implication**: user is comfortable inferring backend behavior from data-level evidence (DB state) rather than only from logs/stack traces — future debugging-style lessons can lean on "check the database" as a primary diagnostic tool, not just a verification step.
