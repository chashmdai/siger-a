package cl.nexbyte.sigera;

public final class Problem extends RuntimeException {
    public final int status;
    public Problem(int status, String message) { super(message); this.status = status; }
    public static void require(boolean condition, String message) {
        if (!condition) throw new Problem(409, message);
    }
    public static void denied() { throw new Problem(403, "No tienes permiso vigente para esta operacion o recurso."); }
}
