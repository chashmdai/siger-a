package cl.nexbyte.sigera;

import java.util.*;

/**
 * Catalogo unico de funciones concedibles (0.5.0, D1-D3). Los roles son fijos: cada uno conserva sus reglas en
 * Access y BoardQueries; aqui solo se declara su alcance y su grupo. AUTORIDAD fue suprimido (D1) y no figura.
 * Un rol se publica solo cuando ya tiene efecto en el servidor.
 */
public final class Roles {
    /** AMBITO = ambito obligatorio; INSTITUCIONAL = sin ambito; OPCIONAL = ambito opcional (nulo = toda la institucion). */
    public enum Reach { AMBITO, INSTITUCIONAL, OPCIONAL }
    public record Role(String code, Reach reach, String group) {
        public boolean scoped() { return reach==Reach.AMBITO; }
        public boolean optional() { return reach==Reach.OPCIONAL; }
        public Map<String,Object> json() { return Map.of("code",code,"reach",reach.name(),"group",group); }
    }
    public static final String TI="TI";
    /** D4: lectura de todos los expedientes salvo lo reservado; nunca concede gestion. */
    public static final String CONSULTA_INSTITUCIONAL="CONSULTA_INSTITUCIONAL";
    /**
     * D6 (G11): cifras agregadas sin datos de personas, en un alcance opcional (nulo = institucion; raiz de unidad = sus
     * ambitos centrales; area o sede = ese ambito; raiz de UPR, UPD o UEP = ademas su materia en sedes). No lee expedientes.
     */
    public static final String REPORTES="REPORTES";
    /** Desglose por funcionario de los reportes; exige REPORTES en el mismo alcance (decision institucional). */
    public static final String REPORTES_PERSONAS="REPORTES_PERSONAS";
    /** Exportacion de filas sin datos ciudadanos (G12): CSV de REQ y Casos de Reportes y resumen de bandejas; exige REPORTES en el mismo alcance. */
    public static final String EXPORTACION="EXPORTACION";
    /**
     * Gestion estadistica (0.8.0, D43, gate E02): generar y anular cortes, importar y anular series y compartir
     * configuraciones dentro de su alcance; exige REPORTES en el mismo alcance y es incompatible con TI. Se concede desde
     * E02, pero no habilita nada hasta E10 y E11 (desvio registrado en la bitacora).
     */
    public static final String GESTION_ESTADISTICA="GESTION_ESTADISTICA";
    /** Funciones que solo rigen con REPORTES vigente en el mismo alcance (G11, G12, E02), en orden estable. */
    public static final Set<String> NEEDS_REPORTES=Collections.unmodifiableSet(new LinkedHashSet<>(List.of(REPORTES_PERSONAS,EXPORTACION,GESTION_ESTADISTICA)));
    public static final List<Role> ALL=List.of(
        new Role("ADMINISTRATIVO",Reach.AMBITO,"OPERATIVA"),
        new Role("PROFESIONAL",Reach.AMBITO,"OPERATIVA"),
        new Role("JEFATURA",Reach.AMBITO,"OPERATIVA"),
        new Role("DIRECCION",Reach.AMBITO,"OPERATIVA"),
        new Role("DISTRIBUIDOR",Reach.INSTITUCIONAL,"OPERATIVA"),
        new Role("LECTURA",Reach.AMBITO,"CONSULTA"),
        new Role(CONSULTA_INSTITUCIONAL,Reach.INSTITUCIONAL,"CONSULTA"),
        new Role("AUDITORIA",Reach.AMBITO,"CONTROL"),
        new Role(REPORTES,Reach.OPCIONAL,"REPORTES"),
        new Role(REPORTES_PERSONAS,Reach.OPCIONAL,"REPORTES"),
        new Role(EXPORTACION,Reach.OPCIONAL,"REPORTES"),
        new Role(GESTION_ESTADISTICA,Reach.OPCIONAL,"REPORTES"),
        new Role(TI,Reach.INSTITUCIONAL,"ADMINISTRACION"));
    private static final Map<String,Role> BY_CODE=new LinkedHashMap<>();
    static { for(var r:ALL)BY_CODE.put(r.code(),r); }
    private Roles() { }

    /** Rol del catalogo o 409 si no existe o fue suprimido. */
    public static Role require(String code) {
        var role=BY_CODE.get(code);
        Problem.require(role!=null,"Funcion no disponible.");
        return role;
    }
    /**
     * Funcion de lectura de expedientes (G08b): las operativas y las de consulta (LECTURA, CONSULTA_INSTITUCIONAL).
     * AUDITORIA (control) y TI (administracion) no leen expedientes por si mismas. Es la regla del buscador.
     */
    public static boolean readsRecords(String code) {
        var role=BY_CODE.get(code);
        return role!=null&&(role.group().equals("OPERATIVA")||role.group().equals("CONSULTA"));
    }
    /** Funcion que abre comunicaciones o fichas de primera atencion de su ambito (G08c): las operativas. */
    public static boolean opensIntake(String code) {
        var role=BY_CODE.get(code);
        return role!=null&&role.group().equals("OPERATIVA");
    }
    /**
     * Concesiones de una funcion de NEEDS_REPORTES que rigen: las que tienen REPORTES vigente en el mismo alcance (nulo =
     * institucion). Es la regla de la sesion (can_manage_stats) y de las facultades estadisticas de E10 y E11.
     */
    public static List<Access.Grant> ruling(Access.Actor a,String role) {
        return a.grants().stream().filter(g->g.role().equals(role)&&a.grants().stream().anyMatch(x->x.role().equals(REPORTES)&&Objects.equals(x.scope(),g.scope()))).toList();
    }
    public static List<Map<String,Object>> json() { return ALL.stream().map(Role::json).toList(); }
}
