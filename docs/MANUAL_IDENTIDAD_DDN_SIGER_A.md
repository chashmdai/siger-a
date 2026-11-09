# Identidad visual de la Defensoría de la Niñez para SIGER A

**Uso:** referencia de diseño e implementación del frontend institucional de SIGER A.  
**Naturaleza:** guía técnica derivada del manual de marca y de una revisión previa del sitio web. **No es un manual oficial nuevo ni reemplaza al documento institucional.**  
**Sistema:** SIGER A, aplicación interna de gestión de Comunicaciones, Solicitudes, Requerimientos y Casos.

## 1. Fuentes y jerarquía de referencia

1. **`DEFENSORIA_MANUALde marca.pdf`** — *Manual de uso de marca* de la Defensoría de la Niñez, 13 páginas. Fuente principal para logotipo, área de seguridad, aplicaciones, paleta CMYK y tipografía.
2. **`SIGER_DDN_AnalisisVisual.md`** — análisis de estilos y recursos de una copia del sitio institucional, realizado el 21 de octubre de 2026. Evidencia de colores y tipografía observados en la web, no norma oficial.
3. **`SIGER_DDN_tokens.css`** — adaptación técnica previa para interfaz web pública. Sus variables de SIGER son propuestas de implementación, no valores normativos de la marca.

**Regla de precedencia:** si una decisión de diseño entra en conflicto con el manual oficial, prevalece el manual. Si faltan especificaciones para aplicaciones internas (tablas, estados, formularios, paneles), diseñar de forma coherente, accesible y sobria, sin atribuir esas decisiones al manual.

## 2. Logotipo institucional

El logotipo combina un símbolo multicolor con el nombre de la Defensoría de la Niñez. El manual contempla:

- **Aplicación horizontal y vertical:** página 2.
- **Área de seguridad alrededor del logotipo:** página 3; se establece mediante un módulo `X` ilustrado en el manual. Respetar la proporción del recurso original y el espacio libre circundante.
- **Versiones monocromáticas:** página 4, para contextos que las requieran.
- **Aplicación multicolor sobre blanco:** páginas 5 y 6, incluso cuando el soporte exterior tenga color.
- **Aplicaciones editoriales e institucionales adicionales:** páginas 7 y 10 a 12.

### Aplicación en SIGER A

- Usar un **archivo original autorizado**, preferentemente SVG; no reconstruir el símbolo con CSS, iconos genéricos ni imágenes generadas.
- Preferir la versión **horizontal en el encabezado** de escritorio. Una versión vertical solo si el espacio y la composición lo justifican.
- Conservar proporciones, colores y margen de seguridad; no deformar, recolorear ni superponer texto o controles.
- Mantener el logotipo multicolor sobre **superficie blanca**. En una barra oscura o de color, reservarle un espacio blanco apropiado o usar una versión oficial permitida.
- No agregar la marca comercial de Nexbyte al encabezado de SIGER A: es una interfaz institucional de la Defensoría.
- No usar el símbolo de la Defensoría como sustituto de iconos operativos como guardar, cerrar, derivar o buscar.

## 3. Paleta cromática institucional

La página 8 del manual define **ocho colores mediante CMYK**. Se conservan aquí sus valores tal como aparecen escritos en esa página:

| Color visual | C | M | Y | K |
| --- | ---: | ---: | ---: | ---: |
| Celeste | 70 | 15 | 5 | 0 |
| Naranja | 0 | 80 | 90 | 0 |
| Verde | 50 | 0 | 100 | 0 |
| Amarillo | 0 | 5 | 90 | 0 |
| Durazno / amarillo anaranjado | 0 | 5 | 90 | 0 |
| Rosado | 5 | 85 | 25 | 0 |
| Ciruela / magenta oscuro | 35 | 100 | 35 | 10 |
| Gris | 0 | 0 | 0 | 80 |

**Precisión:** la transcripción del manual muestra los mismos valores CMYK para los colores cuarto y quinto, aunque sus muestras visuales son diferentes. No corregir o inventar una equivalencia exacta sin un archivo de marca autorizado.

