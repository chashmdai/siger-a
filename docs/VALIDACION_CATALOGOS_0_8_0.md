# SIGER 0.8.0 - respuestas de validación de catálogos (DI-01 a DI-04)

Respuesta del responsable del 2026-11-24, con conformidad institucional de la Dirección de UEP del 2026-11-25. Es la fuente con que E06 prepara los archivos de referencia de `deploy/config-ejemplo/catalogos/`, que quedan en estado PROPUESTA. TI los activa en la actualización de producción solo con conformidad definitiva.

Reglas de transcripción para E06:
- Los rótulos y las definiciones se copian textuales de este archivo.
- Los códigos los asigna SIGER, son estables y no se muestran al usuario. Los que ya existen en los archivos de E02 se conservan.
- Las definiciones van en el campo opcional `descripcion` de cada ítem.
- Los valores especiales (Pendiente, Sin información, No declarado, No aplica) no forman parte de ningún catálogo.

## DI-01. Tipo de solicitud, tipo de requirente y vía de ingreso

### Tipo de solicitud (5 valores, aprobado)

| Rótulo | Decisión | Definición |
| --- | --- | --- |
| Vulneración de derechos | Aprobar | Incluye presuntas vulneraciones, sin exigir acreditación previa. |
| Consulta u orientación | Aprobar | Incluye solicitudes de información, asesoría u orientación. |
| Promoción | Aprobar | Incluye actividades de promoción y difusión de derechos. |
| Reclamo sobre otra institución | Aprobar | Reclamos que, según los antecedentes disponibles, no constituyen una posible vulneración de derechos. |
| Otro | Aprobar | Categoría residual, distinta de Sin información. |

Criterio entre reclamo y vulneración: si existen antecedentes que permitan identificar una posible afectación de los derechos de un NNA, se clasifica como vulneración de derechos, aunque originalmente se haya presentado como reclamo.

### Tipo de requirente (4 valores, aprobado)

| Rótulo | Decisión | Definición |
| --- | --- | --- |
| Persona natural adulta | Aprobar | Independiente de su relación con el NNA. |
| Niño, niña o adolescente | Aprobar | Cuando el propio NNA formula el requerimiento. |
| Persona jurídica u organismo | Aprobar | Incluye instituciones públicas, privadas y organizaciones. |
| Actuación de oficio | Aprobar | Categoría estadística especial para requerimientos iniciados institucionalmente, sin solicitante externo. |

### Vía de ingreso (9 valores y 6 agrupaciones, aprobado)

Las agrupaciones son ítems padre no seleccionables; las vías son los ítems seleccionables. Se conserva siempre la vía original.

| Vía | Agrupación | Decisión | Definición |
| --- | --- | --- | --- |
| Formulario Público adulto | Digital | Aprobar | |
| Formulario Público NNA | Digital | Aprobar | |
| Correo | Digital | Aprobar | |
| Redes sociales | Digital | Aprobar | |
| Presencial | Presencial | Aprobar | |
| Teléfono | Telefónica | Aprobar | |
| Oficio | Documental | Aprobar | |
| Integrante de la Defensoría | Interna | Aprobar | Categoría de ingreso interno, solo cuando representa el origen efectivo de la comunicación. No se usa por el solo hecho de que un funcionario registre un requerimiento recibido por correo, teléfono u otra vía. |
| Otro | Otra | Aprobar | |

Comparabilidad histórica: se debe establecer una correspondencia entre estas categorías y las usadas en los informes anuales anteriores. Se hace en el asistente de series históricas (E11 y E13), sin combinar series automáticamente.

## DI-02. Contexto, caracterización de la vulneración, tramos de edad, rol del Estado y grupos prioritarios

Propuesta del responsable, pendiente de validación de la Dirección de UEP, incluidas sus definiciones operativas. Fuentes de referencia: Informe Anual 2026, registros históricos institucionales y Modelo de conceptualización y categorización de derechos de la niñez y adolescencia, versión 2.0 (2022).

