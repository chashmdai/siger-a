# SIGER A 0.4.0: rediseño de la experiencia visual

Fecha: 2026-11-08. Alcance: solo el frontend (`frontend/`). No cambian el backend, las reglas institucionales, los permisos, los contratos HTTP ni las migraciones.

## Objetivo y criterios

Darle a SIGER A una identidad propia que combine la seriedad institucional de la Defensoría de la Niñez con un diseño contemporáneo. Cada decisión responde a una necesidad del trabajo diario:

- **Encontrar rápido lo pendiente.** El inicio, las bandejas y las fichas muestran primero lo que requiere una decisión.
- **Ubicarse en el recorrido.** Siempre se ve en qué punto de la cadena COM → SOL → REQ → Caso está un registro.
- **Cuidar la primera atención y el registro de NNA.** Se exige lo mínimo, se separa la voz del NNA y las alertas de contacto seguro quedan visibles.
- **Accesibilidad y movilidad.** Teclado, contraste AA, movimiento reducido y una versión móvil real.

El manual de identidad (`docs/MANUAL_IDENTIDAD_DDN_SIGER_A.md`) se usó como referencia, no como plantilla.

## Identidad visual

| Elemento | Decisión | Justificación |
| --- | --- | --- |
| Escudo | Se usa el archivo original `brand/Emblema multicolor transparente con puntos.png`, reducido sin deformar ni recolorear: `frontend/src/assets/emblema-320.png` (320 px) y `emblema-favicon.png` (64 px, centrado). Siempre aparece sobre fondo blanco. | Así lo exige el manual (multicolor sobre blanco). El escudo nunca reemplaza un icono operativo. |
| Tipografía | Cabin (variable, pesos 400–700, subconjunto latino), servida desde el propio JAR en `frontend/src/assets/fonts/`. La licencia SIL OFL 1.1 está en `OFL-Cabin.txt`. Proviene del paquete npm `@fontsource-variable/cabin` 5.3.0, que no queda como dependencia. | Es la tipografía del manual. Al ir dentro del JAR no depende de Google Fonts ni de internet. |
| Color | El azul profundo `#146a8b` se reserva para la acción principal y los enlaces. La tinta y las superficies son grises fríos. Los seis colores del escudo aparecen solo como acentos. | Sobriedad institucional; un color de marca no se convierte en semántica operativa. |
| «Hilo» del emblema | Una cinta de seis segmentos con los colores del escudo, bajo la marca y en el ingreso. | Identidad reconocible sin teñir controles ni datos. |
| Tipo de registro | Insignias COM, SOL, REQ, Caso y Flujo, cada una con un tono propio y el texto siempre visible. | Ayuda a reconocer el tipo de registro de un vistazo; el color nunca reemplaza el texto. |
| Estados | Pastillas con punto y texto en español completo. Tonos: atención (ámbar), resuelto (verde), en curso (azul), cerrado (gris), rechazado (rojo). | El estado se entiende por el texto; el tono solo lo refuerza. |

Los tokens viven en `src/styles/tokens.css`. Los estilos se dividen en `base`, `layout`, `components` y `screens`; el antiguo `style.css` se eliminó.

## Pantallas

- **Ingreso.**
  - Tarjeta dividida: a la izquierda la identidad (escudo, «hilo» y tres principios del sistema), a la derecha el formulario.
  - El campo de clave permite mostrarla u ocultarla y avisa si las mayúsculas están activadas.
  - El cambio de clave temporal comprueba los requisitos mientras se escribe: 14 caracteres, máximo 72 bytes y coincidencia.
- **Navegación.**
  - Barra lateral agrupada por tipo de trabajo: Mi trabajo, Atención ciudadana, Gestión y Administración.
  - Las condiciones de visibilidad de cada entrada son las mismas de antes.
  - Mientras se ve una ficha, queda marcada la bandeja a la que pertenece.
  - Los avisos sin leer muestran un contador.
  - En móvil: barra superior con menú en cajón (`<dialog>` nativo), búsqueda y acceso a avisos.