### Colores digitales observados en la web institucional

Son referencias **aproximadas para pantalla**, no conversiones oficiales ni sustitutos del CMYK del manual:

| Token sugerido | HEX | Procedencia / uso sugerido |
| --- | --- | --- |
| `--ddn-celeste` | `#34AAD7` | Botón de acceso de personas adultas, sitio observado |
| `--ddn-verde` | `#8EC044` | Botón de acceso NNA, sitio observado |
| `--ddn-azul-profundo` | `#146A8B` | Azul de controles y secciones observado en la web |
| `--ddn-rosado` | `#E44D7F` | Destacados del sitio observado |
| `--ddn-naranja` | `#F15A31` | Color del sitio observado |
| `--ddn-gris-texto` | `#504F4F` | Texto del sitio observado |
| `--ddn-magenta-logo` | `#9E1F63` | Aproximación cromática del logotipo |
| `--ddn-gris-logo` | `#58595B` | Aproximación cromática del logotipo |

El sitio público utiliza celeste para acceso de adultos y verde para acceso NNA. **Esto no obliga a usar tales colores para todos los roles, estados o expedientes dentro de SIGER A.** No convertir una convención del sitio público en una regla funcional institucional.

## 4. Tipografía

El manual presenta **Cabin Regular** y **Cabin Bold** en la página 9, con ejemplos de titulares en la página 10.

Para SIGER A:

- Usar **Cabin** en la interfaz si contamos con una fuente obtenida y distribuida de forma autorizada.
- Utilizar pesos regulares para contenido y destacados/semibold o bold para jerarquía, evitando abusar de mayúsculas.
- Si no se dispone del recurso de fuente, utilizar una pila de respaldo legible, por ejemplo `system-ui`, `Segoe UI`, `Arial`, `sans-serif`.
- No depender de Google Fonts en tiempo de ejecución: el entorno institucional puede tener restricciones de conectividad y privacidad.
- Mantener tamaños cómodos para tablas, expedientes, formularios y personas que trabajan durante varias horas con la aplicación.

## 5. Adaptación de marca a una aplicación de gestión

**Estas son decisiones propuestas para SIGER A, no mandatos extraídos del manual.**

### Principios

1. **Institucional antes que decorativo.** Fondo neutro, superficies claras, tipografía legible, buena jerarquía y color deliberado.
2. **La información manda.** Las bandejas deben mostrar qué está pendiente, qué decisión corresponde, quién es responsable y qué antecedentes existen.
3. **Diseño específico por función.** Un distribuidor, una coordinación, un profesional y un administrativo comparten identidad visual, pero no necesitan la misma pantalla de trabajo.
4. **No infantilizar la atención NNA.** Evitar emojis, caricaturas y recursos lúdicos en expedientes internos. La experiencia debe ser respetuosa, comprensible y profesional.
5. **Accesibilidad.** Navegación por teclado, foco visible, etiquetas asociadas, mensajes de error próximos al campo, tamaños de interacción adecuados y contraste verificable.
6. **Responsive real.** Adaptar tablas, paneles, navegación y formularios a pantallas pequeñas sin esconder acciones esenciales.
7. **No usar solo el color para comunicar estados.** Acompañarlo de texto, iconos accesibles y explicaciones cuando corresponda.

### Distribución sugerida

- **Encabezado:** logotipo oficial en blanco, nombre o contexto de SIGER A, identidad de sesión y acciones globales.
- **Navegación:** módulos operativos claros y visibles según los permisos efectivos, sin fingir cambio de rol que conceda accesos.
- **Inicio:** pendientes y acciones del funcionario, en lugar de estadísticas decorativas.
- **Bandejas:** filtros, búsqueda, folios, estados, plazos y responsables, con paginación y estados vacíos útiles.
- **Expediente:** resumen de REQ y sus COM, SOL, Casos, actuaciones, documentos, decisiones e historial sin perder contexto.
- **Formularios:** información progresiva y no redundante; indicación precisa de campos obligatorios, validaciones y guardado.

### Colores de interfaz y accesibilidad