### Contexto (7 categorías y 5 subcategorías)

Selección múltiple. Un requerimiento puede corresponder a varios contextos sin multiplicar su conteo general.

| Rótulo | Depende de | Definición |
| --- | --- | --- |
| Familiar | | Entorno familiar o de cuidado doméstico. |
| Escolar | | Entorno educativo y convivencia escolar. |
| Institucional | | Atención o intervención de instituciones. |
| Salud | Institucional | Prestaciones y establecimientos sanitarios. |
| Protección especializada | Institucional | Residencias, familias de acogida y programas especializados. |
| Justicia juvenil | Institucional | Sistema de responsabilidad penal adolescente. |
| Judicial | Institucional | Procedimientos e intervenciones judiciales. |
| Policial | Institucional | Intervenciones de instituciones policiales. |
| Comunitario | | Barrio, comunidad y espacios públicos. |
| Digital | | Internet, redes sociales y plataformas digitales. |
| Laboral | | Actividades laborales y relaciones de trabajo. |
| Otro contexto | | Entornos no comprendidos en las categorías anteriores. |

### Caracterización de la vulneración (10 categorías y 9 subcategorías)

Selección múltiple de categorías y subcategorías. Elegir una subcategoría implica su categoría superior, sin duplicar el conteo. Se debe revisar la delimitación conceptual de las categorías para que la clasificación sea consistente.

| Rótulo | Depende de | Definición |
| --- | --- | --- |
| Violencia | | Situaciones de violencia contra NNA. |
| Violencia física | Violencia | Agresiones físicas. |
| Violencia psicológica | Violencia | Maltrato emocional o psicológico. |
| Violencia sexual | Violencia | Agresiones y vulneraciones de carácter sexual. |
| Violencia entre pares | Violencia | Agresiones entre NNA. |
| Violencia digital | Violencia | Agresiones mediante medios digitales. |
| Negligencia o descuido | | Incumplimiento de deberes de cuidado. |
| Discriminación | | Trato discriminatorio o exclusión. |
| Explotación | | Aprovechamiento abusivo de NNA. |
| Explotación sexual | Explotación | Situaciones de explotación de carácter sexual. |
| Explotación laboral | Explotación | Situaciones de explotación mediante el trabajo. |
| Barreras de acceso | | Obstáculos para acceder a prestaciones o servicios. |
| Incumplimiento de obligaciones familiares | | Incumplimiento de deberes familiares. |
| Pensión de alimentos | Incumplimiento de obligaciones familiares | Incumplimiento de obligaciones alimentarias. |
| Relación directa y regular | Incumplimiento de obligaciones familiares | Incumplimientos que afectan el régimen de relación directa y regular. |
| Omisión institucional | | Falta de intervención o respuesta pertinente. |
| Trato degradante | | Trato contrario a la dignidad del NNA. |
| Amenazas | | Situaciones de intimidación o amenaza. |
| Otra vulneración | | Situaciones no comprendidas en las categorías anteriores. |

### Tramos de edad (8 tramos)

Cubren de 0 a 130 años sin huecos ni solapes. Se conserva la edad exacta cuando está disponible; los tramos son una agrupación estadística versionada. Deben coincidir con los tramos de uso institucional (Informe Anual) para asegurar la comparabilidad histórica; el responsable lo confirma antes de la activación.

| Desde (años) | Hasta (años) | Rótulo |
| --- | --- | --- |
| 0 | 5 | Primera infancia |
| 6 | 9 | Niñez |
| 10 | 13 | Adolescencia temprana |
| 14 | 17 | Adolescencia |
| 18 | 29 | 18 a 29 años |
| 30 | 44 | 30 a 44 años |
| 45 | 59 | 45 a 59 años |
| 60 | 130 | 60 años o más |

### Rol del Estado (eje VII del modelo 2.0)