- **Paleta «Buscar o ir a…» (Ctrl+K).**
  - Lleva a cualquier pantalla del menú o busca un folio o un texto en las bandejas.
  - Usa la ruta `#req?q=…`, que la bandeja aplica como búsqueda inicial.
  - Solo navega: la consulta la responde el servidor con el alcance de la sesión.
- **Inicio.**
  - Saludo y fecha en hora de Chile.
  - Accesos directos a «Nueva atención» e «Ingreso por clasificar» según la función.
  - Espacios de trabajo como selector segmentado.
  - Las cifras que piden acción y son mayores que cero se destacan; las que valen cero se atenúan.
  - Colas recientes en dos columnas.
  - Los textos del servidor se muestran con ortografía completa (`features/dashboard/texts.ts`); un texto no reconocido se muestra tal como llega.
- **Bandejas.**
  - Filtros visibles de un clic en lugar de una lista desplegable.
  - La búsqueda se aplica sola tras una pausa breve al escribir.
  - Toda la fila es clicable y el folio sigue siendo el control accesible por teclado.
  - Cada bandeja tiene una explicación breve, un conteo y un estado vacío que orienta (por ejemplo, «Limpiar búsqueda»).
  - En móvil cada fila se convierte en una tarjeta con etiquetas.
- **Fichas (expedientes).**
  - Encabezado común: tipo, folio, estado, asunto u objetivo, y la cadena COM → SOL → REQ → Caso con enlaces a los registros relacionados que la ficha ya trae.
    - El paso SOL se marca «No aplica: atención directa» según el origen real del REQ (`solicitation_id` nulo; en el Caso, `req_origin`), nunca por inferencia de la pantalla. Ver `docs/CORRECCIONES_FICHA_CASO_0_4_0.md`.
  - Acciones agrupadas («Gestión», «Jefatura», «Decisión»), con una sola acción primaria por grupo.
  - Columna lateral con los datos clave, las condiciones de cierre y el equipo.
  - Actuaciones, historia y entregas como línea de tiempo.
  - La historia muestra los comandos en lenguaje natural (`ACTION_LABELS`).
  - La carga de documentos queda plegada hasta que se necesita.
- **Primera atención.**
  - Tres pasos con navegación lateral (horizontal y fija en móvil).
  - Barra de guardado fija que indica si hay cambios sin guardar.
  - Modalidad como tarjetas: «Persona adulta o atención general» o «Atención directa a un NNA».
  - Canal como opciones visibles.
  - El motivo inicial se presenta como lo único necesario para comenzar.
  - La sección de escucha del NNA está diferenciada y recuerda sus garantías: no interpretar el silencio y respetar si no desea conversar.
  - En lectura:
    - el recuadro «Qué sigue» orienta según el estado de continuidad;
    - «No contactar», «Verificar antes de contactar» y la prioridad observada aparecen como alertas;
    - entregar y resolver se presentan lado a lado.
- **Ingreso por clasificar.**
  - Cinco secciones numeradas con índice lateral y barra de envío fija.
  - Un aviso remite a «Nueva atención» cuando se atiende directamente a una persona.
- **Diálogos de comando.**
  - Encabezado, cuerpo desplazable y pie fijo.
  - Los campos obligatorios llevan asterisco, con su leyenda.
  - Los textos largos muestran un contador.
  - Un campo dependiente indica «Elige primero el campo anterior».
  - En móvil el diálogo es una hoja inferior a pantalla completa.
- **Administración y subrogancias.** Mismos comandos, con encabezado, paneles, etiquetas legibles de funciones y estados vacíos.

## Movimiento

Las transiciones son breves: 140 a 380 ms, con desaceleración suave.

- Entrada de pantallas y pasos.
- Diálogos con animación de entrada y salida.
- Avisos emergentes con retiro automático, que se pausa con el puntero o el foco.
- Esqueletos de carga en el inicio, las bandejas y las fichas.
- Barra de progreso global solo si una consulta tarda más de 180 ms.
- Botones que se hunden al presionarlos.
- Indicador pulsante de cambios sin guardar.