- Reservar los colores más brillantes como acentos o indicadores, no como fondos indiscriminados para grandes bloques de texto.
- El celeste `#34AAD7` es demasiado claro para confiar en texto blanco pequeño sin comprobar contraste. Para botones primarios con texto blanco es preferible una tonalidad más profunda como `#146A8B`, siempre verificada con herramientas de contraste.
- Usar grises y fondos claros para superficies de trabajo. Reservar los colores de alerta/éxito para su **semántica operativa**, sin asumir que todo color de la marca tiene significado funcional.
- Como criterio de diseño propuesto, comprobar **WCAG 2.2 AA**: contraste de texto normal al menos **4.5:1** y de componentes/indicadores visuales pertinentes al menos **3:1**, con las excepciones previstas por el estándar.

## 6. Tokens de referencia para implementación

Las variables siguientes son una **base orientativa** derivada del sitio institucional y de decisiones UI anteriores. Pueden modificarse por accesibilidad y necesidades del producto sin alterar los recursos oficiales de marca.

```css
:root {
  --ddn-celeste: #34aad7;
  --ddn-verde: #8ec044;
  --ddn-naranja: #f15a31;
  --ddn-rosado: #e44d7f;
  --ddn-azul-profundo: #146a8b;
  --ddn-magenta-logo: #9e1f63;
  --ddn-gris-logo: #58595b;

  --siger-font: 'Cabin', system-ui, 'Segoe UI', Arial, sans-serif;
  --siger-bg: #f5f9fb;
  --siger-surface: #ffffff;
  --siger-border: #d9e5eb;
  --siger-text: #35444c;
  --siger-muted: #61717b;
  --siger-primary: #146a8b;
  --siger-accent: #34aad7;
  --siger-focus: #146a8b;
  --siger-radius-card: 16px;
  --siger-radius-control: 10px;
}
```

Evitar importar estilos del WordPress institucional o depender de recursos remotos. Implementar los componentes en React/TypeScript existente y mantener los estilos coherentes en un sistema pequeño de tokens reutilizables.

## 7. Límites y criterios de aceptación en SIGER A

- El manual de marca **no define** permisos, reglas de negocio, destinos de Solicitudes ni estados del sistema. No inferirlos a partir de colores o pantallas.
- Rediseñar el frontend sin modificar silenciosamente contratos HTTP, validaciones del backend, auditoría, concurrencia o trazabilidad.
- Cada acción debe aparecer según permisos efectivos del servidor; esconder un botón no reemplaza la autorización de backend.
- No introducir una biblioteca grande de componentes solo por estilo si la solución existente puede evolucionar razonablemente.
- Verificar el resultado en escritorio y móvil, con teclado, lectura y estados de error, vacío y carga; comprobar también los recorridos institucionales principales.
- Documentar los archivos visuales incorporados y su procedencia. Los logos originales y las fuentes tienen su propia identidad y condiciones de uso; no reemplazarlos por recreaciones.

## 8. Tareas recomendadas para Claude Code

Esta guía debe servir como **referencia de marca**, no como instrucción automática de rediseño.

**Primera etapa:** reorganizar el frontend con el mismo criterio de la consolidación del backend: responsabilidades comprensibles, menor duplicación, cambios incrementales, pruebas y conservación de comportamiento. 

**Segunda etapa, cuando se solicite expresamente:** aplicar esta guía para diseñar una experiencia de usuario institucional, responsiva y accesible, empezando por los flujos más usados. Mantener separadas las mejoras visuales y los cambios funcionales para poder detectar regresiones.

---

### Referencia rápida al PDF original

- Páginas 1-2: identidad y versiones de logotipo.
- Página 3: área de seguridad.
- Páginas 4-7: aplicaciones cromáticas y monocromáticas.
- Página 8: ocho muestras CMYK.
- Páginas 9-10: Cabin Regular/Bold y uso en titulares.
- Páginas 11-12: piezas y ejemplos de aplicación.

**Advertencia:** los HEX, tokens de aplicación y criterios UX de esta guía son adaptaciones de trabajo. Solo el PDF institucional y los activos oficiales autorizados son fuente de reglas de marca.
