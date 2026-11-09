-- 0.4.0: atencion directa progresiva. No reinterpreta ingresos anteriores.
CREATE TABLE attention_record (
 requirement_id BIGINT PRIMARY KEY, modality VARCHAR(16) NOT NULL,
 data_json MEDIUMTEXT NOT NULL, followup_required BOOLEAN NOT NULL DEFAULT FALSE,
 updated_by BIGINT NOT NULL, updated_ms BIGINT NOT NULL,
 FOREIGN KEY(requirement_id) REFERENCES requirement(id),
 FOREIGN KEY(updated_by) REFERENCES staff(id),
 CHECK(modality IN ('GENERAL','NNA'))
);
CREATE TABLE attention_revision (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, requirement_id BIGINT NOT NULL,
 requirement_revision BIGINT NOT NULL, author_id BIGINT NOT NULL,
 registered_ms BIGINT NOT NULL, reason VARCHAR(2000) NOT NULL, data_json MEDIUMTEXT NOT NULL,
 FOREIGN KEY(requirement_id) REFERENCES attention_record(requirement_id),
 FOREIGN KEY(author_id) REFERENCES staff(id), UNIQUE(requirement_id,requirement_revision)
);
CREATE TABLE attention_handoff (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, requirement_id BIGINT NOT NULL,
 from_user BIGINT NOT NULL, from_scope BIGINT NOT NULL, sent_by BIGINT NOT NULL,
 sent_ms BIGINT NOT NULL, target_scope BIGINT NOT NULL, target_user BIGINT,
 recipient_kind VARCHAR(16) NOT NULL, state VARCHAR(16) NOT NULL DEFAULT 'PENDIENTE',
 reason VARCHAR(2000) NOT NULL, decided_by BIGINT, decided_ms BIGINT, decision_reason VARCHAR(2000),
 FOREIGN KEY(requirement_id) REFERENCES attention_record(requirement_id),
 FOREIGN KEY(from_user) REFERENCES staff(id), FOREIGN KEY(from_scope) REFERENCES org_scope(id),
 FOREIGN KEY(sent_by) REFERENCES staff(id), FOREIGN KEY(target_scope) REFERENCES org_scope(id),
 FOREIGN KEY(target_user) REFERENCES staff(id), FOREIGN KEY(decided_by) REFERENCES staff(id),
 CHECK(recipient_kind IN ('PROFESIONAL','JEFATURA')),
 CHECK(state IN ('PENDIENTE','RECIBIDA','DEVUELTA','RETIRADA')),
 CHECK((recipient_kind='PROFESIONAL' AND target_user IS NOT NULL) OR (recipient_kind='JEFATURA' AND target_user IS NULL))
);
CREATE INDEX ix_attention_pending ON attention_handoff(requirement_id,state);
CREATE INDEX ix_attention_recipient ON attention_handoff(target_user,state,target_scope);
CREATE INDEX ix_attention_revisions ON attention_revision(requirement_id,id);
