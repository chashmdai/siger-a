-- V14: hitos de la cadena COM -> SOL -> REQ -> Caso (0.5.0, D6, gate G10). Aditiva sobre V1-V13.
-- Dato derivado para los reportes de tiempos (G11): Milestones escribe un hito por transicion en la misma transaccion
-- del comando y la carga historica (marcador MILESTONES_V14) reconstruye los anteriores con reconstructed=TRUE.
-- event: codigo del hito. resource_type/resource_id: entidad del hito (COM, SOL, REQ, CAS, ASIGNACION = case_assignment,
-- TAREA, ENTREGA = attention_handoff, FLUJO = flow_request). communication_id: COM de la cadena (siempre existe).
-- subject_user: persona a cargo o destinataria (responsable, ejecutor, encargado). scope_id: ambito que debe actuar
-- o actua. at_ms: instante del sistema, nunca una fecha declarada. detail: codigo corto (decision, tipo de flujo,
-- modo de cierre), nunca texto libre. Valores validados en Java, sin CHECK.
CREATE TABLE milestone (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 event VARCHAR(32) NOT NULL,
 resource_type VARCHAR(16) NOT NULL,
 resource_id BIGINT NOT NULL,
 communication_id BIGINT NOT NULL,
 requirement_id BIGINT,
 case_id BIGINT,
 actor_id BIGINT NOT NULL,
 subject_user BIGINT,
 scope_id BIGINT,
 at_ms BIGINT NOT NULL,
 detail VARCHAR(32),
 reconstructed BOOLEAN NOT NULL DEFAULT FALSE,
 FOREIGN KEY(communication_id) REFERENCES communication(id),
 FOREIGN KEY(requirement_id) REFERENCES requirement(id),
 FOREIGN KEY(case_id) REFERENCES case_record(id),
 FOREIGN KEY(actor_id) REFERENCES staff(id),
 FOREIGN KEY(subject_user) REFERENCES staff(id),
 FOREIGN KEY(scope_id) REFERENCES org_scope(id)
);
CREATE INDEX ix_milestone_event_at ON milestone(event,at_ms);
CREATE INDEX ix_milestone_requirement ON milestone(requirement_id);
CREATE INDEX ix_milestone_resource ON milestone(resource_type,resource_id);
CREATE INDEX ix_milestone_communication ON milestone(communication_id);
-- Cantidad asociada a un marcador de arranque (MILESTONES_V14: eventos historicos omitidos por formato no reconocido).
ALTER TABLE application_marker ADD COLUMN amount BIGINT;
