-- Proyecciones reconstruibles. Las fichas conservan la fuente y la fecha de nacimiento.
CREATE TABLE stat_declared_version (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 communication_id BIGINT NOT NULL,
 source VARCHAR(12) NOT NULL,
 source_id BIGINT NOT NULL,
 registered_ms BIGINT NOT NULL,
 requester_region VARCHAR(4),
 requester_commune VARCHAR(120),
 facts_region VARCHAR(4),
 facts_commune VARCHAR(120),
 FOREIGN KEY(communication_id) REFERENCES communication(id),
 UNIQUE(source,source_id)
);
CREATE INDEX ix_stat_declared_com_at ON stat_declared_version(communication_id,registered_ms,id);

CREATE TABLE stat_declared_person (
 version_id BIGINT NOT NULL,
 person_order INT NOT NULL,
 person_role VARCHAR(24) NOT NULL,
 region_code VARCHAR(4),
 commune VARCHAR(120),
 age_years INT,
 age_origin VARCHAR(16),
 nationality VARCHAR(200),
 PRIMARY KEY(version_id,person_order),
 FOREIGN KEY(version_id) REFERENCES stat_declared_version(id)
);
