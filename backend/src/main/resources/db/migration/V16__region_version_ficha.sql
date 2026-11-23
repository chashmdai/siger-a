-- V16: region de origen por version de la ficha (0.7.0, D21, gate T02). Aditiva sobre V1-V15.
-- Dato derivado y reconstruible (OriginRegion): codigo de region del catalogo del Formulario que registra cada version
-- de la ficha de primera atencion (data_json.region solo si es un codigo valido; nulo = sin region declarada). Se escribe
-- al crear la version desde sus propios datos; las versiones anteriores las calcula la carga inicial (marcador
-- REGION_FICHA_V16). No cambia data_json ni la recepcion del Formulario. Valores validados en Java, sin CHECK.
ALTER TABLE attention_revision ADD COLUMN region_code VARCHAR(4);
