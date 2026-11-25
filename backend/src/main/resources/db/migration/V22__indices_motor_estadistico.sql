-- V22: indices del motor estadistico (0.8.0, gate E08). Aditiva sobre V1-V21, sin columnas ni datos.
-- Cada indice responde a un recorrido completo que mostro EXPLAIN en MySQL 8.4 con 50.005 COM sinteticas (evidencia en
-- docs/BITACORA_0_8_0.md, E08): las cuatro fechas de analisis y el corte "al constituirse el REQ"
-- (TerritorialDims.constitutedSql), que busca el primer comando con ese REQ como resultado.
CREATE INDEX ix_communication_received ON communication(received_ms);
CREATE INDEX ix_communication_created ON communication(created_ms);
CREATE INDEX ix_requirement_created ON requirement(created_ms);
CREATE INDEX ix_case_created ON case_record(created_ms);
CREATE INDEX ix_command_log_result ON command_log(result_kind,result_id,created_ms);
