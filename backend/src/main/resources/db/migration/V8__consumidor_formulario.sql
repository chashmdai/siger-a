-- Consumidor institucional del Formulario Publico (PULL/ACK). Aditivo sobre V1-V7, compatible con H2 y MySQL.

-- Identidad tecnica interna: nunca inicia sesion, no recibe funciones y no se administra como cuenta de funcionario.
ALTER TABLE staff ADD COLUMN technical BOOLEAN NOT NULL DEFAULT FALSE;

-- Estado durable de cada entrega del Formulario: reserva vigente, comprobante institucional emitido y ACK pendiente.
-- Solo identificadores, huellas y codigos tecnicos; ningun dato ciudadano.
CREATE TABLE formulario_reception (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 operation_id VARCHAR(36) NOT NULL UNIQUE,
 state VARCHAR(16) NOT NULL,
 document_version VARCHAR(8),
 channel VARCHAR(40),
 content_hash CHAR(64),
 provenance_hash CHAR(64),
 lease_id VARCHAR(36),
 lease_until_ms BIGINT,
 attempt INT NOT NULL DEFAULT 0,
 communication_id BIGINT UNIQUE,
 institutional_id VARCHAR(36) UNIQUE,
 folio_number BIGINT,
 folio VARCHAR(40),
 registered_ms BIGINT,
 receipt MEDIUMTEXT,
 receipt_key_id VARCHAR(64),
 ack_pending BOOLEAN NOT NULL DEFAULT FALSE,
 ack_attempts INT NOT NULL DEFAULT 0,
 ack_next_ms BIGINT NOT NULL DEFAULT 0,
 ack_blocked BOOLEAN NOT NULL DEFAULT FALSE,
 acked_ms BIGINT,
 last_error VARCHAR(64),
 created_ms BIGINT NOT NULL,
 updated_ms BIGINT NOT NULL,
 CONSTRAINT formulario_reception_state CHECK (state IN ('RECHAZADA','REGISTRADA','CONFIRMADA')),
 FOREIGN KEY(communication_id) REFERENCES communication(id)
);

-- Originales ciudadanos descargados del Formulario: el archivo vive en document_item (cuarentena, autorizacion de la COM)
-- y este vinculo conserva el descriptor firmado (operacion, adjunto y tipo declarado).
CREATE TABLE external_document (
 document_id BIGINT PRIMARY KEY,
 communication_id BIGINT NOT NULL,
 operation_id VARCHAR(36) NOT NULL,
 attachment_id VARCHAR(36) NOT NULL,
 media_type VARCHAR(255) NOT NULL,
 CONSTRAINT external_document_attachment UNIQUE(communication_id,attachment_id),
 FOREIGN KEY(document_id) REFERENCES document_item(id),
 FOREIGN KEY(communication_id) REFERENCES communication(id)
);

-- Un solo ciclo consumidor de SIGER A a la vez (lease con vencimiento; no coordina con otros sistemas).
CREATE TABLE formulario_consumer_lock (
 id INT PRIMARY KEY,
 holder VARCHAR(36),
 until_ms BIGINT NOT NULL DEFAULT 0
);
INSERT INTO formulario_consumer_lock(id,until_ms) VALUES(1,0);
