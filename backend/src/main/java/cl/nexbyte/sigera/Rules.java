package cl.nexbyte.sigera;

/** Reglas de cierre independientes de HTTP y de la persistencia. */
public final class Rules {
    private Rules() { }
    public static String closure(String level, boolean owner, boolean head, boolean ownSubstitution,
                                 String state, int pendingTasks, boolean pendingFlow) {
        Problem.require(!"CERRADO".equals(state), "El Caso ya esta cerrado.");
        Problem.require(pendingTasks == 0, "Hay tareas pendientes. Complétalas o cancélalas con fundamento antes de cerrar.");
        Problem.require(!pendingFlow, "Hay decisiones de continuidad pendientes.");
        Problem.require(!(owner && ownSubstitution), "El subrogante no confirma su propio Caso: requiere revision titular.");
        if (head && owner) return "JEFATURA_PROPIA";
        if (owner && "BAJA".equals(level)) return "PROFESIONAL_BAJA";
        if (head && "EN_REVISION".equals(state)) return "JEFATURA";
        throw new Problem(403, "El responsable solicita termino; la jefatura confirma, salvo BAJA o jefatura propia.");
    }
    /**
     * Solicitar termino es una facultad distinta de confirmar el cierre: la ejerce solo el responsable,
     * en cualquier nivel de complejidad (incluido el subrogante en su propio Caso), y la jefatura decide.
     */
    public static void closeRequest(boolean owner, String state, int pendingTasks, boolean pendingFlow) {
        if (!owner) Problem.denied();
        Problem.require(!"CERRADO".equals(state), "El Caso esta cerrado.");
        Problem.require(!"EN_REVISION".equals(state), "Ya esta en revision.");
        Problem.require(!pendingFlow, "Hay una derivacion pendiente.");
        Problem.require(pendingTasks == 0, "Completa o cancela las tareas pendientes antes de solicitar el cierre.");
    }
    public static void requirementClosable(int openCases, int pendingDecisions, boolean principalHead) {
        if (!principalHead) Problem.denied();
        Problem.require(openCases == 0, "Todavia existen Casos abiertos.");
        Problem.require(pendingDecisions == 0, "Hay decisiones pendientes de continuidad.");
    }
    public static void derivable(boolean open, int pendingTasks) {
        Problem.require(open, "El Caso de origen no esta en gestion.");
        Problem.require(pendingTasks == 0, "Resuelve las tareas pendientes antes de aceptar la derivacion.");
    }
}
