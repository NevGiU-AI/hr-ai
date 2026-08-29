-- Security event and outcome values are validated by Java enums and JPA converters.
-- Hibernate-generated enum checks become stale when the application adds values.
BEGIN;
ALTER TABLE security_audit_events
    DROP CONSTRAINT IF EXISTS security_audit_events_event_type_check;
ALTER TABLE security_audit_events
    DROP CONSTRAINT IF EXISTS security_audit_events_outcome_check;
COMMIT;
