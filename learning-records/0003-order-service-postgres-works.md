# Order service + Postgres verified hands-on, plus DB-provisioning gap found

Hit a real gap Lesson 3 didn't anticipate: Postgres container only auto-creates a DB named after `POSTGRES_USER` (`alibou`), not the per-service DBs (`order`, later `payment`) each service's datasource URL expects — had to `CREATE DATABASE "order"` manually. Confirmed empty-list/404 hands-on steps worked after that fix. User also picked up `psql`/pgAdmin basics (registering a server with host=`postgresql` not `localhost` since pgAdmin/Postgres share a Docker network) and how to enable Hibernate SQL logging.

**Implication for later lessons**: Section 8 (Payment service, Postgres-backed) will hit the same missing-database issue — create the `payment` DB proactively instead of waiting for the error. User is comfortable driving `psql`/pgAdmin themselves now, so hands-on DB-inspection steps in future lessons can be lighter-touch (point at the tool, not step-by-step).
