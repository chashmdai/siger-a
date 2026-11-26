-- Las tablas de exportación se escriben con el corte. La serialización V23 sigue leyéndose con sus seis tablas.
CREATE TABLE stat_export_cut_folio (
 cut_id BIGINT NOT NULL,
 communication_id BIGINT NOT NULL,
 folio VARCHAR(48),
 PRIMARY KEY(cut_id,communication_id)
);
CREATE TABLE stat_export_cut_origin (
 cut_id BIGINT NOT NULL,
 source_kind VARCHAR(8) NOT NULL,
 value_id BIGINT NOT NULL,
 record_type VARCHAR(8) NOT NULL,
 record_id BIGINT NOT NULL,
 PRIMARY KEY(cut_id,source_kind,value_id)
);
CREATE TABLE stat_export_cut_integrity (
 cut_id BIGINT PRIMARY KEY,
 export_sha256 CHAR(64) NOT NULL
);

CREATE TABLE stat_series (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 title VARCHAR(200) NOT NULL,
 indicator VARCHAR(40) NOT NULL,
 scope_id BIGINT,
 source VARCHAR(300) NOT NULL,
 methodology TEXT NOT NULL,
 granularity VARCHAR(20) NOT NULL,
 period_from VARCHAR(10) NOT NULL,
 period_to VARCHAR(10) NOT NULL,
 imported_by BIGINT NOT NULL,
 imported_ms BIGINT NOT NULL,
 basis TEXT NOT NULL,
 file_sha256 CHAR(64) NOT NULL,
 state VARCHAR(12) NOT NULL,
 revision BIGINT NOT NULL,
 annulled_by BIGINT,
 annulled_ms BIGINT,
 annul_basis TEXT,
 FOREIGN KEY(scope_id) REFERENCES org_scope(id),
 FOREIGN KEY(imported_by) REFERENCES staff(id),
 FOREIGN KEY(annulled_by) REFERENCES staff(id)
);
CREATE INDEX ix_stat_series_overlap ON stat_series(indicator,state,scope_id,period_from,period_to);
CREATE TABLE stat_series_value (
 series_id BIGINT NOT NULL,
 ordinal INT NOT NULL,
 period_from VARCHAR(10) NOT NULL,
 period_to VARCHAR(10) NOT NULL,
 dimension VARCHAR(40) NOT NULL,
 category_label VARCHAR(200) NOT NULL,
 category_code VARCHAR(200),
 is_total BOOLEAN NOT NULL,
 amount BIGINT NOT NULL CHECK(amount>=0),
 PRIMARY KEY(series_id,ordinal),
 FOREIGN KEY(series_id) REFERENCES stat_series(id)
);