Se usa como caracterización transversal, no como un derecho adicional: representa una dimensión de análisis distinta de los derechos afectados. Se conservan sus 5 subcategorías según el modelo. Fuente: la transcripción del eje VII que ya contiene `derechos-2022-v2.json` (E02), que pasa a un catálogo propio; el eje VII sale del catálogo de derechos.

### Grupos prioritarios (sección VIII del modelo 2.0)

Bloque independiente de caracterización de la población afectada, sin confundir la pertenencia a un grupo con una vulneración de derechos: pertenecer a un grupo prioritario no constituye por sí mismo una vulneración. Selección múltiple, basada exclusivamente en antecedentes pertinentes, respetando la reserva y sin inferir características personales.

Grupos (11). E06 verifica cada denominación textual contra la sección VIII del PDF y registra cualquier diferencia en `LEEME.txt`:
1. NNA bajo cuidado alternativo o privación de libertad estatal
2. NNA sujetos a medidas administrativas o judiciales
3. NNA con discapacidad
4. NNA pertenecientes a pueblos originarios o tribales
5. NNA migrantes
6. NNA en situación de calle
7. Niñas y adolescentes mujeres
8. NNA en situación de hospitalización
9. NNA con necesidades especiales de atención de salud
10. NNA diversos en su sexualidad
11. NNA de zonas aisladas e insulares

## DI-03. Tipo de actuación, tipo de institución y relación con el NNA

### Tipo de actuación (14 valores, aprobado con cambios)

Una actuación tiene un solo tipo y se clasifica por su propósito. Solo cuentan las actuaciones efectivamente realizadas; una tarea pendiente es un compromiso y no cuenta como actuación.

| Rótulo | Decisión | Definición |
| --- | --- | --- |
| Orientación | Aprobar | |
| Respuesta directa | Aprobar | |
| Derivación externa | Aprobar | |
| Oficio | Aprobar | Oficio cuyo propósito no corresponde a otro tipo de actuación. |
| Denuncia administrativa | Aprobar | |
| Denuncia penal | Aprobar | |
| Solicitud de medida de protección | Cambiar (antes "Medida de protección") | Distingue la actuación de la Defensoría de una medida decretada por un tribunal. |
| Querella | Aprobar | |
| Acción constitucional | Aprobar | |
| Amicus curiae | Aprobar | |
| Otra actuación | Aprobar | |
| Solicitud de antecedentes | Agregar | Una solicitud de antecedentes realizada mediante oficio no se cuenta como dos actuaciones por el solo hecho de usar ese medio. |
| Coordinación interinstitucional | Agregar | |
| Seguimiento de requerimiento | Agregar | |

La respuesta directa y la orientación de la primera atención forman parte del universo de actuaciones institucionales, con su origen diferenciado: primera atención, gestión de requerimiento o gestión de caso. El análisis puede ser consolidado o por origen, sin contar dos veces una misma actuación.

### Tipo de institución (5 valores, aprobado)

Se mantienen íntegros los valores y códigos del Formulario Público.

| Rótulo | Decisión |
| --- | --- |
| Establecimiento educativo | Aprobar |
| Organismo público | Aprobar |
| Empresa o entidad privada | Aprobar |
| Fundación u ONG | Aprobar |
| Otra institución | Aprobar |

### Relación con el NNA (11 valores, aprobado con cambios)

Se mantienen intactos los códigos y valores originales de la integración con el Formulario Público. Los valores agregados son exclusivos de SIGER mientras no formen parte del Formulario.

| Rótulo | Decisión |
| --- | --- |
| Madre | Aprobar |
| Padre | Aprobar |
| Cuidador o cuidadora | Aprobar |
| Otro familiar | Aprobar |
| Docente | Aprobar |
| Salud | Aprobar |
| Testigo | Aprobar |
| Organización | Aprobar |
| Otro | Aprobar |
| El propio NNA | Agregar (exclusivo de SIGER) |
| Representante legal | Agregar (exclusivo de SIGER) |

