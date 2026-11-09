package cl.nexbyte.sigera;

import java.sql.Statement;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class Db {
    private final JdbcTemplate jdbc;
    public Db(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public javax.sql.DataSource dataSource() { return Objects.requireNonNull(jdbc.getDataSource(), "DataSource no configurado"); }
    public List<Map<String,Object>> rows(String sql, Object... values) {
        return jdbc.queryForList(sql, values).stream().map(row -> {
            Map<String,Object> out = new LinkedHashMap<>();
            row.forEach((k,v) -> out.put(k.toLowerCase(Locale.ROOT), v)); return out;
        }).toList();
    }
    public Map<String,Object> one(String sql, Object... values) {
        var result = rows(sql, values);
        if (result.isEmpty()) throw new Problem(404, "Registro no disponible.");
        return result.getFirst();
    }
    public int update(String sql, Object... values) { return jdbc.update(sql, values); }
    public long count(String sql, Object... values) { return Objects.requireNonNull(jdbc.queryForObject(sql, Long.class, values)); }
    public long insert(String sql, Object... values) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(c -> {
            var ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < values.length; i++) ps.setObject(i + 1, values[i]);
            return ps;
        }, keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
    public void lock() { jdbc.queryForObject("SELECT id FROM operation_lock WHERE id=1 FOR UPDATE", Integer.class); }

    /** Mensaje visible cuando la base no admite escrituras (sin detalles tecnicos ni SQL). */
    public static final String READ_ONLY_MESSAGE = "La base de datos de SIGER no admite escrituras en este momento. No se registro la operacion; avisa al equipo tecnico.";

    /**
     * Base abierta solo para lectura: H2 cuando el proceso no puede escribir el archivo (90097),
     * transaccion de solo lectura (25006) o MySQL con read_only/super_read_only (1290, 1836).
     */
    public static boolean readOnly(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof java.sql.SQLException e && ("90097".equals(e.getSQLState()) || "25006".equals(e.getSQLState()) || e.getErrorCode() == 90097 || e.getErrorCode() == 1290 || e.getErrorCode() == 1836)) return true;
        }
        return false;
    }

    /**
     * Comprueba al arrancar que la base acepta escrituras: actualiza sin cambios la fila del lock global
     * y revierte. H2 abre en solo lectura, sin avisar, un archivo que el proceso no puede escribir.
     */
    public static void requireWritable(java.sql.Connection c) throws java.sql.SQLException {
        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false);
        try (var st = c.createStatement()) {
            st.executeUpdate("UPDATE operation_lock SET id=id WHERE id=1");
        } catch (java.sql.SQLException e) {
            if (readOnly(e)) throw new IllegalStateException("La base de datos se abrio en modo solo lectura. Revisa que la cuenta que ejecuta SIGER pueda escribir el archivo o el esquema de la base (por ejemplo var\\local-db.mv.db creado desde una consola de administrador).", e);
            throw e;
        } finally {
            c.rollback();
            c.setAutoCommit(auto);
        }
    }
    public void audit(long actor, String action, String kind, long id, Long scope, String note) {
        update("INSERT INTO audit_event(actor_id,at_ms,action,resource_type,resource_id,scope_id,note) VALUES(?,?,?,?,?,?,?)",
            actor, System.currentTimeMillis(), action, kind, id, scope, note == null ? "" : note);
    }
    public static long n(Map<String,Object> r, String k) { return ((Number)r.get(k)).longValue(); }
    public static Long nullableId(Map<String,Object> r, String k) { return r.get(k) == null ? null : n(r,k); }
    public static String s(Map<String,Object> r, String k) { return r.get(k) == null ? "" : r.get(k).toString(); }
    public static boolean b(Map<String,Object> r, String k) {
        Object v=r.get(k); return Boolean.TRUE.equals(v) || v instanceof Number num && num.intValue()!=0;
    }
    public static void revision(Map<String,Object> r, Long expected) {
        Problem.require(expected != null && n(r,"revision")==expected, "El registro cambio. Actualiza la ficha antes de confirmar.");
    }
}
