-- Customer-facing failure code, set when the saga enters COMPENSATING (null while healthy).
alter table saga_instance add column failure_reason varchar(32);