Con `prefers-reduced-motion: reduce` se anulan las animaciones y transiciones. Solo se conserva un giro lento del indicador de «ocupado», porque es información esencial.

## Accesibilidad

- **Contraste.** El texto cumple al menos AA (4,5:1); la tinta secundaria llega a 4,7:1 sobre las superficies más oscuras.
  - Los bordes de campos usan `--field-line`, con 3,1:1 o más frente a blanco y gris claro.
  - Las insignias de tipo y los estados están entre 6,2:1 y 8,5:1.
- **Teclado.**
  - El foco es visible en todo control.
  - Hay un enlace «Saltar al contenido».
  - Al cambiar de pantalla, el foco pasa al contenido.
  - Los diálogos nativos atrapan el foco y se cierran con Esc.
  - La paleta funciona con flechas y Enter.
- **Semántica.**
  - `aria-current` en la navegación y en los pasos.
  - Radios nativos en las opciones visibles.
  - `role="alert"` en los errores y anuncio de los avisos emergentes.
  - Las insignias de tipo incluyen el nombre completo para lectores de pantalla.
- **Color.** Ningún estado depende solo del color: siempre hay texto.

## Cambios de comportamiento (solo presentación y navegación)

- **Textos visibles.** Los textos de la interfaz tienen tildes y eñes. Los valores enviados al servidor siguen siendo los mismos códigos (`PRESENCIAL`, `ORIENTACION`, etc.), ahora con etiquetas legibles. Los mensajes que redacta el servidor no se modificaron.
- **Ficha al recargar.** La ficha visible se conserva mientras se recarga la misma ruta, sin parpadeo.
- **Cambio de pantalla.** Cualquier cambio de pantalla cierra el diálogo de comando y el menú móvil abiertos.
- **Errores de versiones anteriores.** En la ficha de atención en lectura, un error al abrir una versión anterior ahora se muestra; antes quedaba oculto.
- **Aviso por cambios sin guardar.** Solo se activa con enlaces internos (`#…`), no con descargas.

## Verificación

Hecha en el entorno de laboratorio: una copia aislada de la base de pruebas (puerto 18610) y Chrome sin interfaz.

- **Compilación.** `npm run build` (tsc y vite) pasa sin errores.
- **Envíos reales.** Se repitieron los 5 envíos reales del arnés de equivalencia (nueva atención, COM por clasificar, complejidad, recepción de entrega y nueva tarea) sobre el mismo snapshot. Los cuerpos POST son **idénticos byte a byte** a la línea base de la consolidación, normalizando claves UUID y fechas ISO. Los folios resultantes también coinciden (REQ/33, COM/34).
- **Revisión visual.** Se revisaron en escritorio (1440×900) y móvil (390×844, 2×):
  - ingreso, inicio, bandejas, fichas REQ y Caso, nueva atención, ficha de atención con entrega pendiente;
  - diálogo de comando, paleta y menú móvil.
- **Accesibilidad y navegación.** Se comprobaron:
  - el foco visible;
  - el movimiento reducido (duración de animación efectiva de 1e-6 s);
  - la búsqueda inicial por ruta (`#cas?q=Linea` devuelve 2 filas);
  - la consola sin errores de la aplicación.

## Pendiente

- Probar con lectores de pantalla reales (NVDA o JAWS) y con usuarios de cada función.
- Revisar en Edge y Firefox y en tabletas. La revisión se hizo en Chrome 156.
- Probar la impresión de la ficha en una impresora o PDF real; la regla de impresión se adaptó al nuevo diálogo.
- Evaluar si los mensajes del servidor (sin tildes) deben pasar a ortografía completa. Es un cambio del backend, no se hizo.
- No hay modo oscuro: la regla del escudo exige superficies blancas y no se solicitó.
- `SOURCE_SHA256.txt` queda desactualizado.
