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
    public static List<Map<String,Object>> json() { return ALL.stream().map(Role::json).toList(); }
}
