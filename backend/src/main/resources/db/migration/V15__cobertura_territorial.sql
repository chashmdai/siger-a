-- V15: cobertura territorial (0.7.0, D18-D19, gate T01). Aditiva sobre V1-V14.
-- Cada fila es un periodo en que una sede regional (scope_id) cubre una region del Formulario ademas de su region
-- fisica (org_scope.region_code, que no se repite aqui), o en que el nivel central (scope_id nulo) atiende esa region.
-- Un periodo abierto (until_ms nulo) es la cobertura vigente; terminar fija until_ms, until_by y until_basis y nunca
-- borra la fila: la historia queda consultable. Los comandos de TI (SCOPE_COVERAGE, CENTRAL_COVERAGE, SCOPE_CLOSE) y
-- el archivo de estructura inicial son las unicas escrituras. No se siembra ninguna region en migraciones.
-- Valores validados en Java (Territory, Personnel), sin CHECK que H2 y MySQL traten distinto.
CREATE TABLE territorial_coverage (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 scope_id BIGINT,
 region_code VARCHAR(4) NOT NULL,
 from_ms BIGINT NOT NULL,
 from_by BIGINT NOT NULL,
 from_basis TEXT NOT NULL,
 until_ms BIGINT,
 until_by BIGINT,
 until_basis TEXT,
 FOREIGN KEY(scope_id) REFERENCES org_scope(id),
 FOREIGN KEY(from_by) REFERENCES staff(id),
 FOREIGN KEY(until_by) REFERENCES staff(id)
);
CREATE INDEX ix_territorial_coverage_scope ON territorial_coverage(scope_id,until_ms);
