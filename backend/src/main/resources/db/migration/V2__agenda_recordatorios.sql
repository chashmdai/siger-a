-- Compatible con MySQL 8 y H2 MODE=MySQL. V1 permanece intacta.
ALTER TABLE task_item ADD COLUMN reminder_interval_hours INT NOT NULL DEFAULT 24;
ALTER TABLE task_item ADD COLUMN next_reminder_ms BIGINT;
UPDATE task_item SET next_reminder_ms=due_ms WHERE state IN ('PENDIENTE','EN_CURSO') AND due_ms IS NOT NULL;
CREATE INDEX ix_task_due_reminder ON task_item(state,next_reminder_ms);
