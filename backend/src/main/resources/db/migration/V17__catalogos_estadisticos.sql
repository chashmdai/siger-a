-- V17: catalogos estadisticos versionados (0.8.0, D27, gate E01). Aditiva sobre V1-V16.
-- Cada version de un catalogo (lista cerrada en StatCatalogs.CATALOGS) se importa como PROPUESTA (CATALOG_IMPORT, solo
-- TI, todo o nada, con fundamento), se activa como VIGENTE (CATALOG_ACTIVATE, con fundamento; la VIGENTE anterior pasa
-- a RETIRADA en el mismo acto) y nunca se borra. current_catalog repite el catalogo solo mientras la version esta
-- VIGENTE (nulo en otro estado): su indice UNIQUE garantiza una sola VIGENTE por catalogo, porque un UNIQUE admite
-- varios nulos en H2 y en MySQL. CURRENT_CATALOG es palabra reservada en H2 2.x: la columna va siempre entre
-- acentos graves (`current_catalog`), que H2 en MODE=MySQL y MySQL aceptan. file_sha256 = SHA-256 del archivo importado sin BOM y con saltos LF.
-- Los items (codigo, rotulo, jerarquia por parent_code, nivel y orden calculados por el servidor, seleccionable,
-- rango de anios solo en TRAMO_EDAD y referencia de la fuente) y las correspondencias entre versiones
-- (CATALOG_MAPPING_IMPORT) se escriben solo al importar. Ninguna migracion siembra catalogos (DISENO 3.1).
-- Valores validados en Java (StatCatalogs), sin CHECK que H2 y MySQL traten distinto.
CREATE TABLE stat_catalog_version (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 catalog VARCHAR(32) NOT NULL,
 label VARCHAR(200) NOT NULL,
 state VARCHAR(12) NOT NULL,
 source VARCHAR(500) NOT NULL,
 file_sha256 CHAR(64) NOT NULL,
 loaded_by BIGINT NOT NULL,
 loaded_ms BIGINT NOT NULL,
 basis TEXT NOT NULL,
 activated_by BIGINT,
 activated_ms BIGINT,
 activation_basis TEXT,
 retired_by BIGINT,
 retired_ms BIGINT,
 `current_catalog` VARCHAR(32),
 revision BIGINT NOT NULL DEFAULT 0,
 FOREIGN KEY(loaded_by) REFERENCES staff(id),
 FOREIGN KEY(activated_by) REFERENCES staff(id),
 FOREIGN KEY(retired_by) REFERENCES staff(id)
);
CREATE UNIQUE INDEX ux_stat_catalog_current ON stat_catalog_version(`current_catalog`);
CREATE INDEX ix_stat_catalog_version_catalog ON stat_catalog_version(catalog,id);
CREATE TABLE stat_catalog_item (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 version_id BIGINT NOT NULL,
 code VARCHAR(40) NOT NULL,
 label VARCHAR(300) NOT NULL,
 parent_code VARCHAR(40),
 level INT NOT NULL,
 sort INT NOT NULL,
 selectable BOOLEAN NOT NULL,
 range_from INT,
 range_to INT,
 source_ref VARCHAR(80),
 FOREIGN KEY(version_id) REFERENCES stat_catalog_version(id)
);
CREATE UNIQUE INDEX ux_stat_catalog_item_code ON stat_catalog_item(version_id,code);
CREATE TABLE stat_catalog_mapping (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 from_version BIGINT NOT NULL,
 from_code VARCHAR(40) NOT NULL,
 to_version BIGINT NOT NULL,
 to_code VARCHAR(40) NOT NULL,
 loaded_by BIGINT NOT NULL,
 loaded_ms BIGINT NOT NULL,
 basis TEXT NOT NULL,
 FOREIGN KEY(from_version) REFERENCES stat_catalog_version(id),
 FOREIGN KEY(to_version) REFERENCES stat_catalog_version(id),
 FOREIGN KEY(loaded_by) REFERENCES staff(id)
);
CREATE UNIQUE INDEX ux_stat_catalog_mapping ON stat_catalog_mapping(from_version,from_code,to_version,to_code);
CREATE INDEX ix_stat_catalog_mapping_to ON stat_catalog_mapping(to_version,to_code);
