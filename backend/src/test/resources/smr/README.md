Vectores completamente sinteticos, copiados de SMR continuo (commit 02d4184,
`siger-institucional/backend/src/test/resources/smr/pull-respuesta-1.0.json` y `-1.1.json`):
se extrajo solo el objeto `entrega` de cada respuesta. No contienen datos ciudadanos reales.
Ambos usan el mismo identificador de operacion con contenido distinto (escenarios independientes).

La clave publica sintetica `claves-formulario-sinteticas.json` es `fixture-productor-public.pem` de SMR (misma procedencia), con keyId FORM_FIXTURE_PUBLIC. No es una clave institucional.

La clave de prueba `PRUEBA_SIGER` no se versiona: `FormularioDocs` genera el par Ed25519 en memoria en cada ejecucion,
firma con ese par los documentos sinteticos que construyen las pruebas y escribe un archivo de confianza temporal en `target/`
(esta publica sintetica de SMR mas la publica `PRUEBA_SIGER` de esa ejecucion), que las pruebas configuran como
`siger.formulario-trusted-keys`. Los vectores de SMR siguen verificandose solo con `claves-formulario-sinteticas.json`.
Ninguna de estas claves es del Formulario ni institucional, y nunca deben configurarse fuera de las pruebas.

`ack-solicitud-1.0.json` y `ack-solicitud-1.1.json` son copia sin cambios de
`formulario-web/docs/smr-ejemplos` (commit bbed7ad): ACK sinteticos del productor (keyId SMR_FIXTURE_ACK, solo clave publica),
usados para comprobar que el payload del comprobante institucional de SIGER A es identico byte a byte (la clave publica
`fixture-ack-public.pem` va incrustada en FormularioAckTest porque `.gitignore` excluye `*.pem`). El original de 44 bytes
del vector 1.1 (`original-sintetico.txt`) va incrustado en FormularioConsumerTest para que el fin de linea no altere su SHA-256. Las claves ACK de las pruebas de SIGER A se generan en memoria en cada ejecucion; ninguna privada se versiona.
