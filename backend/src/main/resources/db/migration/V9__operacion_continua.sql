-- V9: operacion continua. Aditiva sobre V1-V8 (no modifica tablas ni datos existentes).
-- Control (pausa auditada por TI) y estado (latido sin datos ciudadanos) de las integraciones permanentes:
-- consumidor automatico del Formulario y correo institucional Graph con outbox durable.

CREATE TABLE integration_control (
 id BIGINT PRIMARY KEY,
 name VARCHAR(32) NOT NULL UNIQUE,
 paused BOOLEAN NOT NULL DEFAULT FALSE,
 revision BIGINT NOT NULL DEFAULT 0,
 updated_ms BIGINT NOT NULL DEFAULT 0,
 updated_by BIGINT,
 reason VARCHAR(1000),
 FOREIGN KEY(updated_by) REFERENCES staff(id),
 CHECK(name IN ('FORMULARIO_PULL','CORREO_GRAPH'))
);
INSERT INTO integration_control(id,name) VALUES(1,'FORMULARIO_PULL');
INSERT INTO integration_control(id,name) VALUES(2,'CORREO_GRAPH');

CREATE TABLE integration_status (
 name VARCHAR(32) PRIMARY KEY,
 state VARCHAR(32) NOT NULL DEFAULT 'SIN_ACTIVIDAD',
 holder VARCHAR(36),
 cycles BIGINT NOT NULL DEFAULT 0,
 failures INT NOT NULL DEFAULT 0,
 last_cycle_ms BIGINT,
 last_success_ms BIGINT,
 next_ms BIGINT,
 last_error VARCHAR(64),
 updated_ms BIGINT NOT NULL DEFAULT 0,
 CHECK(name IN ('FORMULARIO_PULL','CORREO_GRAPH'))
);
INSERT INTO integration_status(name) VALUES('FORMULARIO_PULL');
INSERT INTO integration_status(name) VALUES('CORREO_GRAPH');

-- Un correo por aviso interno, creado en el mismo COMMIT que el aviso. El estado AMBIGUO (pudo salir o no)
-- nunca se reenvia automaticamente: lo resuelve TI con una decision auditada.
CREATE TABLE mail_outbox (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 notice_id BIGINT NOT NULL UNIQUE,
 recipient_id BIGINT NOT NULL,
 correlation_id CHAR(36) NOT NULL UNIQUE,
 state VARCHAR(16) NOT NULL,
 attempts INT NOT NULL DEFAULT 0,
 next_ms BIGINT NOT NULL,
 claim_id CHAR(36),
 lease_until_ms BIGINT,
 last_phase VARCHAR(24),
 last_http INT,
 last_request_id VARCHAR(36),
 accepted_ms BIGINT,
 resolution VARCHAR(16),
 resolved_by BIGINT,
 resolved_ms BIGINT,
 created_ms BIGINT NOT NULL,
 updated_ms BIGINT NOT NULL,
 FOREIGN KEY(notice_id) REFERENCES inbox_notice(id),
 FOREIGN KEY(recipient_id) REFERENCES staff(id),
 FOREIGN KEY(resolved_by) REFERENCES staff(id),
 CHECK(state IN ('PENDIENTE','EN_INTENTO','ACEPTADO','REINTENTABLE','AMBIGUO','RECHAZADO','SUPRIMIDO','DESCARTADO'))
);
CREATE INDEX ix_mail_outbox_due ON mail_outbox(state,next_ms,id);

-- Cada intento queda registrado ANTES de la red; el resultado se completa despues.
CREATE TABLE mail_attempt (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 outbox_id BIGINT NOT NULL,
 claim_id CHAR(36) NOT NULL UNIQUE,
 address VARCHAR(200) NOT NULL,
 started_ms BIGINT NOT NULL,
 finished_ms BIGINT,
 state VARCHAR(16) NOT NULL,
 phase VARCHAR(24) NOT NULL,
 http_status INT,
 request_id VARCHAR(36),
 FOREIGN KEY(outbox_id) REFERENCES mail_outbox(id)
);
CREATE INDEX ix_mail_attempt_outbox ON mail_attempt(outbox_id,id);
