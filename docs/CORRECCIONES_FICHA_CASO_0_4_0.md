# Correcciones: ficha del Caso (0.4.0)

Fecha: 2026-11-09. Origen: pruebas manuales.

## 1. La cadena mostraba una SOL inexistente

**Síntoma.** En la ficha de un Caso cuyo REQ nació de una primera atención directa, la cadena COM → SOL → REQ → Caso mostraba el paso «Solicitud» como si existiera.

**Causa.**
- La ficha del Caso no recibía el origen del REQ, y la cadena dibujaba siempre los cuatro pasos.
- En la ficha del REQ, el paso se omitía solo si había una ficha de primera atención (`attention_record`). Por eso tampoco era correcto para la atención resuelta en el mismo acto (`resolve_now`), que crea el REQ sin SOL y sin ficha.
- En la ficha de la COM no se distinguía «sin SOL todavía» de «REQ sin SOL».

**Dato real usado.** `requirement.solicitation_id`. Un REQ solo nace sin SOL por primera atención directa (`Attention.create` o atención resuelta en el mismo acto). Cuando la jefatura asume una COM, se crea una SOL `ASUMIDA`, así que ese REQ sí tiene Solicitud.

**Corrección.**
- **Backend** (`Queries.caseSummary`): agrega el campo `req_origin`, con valor `ATENCION_DIRECTA` o `SOLICITUD`, calculado desde `solicitation_id`. El campo es aditivo y no expone el identificador de la SOL a quien solo lee el Caso.
- **Frontend** (`features/detail/parts.tsx`): el paso SOL se marca «No aplica: atención directa» cuando corresponde:
  - en el Caso, si `req_origin` es `ATENCION_DIRECTA`;
  - en el REQ, si `solicitation_id` es nulo;
  - en la COM, si el REQ vinculado no tiene SOL.

## 2. «Solicitar cierre» usaba la facultad de confirmar

**Síntoma.** El botón del responsable se habilitaba con `can_close`, que el servidor calcula con `Rules.closure`, la regla para **confirmar** el cierre.
- Para un profesional sin jefatura en un Caso MEDIANA, ALTA o sin clasificar, `can_close` es siempre falso. Así, no podía solicitar el término, aunque el comando `CASE_REQUEST_CLOSE` lo admite.
- Lo mismo ocurría con el subrogante en su propio Caso.
- El panel lateral además mostraba «Antes de cerrar» con el bloqueo de la confirmación.

**Corrección: la regla actual, sin cambios, se hace explícita y se usa en ambos lados.**
- **`Rules.closeRequest(owner, state, pendingTasks, pendingFlow)`** contiene las mismas condiciones que ya aplicaba `CASE_REQUEST_CLOSE`:
  - solo el responsable;
  - el Caso no está cerrado ni en revisión;
  - no hay derivación pendiente;
  - no hay tareas pendientes.

  Los mensajes son los mismos. No depende de la complejidad ni de la subrogancia.
- **`CaseCommands`**: `CASE_REQUEST_CLOSE` llama a esa regla en lugar de repetir las condiciones. El comportamiento es idéntico.
- **`Queries.caseSummary`** agrega `can_request_close` y `close_request_blockers`, que también consideran el REQ cerrado, como el comando. `can_close` y `closure_blockers` no cambian.
- **Frontend**:
  - «Solicitar cierre» usa `can_request_close`, y su texto de ayuda muestra el motivo del bloqueo.
  - «Confirmar cierre» sigue con `can_close`.
  - `ClosureStatus` muestra «Puedes solicitar el término» o «Antes de solicitar el término» al responsable que no puede confirmar. A quien confirma le sigue mostrando las condiciones de cierre.

No cambian las reglas institucionales:
- el profesional solo confirma Casos BAJA;
- la jefatura confirma en revisión, o su propio Caso;
- el subrogante no autoconfirma;
- las tareas pendientes bloquean tanto la solicitud como el cierre.

No hay migraciones ni se modifican datos existentes.

## Pruebas agregadas

- **`RulesTest.solicitarTerminoEsFacultadDistintaDeConfirmar`**
  - El responsable de un Caso MEDIANA, ALTA o sin clasificar no confirma, pero sí solicita.
  - Se admite solicitar desde «En espera».
  - Quien no es responsable recibe 403.
  - Se rechaza si el Caso está en revisión o cerrado, o si hay tareas o derivaciones pendientes.
- **`CaseDetailFacultiesTest`** (integrado, H2 en memoria):
  - **`cadenaDelCasoUsaElOrigenRealDelReq`**: un Caso de un REQ de atención directa informa `ATENCION_DIRECTA`; uno de una COM asumida, `SOLICITUD`.
  - **`responsableSolicitaTerminoAunqueNoPuedaConfirmarlo`**:
    - Sin clasificar y con ALTA: `can_close=false` y `can_request_close=true`.
    - Con una tarea pendiente: la ficha y el comando rechazan por igual. Al cancelarla, se puede solicitar.
    - La jefatura no puede solicitar el término de un Caso ajeno.
    - Tras la solicitud: el Caso queda en revisión, el responsable ya no puede volver a solicitar y la jefatura puede confirmar.

## Resultados

- **Suite completa:** 19 pruebas, 0 fallas (16 anteriores y 3 nuevas), con `tsc` limpio. Se compiló en una copia del proyecto.
- **Instancia de laboratorio aislada** (puerto 18630, base nueva en el directorio temporal de la sesión):
  - Recorrido real por API y en Chrome como profesional.
  - **Caso de atención directa, MEDIANA:**
    - la cadena muestra «Solicitud · No aplica: atención directa», como paso omitido;
    - «Solicitar cierre» está habilitado y «Confirmar cierre» no aparece;
    - el panel dice «Puedes solicitar el término».
  - **Caso de COM asumida:** la Solicitud aparece como paso real.
  - **Solicitud desde el diálogo de la ficha:** el Caso pasa a «En revisión de cierre».
  - Revisado en escritorio y en móvil (390 px), sin excepciones de JavaScript.
- No se usaron ni modificaron la base local `var\local-db` ni la instancia del usuario. Los procesos del laboratorio se detuvieron.
