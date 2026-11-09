-- 0.3.1: cambios aditivos; V1, V2 y V3 se conservan sin modificar.
CREATE TABLE application_marker (code VARCHAR(80) PRIMARY KEY, applied_ms BIGINT NOT NULL);
-- No volver a conceder roles demo revocados al actualizar una BD ya inicializada.
INSERT INTO application_marker(code,applied_ms)
 SELECT 'DEMO_ORGANIZATION_030',0 FROM app_meta
 WHERE id=1 AND EXISTS(SELECT 1 FROM org_scope WHERE code='UPR')
 AND EXISTS(SELECT 1 FROM staff WHERE username='direccion.upd');
CREATE TABLE substitution_scope (
 substitution_id BIGINT NOT NULL, grant_id BIGINT NOT NULL, scope_id BIGINT NOT NULL, role_code VARCHAR(32) NOT NULL,
 PRIMARY KEY(substitution_id,grant_id), FOREIGN KEY(substitution_id) REFERENCES substitution(id),
 FOREIGN KEY(grant_id) REFERENCES role_assignment(id), FOREIGN KEY(scope_id) REFERENCES org_scope(id)
);
-- Conservar la funcion efectiva de subrogancias anteriores, sin dar acceso a otras unidades.
INSERT INTO substitution_scope(substitution_id,grant_id,scope_id,role_code)
 SELECT x.id,g.id,g.scope_id,g.role_code FROM substitution x
 JOIN substitution_designation d ON d.id=x.designation_id JOIN org_scope root ON root.id=d.scope_id
 JOIN role_assignment g ON g.user_id=d.titular_id JOIN org_scope sc ON sc.id=g.scope_id
 WHERE g.revoked_at IS NULL AND g.role_code IN ('JEFATURA','DIRECCION')
 AND (g.scope_id=d.scope_id OR (root.regional=FALSE AND COALESCE(root.area_code,'')=''
 AND g.role_code='DIRECCION' AND sc.unit_code=root.unit_code AND sc.site_code=root.site_code
 AND sc.code<>'UPR-REQ'));
ALTER TABLE communication ADD COLUMN contact_email VARCHAR(200);
ALTER TABLE communication ADD COLUMN contact_phone VARCHAR(80);
ALTER TABLE communication ADD COLUMN contact_restrictions VARCHAR(1000);
ALTER TABLE communication ADD COLUMN presenter_domicile VARCHAR(500);
CREATE TABLE communication_person (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, communication_id BIGINT NOT NULL,
 person_role VARCHAR(24) NOT NULL, name VARCHAR(200), identity_document VARCHAR(100),
 relationship VARCHAR(200), location_text VARCHAR(500), FOREIGN KEY(communication_id) REFERENCES communication(id)
);
CREATE TABLE intake_upload (
 token VARCHAR(36) PRIMARY KEY, user_id BIGINT NOT NULL, scope_id BIGINT NOT NULL,
 name VARCHAR(180) NOT NULL, storage_key VARCHAR(80) NOT NULL UNIQUE, sha256 CHAR(64) NOT NULL,
 size_bytes BIGINT NOT NULL, created_ms BIGINT NOT NULL, communication_id BIGINT,
 FOREIGN KEY(user_id) REFERENCES staff(id), FOREIGN KEY(scope_id) REFERENCES org_scope(id),
 FOREIGN KEY(communication_id) REFERENCES communication(id)
);
ALTER TABLE communication_link ADD COLUMN returned_ms BIGINT;
ALTER TABLE communication_link ADD COLUMN returned_by BIGINT;
ALTER TABLE communication_link ADD CONSTRAINT fk_link_return_actor FOREIGN KEY(returned_by) REFERENCES staff(id);
ALTER TABLE communication_link ADD COLUMN return_reason TEXT;
ALTER TABLE document_item ADD COLUMN note_id BIGINT;
ALTER TABLE document_item ADD CONSTRAINT fk_document_note FOREIGN KEY(note_id) REFERENCES management_note(id);
ALTER TABLE document_item ADD COLUMN document_type VARCHAR(32) NOT NULL DEFAULT 'OTRO';
ALTER TABLE document_item ADD COLUMN description VARCHAR(500);
ALTER TABLE document_item ADD COLUMN previous_document_id BIGINT;
ALTER TABLE document_item ADD CONSTRAINT fk_document_previous FOREIGN KEY(previous_document_id) REFERENCES document_item(id);
ALTER TABLE document_item ADD COLUMN upload_key VARCHAR(36);
CREATE UNIQUE INDEX ux_document_upload ON document_item(upload_key);
CREATE UNIQUE INDEX ux_document_previous ON document_item(previous_document_id);
CREATE INDEX ix_com_queue ON communication(state,origin_scope,id);
CREATE INDEX ix_sol_queue ON solicitation(state,destination_scope,id);
CREATE INDEX ix_req_queue ON requirement(state,principal_scope,id);
CREATE INDEX ix_case_scope_state ON case_record(scope_id,state,id);
CREATE INDEX ix_case_owner_state ON case_record(responsible_id,state,id);
CREATE INDEX ix_assignment_current ON case_assignment(case_id,current_flag,acknowledged_ms);
CREATE INDEX ix_task_owner_due ON task_item(executor_id,state,due_ms,id);
CREATE INDEX ix_flow_target_state ON flow_request(target_scope,state,id);
CREATE INDEX ix_notice_unread ON inbox_notice(user_id,read_ms,id);
CREATE INDEX ix_intake_upload_owner ON intake_upload(user_id,communication_id,created_ms);