Los rótulos de los 9 valores del Formulario son los de `relacion-v1.json` (E02), que prevalecen sobre las abreviaturas de esta tabla.

## DI-04. Modelo de derechos 2.0 (2022)

Se propone usar la versión 2.0 de 2022, porque es el modelo publicado por la Defensoría de la Niñez y es consistente con el Informe Anual 2026. UEP debe ratificar su vigencia. Se aprueban los 4 ejes, los 17 derechos y sus 60 subcategorías, sujetos a esa ratificación y a la verificación de la transcripción íntegra.

| Eje | Código | Derecho | Subcategorías | Decisión |
| --- | --- | --- | --- | --- |
| III Vida y supervivencia | 3.1 | Salud | 5 | Aprobar |
| III Vida y supervivencia | 3.2 | Vivienda adecuada | 3 | Aprobar |
| III Vida y supervivencia | 3.3 | Medioambiente sano | 2 | Aprobar |
| III Vida y supervivencia | 3.4 | Nivel de vida adecuado | 4 | Aprobar |
| IV Desarrollo | 4.1 | Desarrollo integral | 3 | Aprobar |
| IV Desarrollo | 4.2 | Descanso, esparcimiento, cultura y arte | 3 | Aprobar |
| IV Desarrollo | 4.3 | Educación | 2 | Aprobar |
| IV Desarrollo | 4.4 | Uso y disfrute del entorno público | 2 | Aprobar |
| V Protección | 5.1 | Vivir en familia | 3 | Aprobar |
| V Protección | 5.2 | Protección contra toda forma de violencia | 9 | Aprobar |
| V Protección | 5.3 | Trato justo | 3 | Aprobar |
| V Protección | 5.4 | No discriminación | 8 | Aprobar |
| V Protección | 5.5 | Identidad, nombre y nacionalidad | 2 | Aprobar |
| VI Participación | 6.1 | Libertad de pensamiento, conciencia y religión | 3 | Aprobar |
| VI Participación | 6.2 | Información y libertad de expresión | 3 | Aprobar |
| VI Participación | 6.3 | Libertad de asociación | 2 | Aprobar |
| VI Participación | 6.4 | Ser escuchados | 3 | Aprobar |

Los rótulos de esta tabla están abreviados; los textuales son los de `derechos-2022-v2.json` (E02).

Reglas:
- Clasificación por derecho y por subcategoría, con subcategoría opcional: se puede identificar un derecho sin antecedentes suficientes para precisar su subcategoría.
- Elegir una subcategoría implica estadísticamente su derecho superior, sin duplicar el conteo.
- Selección múltiple de derechos y subcategorías.
- Eje VII y sección VIII: según DI-02.
- Las dimensiones de seguimiento del modelo no se transcriben.
- Rótulos: los de la Tabla 2 del modelo son la denominación canónica; las secciones específicas sirven de referencia conceptual. Las 11 diferencias de rótulo se revisan individualmente contra `LEEME.txt`, y cualquier modificación se documenta y se valida institucionalmente.

## Conformidad propuesta

| Catálogo | Resultado propuesto |
| --- | --- |
| Tipo de solicitud | Aprobado |
| Tipo de requirente | Aprobado |
| Vía de ingreso | Aprobado |
| Contexto | Pendiente de validación institucional de la propuesta |
| Caracterización de la vulneración | Pendiente de validación institucional de la propuesta |
| Tramos de edad | Pendiente de validación institucional de la propuesta |
| Rol del Estado | Propuesto como caracterización transversal |
| Grupos prioritarios | Propuesto como caracterización de la población afectada |
| Tipo de actuación | Aprobado con cambios |
| Tipo de institución | Aprobado |
| Relación con el NNA | Aprobado con cambios |
| Derechos (modelo 2.0) | Aprobado con precisiones |

Nombre, cargo, fecha y conformidad definitiva: pendientes de la autoridad institucional competente.
