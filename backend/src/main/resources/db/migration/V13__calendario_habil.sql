-- V13: calendario habil (0.5.0, D6, gate G09). Aditiva sobre V1-V12.
-- Feriados que mantiene TI (HOLIDAY_ADD, HOLIDAY_IMPORT, HOLIDAY_REMOVE); no se siembran feriados en codigo ni en
-- migraciones. region_code nulo = feriado nacional; si no, codigo de region del Formulario (feriado regional).
-- region_key repite la region con 'NAC' para los nacionales: un indice UNIQUE sobre una columna nula admite varios
-- nulos en H2 y en MySQL, asi que la unicidad (holiday_day, region) se exige sobre region_key, que nunca es nula.
-- La columna se llama holiday_day porque DAY es palabra reservada en H2 2.x. Valores validados en Java (BusinessCalendar/Holidays), sin CHECK.
CREATE TABLE business_holiday (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 holiday_day CHAR(10) NOT NULL,
 region_code VARCHAR(4),
 region_key VARCHAR(4) NOT NULL,
 name VARCHAR(120) NOT NULL,
 created_by BIGINT NOT NULL,
 created_ms BIGINT NOT NULL,
 FOREIGN KEY(created_by) REFERENCES staff(id)
);
CREATE UNIQUE INDEX ux_business_holiday_day_region ON business_holiday(holiday_day,region_key);
