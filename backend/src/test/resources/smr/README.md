Vectores completamente sinteticos, copiados de SMR continuo (commit 02d4184,
`siger-institucional/backend/src/test/resources/smr/pull-respuesta-1.0.json` y `-1.1.json`):
se extrajo solo el objeto `entrega` de cada respuesta. No contienen datos ciudadanos reales.
Ambos usan el mismo identificador de operacion con contenido distinto (escenarios independientes).

La clave publica sintetica `claves-formulario-sinteticas.json` es `fixture-productor-public.pem` de SMR (misma procedencia), con keyId FORM_FIXTURE_PUBLIC. No es una clave institucional.

`clave-firma-prueba-siger.pem` es una clave Ed25519 generada solo para las pruebas de SIGER A (keyId `PRUEBA_SIGER`,
publica incluida en `claves-formulario-sinteticas.json`). Firma documentos sinteticos construidos por las pruebas;
no es una clave del Formulario ni institucional y nunca debe configurarse fuera de las pruebas.
