# Evidencia y limites de verificacion - 0.3.1

## Que se ejecuto aqui

| Comprobacion | Resultado | Limite |
|---|---|---|
| Sintaxis Java | 20 archivos sin errores de parseo con javac 21. | No resuelve dependencias Spring/Jackson/JDBC. |
| Tipos Java auxiliares | Clases modificadas y fuentes de prueba aceptadas por javac 21 contra firmas temporales de API. | No son dependencias reales ni ejecucion de Spring. Las firmas auxiliares no se entregan en el proyecto. |
| Sintaxis TypeScript/TSX | 11 fuentes transpiladas sin errores sintacticos con el TypeScript del entorno. | No es tsc/Vite con React19/TS6 reales. |
| Tipos TypeScript auxiliares | Sin errores en chequeo interno con declaraciones React temporales. | Tipos JSX simplificados, inferencia de parametros de callbacks no comprobada completamente; tsconfig del proyecto sigue estricto e intacto. |
| Politica pura | 8 comprobaciones reales correctas de Rules/Problem con Java21. | No incluye persistencia/API ni transacciones. |
| Consultas de bandejas | 235 ejecuciones auxiliares de SQL generado por BoardQueries, sin errores, con resultados contrastados por rol. | Modelo SQLite adaptado; no prueba de sintaxis completa MySQL/H2, Flyway, concurrencia ni rendimiento real. |
| Dashboard visual | 3 vistas de rol/resumen de escritorio y 1 movil revisadas en Chromium; cuatro tarjetas, sin desbordamiento horizontal global. | HTML derivado del componente TSX con hooks/datos auxiliares. No es React real conectado ni prueba E2E. |
| Estructura | JSON/XML validos; V1/V2/V3 byte a byte iguales a la 0.3.0. | V4 necesita aplicarse realmente sobre H2/MySQL. |

La comprobacion SQL usa identidades sinteticas: distribucion, Direccion UPR, Coordinadora Requerimientos, profesional, TI, sede, lector por ambito, autoridad y suplente Direccion UPD. Se contrastaron permisos negativos de TI/autoridad y la perdida de acceso por un vinculo devuelto. Son comprobaciones auxiliares, no 235 pruebas unitarias incorporadas al producto.

## No ejecutado aqui

El entorno solo dispone de Java21/Node22 y no pudo resolver/descargar Maven/npm. No se ejecuto `Build.ps1`, Maven verify, las nueve pruebas JUnit con Spring, React19/Vite8 real, H2/MySQL nativos, PowerShell5.1, Windows, la VM o una integracion externa. No se produjo un JAR acreditado ni se afirma que los scripts de restauracion hayan corrido en el servidor.

No atribuir a esta fuente los builds del SIGER anterior o la validacion local de 0.2.0. El primer paso sigue siendo compilar en el PC con Java25/Node24 y su lockfile, sin omitir pruebas.

## Pruebas incluidas para ese build

- `RulesTest`: cuatro metodos de politicas.
- `WorkflowTest`: un recorrido transaccional previo con H2.
- `Version031Test`: cuatro recorridos focales (subrogancia Direccion/cartera propia; permiso revocado tras reinicio; primera atencion con adjunto y retorno de antecedente; documentos asociados/versionados y bandejas por permiso).

Total: **nueve metodos JUnit**, no una bateria masiva. No acceden a Graph, SMR ni entornos reales. Su fuente fue revisada, pero su resultado con las dependencias reales esta pendiente del build del operador.

## Siguiente evidencia necesaria

1. Build completo sin `-SkipChecks`; mantener log y manifiesto del JAR.
2. Arranque H2 local existente con V4, sin borrar `var/`; ocho recorridos de `PRUEBAS_MANUALES_0_3_1.md`.
3. En ventana autorizada, MySQL/Java propios en VM, reinicio, respaldo, restore-check nuevo y arranque comprobado.
4. Corregir el primer fallo observado. No introducir datos reales antes de completar pendientes de documentos/seguridad/operacion e integracion.

`VERIFICACION_LOCAL.json` registra estas comprobaciones y sus limites. Las herramientas auxiliares, mocks y HTML de pruebas quedan fuera de la distribucion de fuente.
