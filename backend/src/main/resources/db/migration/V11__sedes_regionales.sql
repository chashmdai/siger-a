-- V11: sedes regionales (0.5.0, D7-D9). Aditiva sobre V1-V10.
-- Cada ambito tiene estado (PROYECTADA, ACTIVA o CERRADA) coherente con enabled: ACTIVA = habilitado; PROYECTADA y
-- CERRADA = deshabilitado. Las sedes llevan region (codigo del Formulario) y fecha de apertura. revision da
-- concurrencia optimista a los comandos SCOPE_*. Los valores se validan en Java (sin CHECK que H2 y MySQL traten distinto).
ALTER TABLE org_scope ADD COLUMN region_code VARCHAR(4);
ALTER TABLE org_scope ADD COLUMN state VARCHAR(12) NOT NULL DEFAULT 'ACTIVA';
ALTER TABLE org_scope ADD COLUMN opens_ms BIGINT;
ALTER TABLE org_scope ADD COLUMN revision BIGINT NOT NULL DEFAULT 0;
-- Los ambitos deshabilitados de versiones anteriores quedan CERRADA; los demas, ACTIVA.
UPDATE org_scope SET state='CERRADA' WHERE enabled=FALSE;
-- Materia (R02): sugerida al enviar una SOL a una sede y heredada por el REQ al asumirla. Nula hasta entonces.
ALTER TABLE solicitation ADD COLUMN suggested_matter VARCHAR(8);
ALTER TABLE requirement ADD COLUMN matter VARCHAR(8);
