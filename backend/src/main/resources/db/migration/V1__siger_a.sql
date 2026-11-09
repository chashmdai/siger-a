-- Base nueva y aislada. No ejecutar sobre SIGER historico ni SMR.
CREATE TABLE app_meta (id INT PRIMARY KEY, namespace VARCHAR(40) NOT NULL);
INSERT INTO app_meta VALUES (1,'SIGER_A_2026');
-- Serializacion corta de comandos para esta candidata de bajo volumen.
CREATE TABLE operation_lock (id INT PRIMARY KEY);
INSERT INTO operation_lock VALUES (1);
CREATE TABLE org_unit (code VARCHAR(24) PRIMARY KEY, name VARCHAR(160) NOT NULL);
CREATE TABLE org_scope (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, code VARCHAR(48) NOT NULL UNIQUE,
 name VARCHAR(160) NOT NULL, unit_code VARCHAR(24) NOT NULL, area_code VARCHAR(48),
 site_code VARCHAR(48) NOT NULL, regional BOOLEAN NOT NULL DEFAULT FALSE,
 enabled BOOLEAN NOT NULL DEFAULT TRUE, FOREIGN KEY(unit_code) REFERENCES org_unit(code)
);
CREATE TABLE staff (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(64) NOT NULL UNIQUE,
 name VARCHAR(160) NOT NULL, email VARCHAR(200), password_hash VARCHAR(100) NOT NULL,
 enabled BOOLEAN NOT NULL DEFAULT TRUE, must_change BOOLEAN NOT NULL DEFAULT TRUE,
 security_version BIGINT NOT NULL DEFAULT 0, home_scope BIGINT NOT NULL,
 FOREIGN KEY(home_scope) REFERENCES org_scope(id)
);
CREATE TABLE role_assignment (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, role_code VARCHAR(32) NOT NULL,
 scope_id BIGINT, valid_from BIGINT NOT NULL, valid_until BIGINT, revoked_at BIGINT,
 granted_by BIGINT, basis VARCHAR(1000) NOT NULL,
 FOREIGN KEY(user_id) REFERENCES staff(id), FOREIGN KEY(scope_id) REFERENCES org_scope(id),
 FOREIGN KEY(granted_by) REFERENCES staff(id)
);
CREATE INDEX ix_grant_user ON role_assignment(user_id,revoked_at,valid_until);
CREATE TABLE substitution_designation (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, scope_id BIGINT NOT NULL, titular_id BIGINT NOT NULL,
 substitute_id BIGINT NOT NULL, basis VARCHAR(1000) NOT NULL, enabled BOOLEAN NOT NULL DEFAULT TRUE,
 FOREIGN KEY(scope_id) REFERENCES org_scope(id), FOREIGN KEY(titular_id) REFERENCES staff(id),
 FOREIGN KEY(substitute_id) REFERENCES staff(id), UNIQUE(scope_id,titular_id,substitute_id),
 CHECK(titular_id<>substitute_id)
);
CREATE TABLE substitution (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, designation_id BIGINT NOT NULL,
 activated_by BIGINT NOT NULL, from_ms BIGINT NOT NULL, until_ms BIGINT NOT NULL,
 reason VARCHAR(2000) NOT NULL, ended_ms BIGINT, ended_by BIGINT,
 revision BIGINT NOT NULL DEFAULT 0, FOREIGN KEY(designation_id) REFERENCES substitution_designation(id),
 FOREIGN KEY(activated_by) REFERENCES staff(id), FOREIGN KEY(ended_by) REFERENCES staff(id), CHECK(until_ms>from_ms)
);
CREATE TABLE communication (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, folio VARCHAR(48) UNIQUE, external_source VARCHAR(48), external_id VARCHAR(100),
 channel VARCHAR(32) NOT NULL, received_ms BIGINT NOT NULL, created_ms BIGINT NOT NULL,
 created_by BIGINT NOT NULL, origin_scope BIGINT NOT NULL, presenter VARCHAR(200), contact VARCHAR(300),
 identity_document VARCHAR(100), subject VARCHAR(240) NOT NULL, body MEDIUMTEXT NOT NULL,
 state VARCHAR(24) NOT NULL DEFAULT 'NUEVA', revision BIGINT NOT NULL DEFAULT 0,
 FOREIGN KEY(created_by) REFERENCES staff(id), FOREIGN KEY(origin_scope) REFERENCES org_scope(id),
 UNIQUE(external_source,external_id)
);
CREATE TABLE solicitation (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, communication_id BIGINT NOT NULL UNIQUE,
 destination_scope BIGINT NOT NULL, state VARCHAR(24) NOT NULL DEFAULT 'PENDIENTE',
 classification VARCHAR(2000) NOT NULL, created_by BIGINT NOT NULL, created_ms BIGINT NOT NULL,
 revision BIGINT NOT NULL DEFAULT 0,
 FOREIGN KEY(communication_id) REFERENCES communication(id), FOREIGN KEY(destination_scope) REFERENCES org_scope(id),
 FOREIGN KEY(created_by) REFERENCES staff(id)
);
CREATE TABLE requirement (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, folio VARCHAR(48) UNIQUE, communication_id BIGINT NOT NULL UNIQUE,
 solicitation_id BIGINT UNIQUE, principal_scope BIGINT NOT NULL, attention_user BIGINT, attention_scope BIGINT NOT NULL,
 complexity VARCHAR(12), state VARCHAR(16) NOT NULL DEFAULT 'ABIERTO', created_ms BIGINT NOT NULL,
 created_by BIGINT NOT NULL, closed_ms BIGINT, closed_by BIGINT, close_reason TEXT,
 revision BIGINT NOT NULL DEFAULT 0, FOREIGN KEY(communication_id) REFERENCES communication(id),
 FOREIGN KEY(solicitation_id) REFERENCES solicitation(id), FOREIGN KEY(principal_scope) REFERENCES org_scope(id),
 FOREIGN KEY(attention_scope) REFERENCES org_scope(id), FOREIGN KEY(attention_user) REFERENCES staff(id), FOREIGN KEY(created_by) REFERENCES staff(id),
 FOREIGN KEY(closed_by) REFERENCES staff(id), CHECK(complexity IS NULL OR complexity IN ('BAJA','MEDIANA','ALTA'))
);
CREATE TABLE case_record (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, folio VARCHAR(48) UNIQUE, requirement_id BIGINT NOT NULL,
 scope_id BIGINT NOT NULL, responsible_id BIGINT NOT NULL, objective TEXT NOT NULL,
 matter VARCHAR(24) NOT NULL, state VARCHAR(24) NOT NULL DEFAULT 'EN_GESTION',
 created_ms BIGINT NOT NULL, created_by BIGINT NOT NULL, closed_ms BIGINT, closed_by BIGINT,
 closure_mode VARCHAR(32), close_reason TEXT, origin_case_id BIGINT,
 revision BIGINT NOT NULL DEFAULT 0,
 FOREIGN KEY(requirement_id) REFERENCES requirement(id), FOREIGN KEY(scope_id) REFERENCES org_scope(id),
 FOREIGN KEY(responsible_id) REFERENCES staff(id), FOREIGN KEY(created_by) REFERENCES staff(id),
 FOREIGN KEY(closed_by) REFERENCES staff(id), FOREIGN KEY(origin_case_id) REFERENCES case_record(id)
);
CREATE INDEX ix_case_req ON case_record(requirement_id,state);
CREATE TABLE case_assignment (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, case_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
 assigned_by BIGINT NOT NULL, assigned_ms BIGINT NOT NULL, acknowledged_ms BIGINT, current_flag BOOLEAN NOT NULL,
 FOREIGN KEY(case_id) REFERENCES case_record(id), FOREIGN KEY(user_id) REFERENCES staff(id), FOREIGN KEY(assigned_by) REFERENCES staff(id)
);
CREATE TABLE case_member (
 case_id BIGINT NOT NULL, user_id BIGINT NOT NULL, added_by BIGINT NOT NULL, added_ms BIGINT NOT NULL,
 PRIMARY KEY(case_id,user_id), FOREIGN KEY(case_id) REFERENCES case_record(id), FOREIGN KEY(user_id) REFERENCES staff(id),
 FOREIGN KEY(added_by) REFERENCES staff(id)
);
CREATE TABLE management_note (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, requirement_id BIGINT NOT NULL, case_id BIGINT,
 author_id BIGINT NOT NULL, effective_ms BIGINT NOT NULL, registered_ms BIGINT NOT NULL,
 body MEDIUMTEXT NOT NULL, reserved BOOLEAN NOT NULL DEFAULT FALSE, rectifies_id BIGINT, correction_reason TEXT,
 FOREIGN KEY(requirement_id) REFERENCES requirement(id), FOREIGN KEY(case_id) REFERENCES case_record(id),
 FOREIGN KEY(author_id) REFERENCES staff(id), FOREIGN KEY(rectifies_id) REFERENCES management_note(id)
);
CREATE TABLE task_item (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, case_id BIGINT NOT NULL, title VARCHAR(300) NOT NULL,
 executor_id BIGINT NOT NULL, created_by BIGINT NOT NULL, created_ms BIGINT NOT NULL,
 due_ms BIGINT, mandatory BOOLEAN NOT NULL DEFAULT FALSE, state VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
 revision BIGINT NOT NULL DEFAULT 0, FOREIGN KEY(case_id) REFERENCES case_record(id),
 FOREIGN KEY(executor_id) REFERENCES staff(id), FOREIGN KEY(created_by) REFERENCES staff(id)
);
CREATE TABLE flow_request (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, kind VARCHAR(24) NOT NULL, requirement_id BIGINT NOT NULL,
 origin_case_id BIGINT, origin_scope BIGINT NOT NULL, target_scope BIGINT NOT NULL,
 requested_by BIGINT NOT NULL, created_ms BIGINT NOT NULL, reason TEXT NOT NULL, objective TEXT,
 state VARCHAR(16) NOT NULL DEFAULT 'PENDIENTE', decided_by BIGINT, decided_ms BIGINT,
 decision_reason TEXT, successor_case_id BIGINT, revision BIGINT NOT NULL DEFAULT 0,
 FOREIGN KEY(requirement_id) REFERENCES requirement(id), FOREIGN KEY(origin_case_id) REFERENCES case_record(id),
 FOREIGN KEY(origin_scope) REFERENCES org_scope(id), FOREIGN KEY(target_scope) REFERENCES org_scope(id),
 FOREIGN KEY(requested_by) REFERENCES staff(id), FOREIGN KEY(decided_by) REFERENCES staff(id),
 FOREIGN KEY(successor_case_id) REFERENCES case_record(id)
);
CREATE TABLE communication_link (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, communication_id BIGINT NOT NULL UNIQUE, requirement_id BIGINT NOT NULL,
 reason TEXT NOT NULL, proposed_by BIGINT NOT NULL, proposed_ms BIGINT NOT NULL,
 state VARCHAR(16) NOT NULL DEFAULT 'PROPUESTA', target_case_id BIGINT,
 decided_by BIGINT, decided_ms BIGINT, decision_reason TEXT, revision BIGINT NOT NULL DEFAULT 0,
 FOREIGN KEY(communication_id) REFERENCES communication(id), FOREIGN KEY(requirement_id) REFERENCES requirement(id),
 FOREIGN KEY(proposed_by) REFERENCES staff(id), FOREIGN KEY(target_case_id) REFERENCES case_record(id),
 FOREIGN KEY(decided_by) REFERENCES staff(id)
);
CREATE TABLE reopening (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, requirement_id BIGINT NOT NULL, case_id BIGINT,
 requested_by BIGINT NOT NULL, requested_ms BIGINT NOT NULL, reason TEXT NOT NULL,
 state VARCHAR(16) NOT NULL DEFAULT 'PENDIENTE', principal_approved_by BIGINT, principal_approved_ms BIGINT,
 principal_scope_at_approval BIGINT, decided_by BIGINT, decided_ms BIGINT, decision_reason TEXT,
 revision BIGINT NOT NULL DEFAULT 0, FOREIGN KEY(requirement_id) REFERENCES requirement(id),
 FOREIGN KEY(case_id) REFERENCES case_record(id), FOREIGN KEY(requested_by) REFERENCES staff(id),
 FOREIGN KEY(principal_approved_by) REFERENCES staff(id), FOREIGN KEY(decided_by) REFERENCES staff(id)
);
CREATE TABLE document_item (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, communication_id BIGINT, requirement_id BIGINT, case_id BIGINT,
 name VARCHAR(180) NOT NULL, storage_key VARCHAR(80) NOT NULL UNIQUE, sha256 CHAR(64) NOT NULL,
 size_bytes BIGINT NOT NULL, content_type VARCHAR(120) NOT NULL, uploaded_by BIGINT NOT NULL,
 created_ms BIGINT NOT NULL, reserved BOOLEAN NOT NULL DEFAULT FALSE, quarantined BOOLEAN NOT NULL DEFAULT TRUE,
 FOREIGN KEY(communication_id) REFERENCES communication(id), FOREIGN KEY(requirement_id) REFERENCES requirement(id),
 FOREIGN KEY(case_id) REFERENCES case_record(id), FOREIGN KEY(uploaded_by) REFERENCES staff(id),
 CHECK((communication_id IS NOT NULL AND requirement_id IS NULL AND case_id IS NULL) OR
 (communication_id IS NULL AND requirement_id IS NOT NULL AND case_id IS NULL) OR
 (communication_id IS NULL AND requirement_id IS NULL AND case_id IS NOT NULL))
);
CREATE TABLE audit_event (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, actor_id BIGINT NOT NULL, at_ms BIGINT NOT NULL,
 action VARCHAR(48) NOT NULL, resource_type VARCHAR(24) NOT NULL, resource_id BIGINT NOT NULL,
 scope_id BIGINT, note TEXT NOT NULL, FOREIGN KEY(actor_id) REFERENCES staff(id), FOREIGN KEY(scope_id) REFERENCES org_scope(id)
);
CREATE INDEX ix_audit_resource ON audit_event(resource_type,resource_id,id);
CREATE TABLE inbox_notice (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, at_ms BIGINT NOT NULL,
 text_value VARCHAR(300) NOT NULL, resource_type VARCHAR(24) NOT NULL, resource_id BIGINT NOT NULL,
 read_ms BIGINT, FOREIGN KEY(user_id) REFERENCES staff(id)
);
CREATE TABLE command_log (
 command_key VARCHAR(36) PRIMARY KEY, actor_id BIGINT NOT NULL, payload_hash CHAR(64) NOT NULL,
 result_kind VARCHAR(24) NOT NULL, result_id BIGINT NOT NULL, result_message VARCHAR(300) NOT NULL,
 created_ms BIGINT NOT NULL, FOREIGN KEY(actor_id) REFERENCES staff(id)
);

CREATE TABLE closure_record (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, requirement_id BIGINT NOT NULL, case_id BIGINT,
 actor_id BIGINT NOT NULL, closed_ms BIGINT NOT NULL, mode VARCHAR(32) NOT NULL, reason TEXT NOT NULL,
 FOREIGN KEY(requirement_id) REFERENCES requirement(id), FOREIGN KEY(case_id) REFERENCES case_record(id),
 FOREIGN KEY(actor_id) REFERENCES staff(id)
);
