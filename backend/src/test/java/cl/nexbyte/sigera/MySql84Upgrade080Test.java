package cl.nexbyte.sigera;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static cl.nexbyte.sigera.Db.*;

/**
 * Actualizacion 0.8.0 -> version actual sobre un MySQL 8.4 AISLADO y desechable (A00 de 0.9.1; mismas propiedades que
 * {@link MySql84IntegrationTest}; sin ellas se omite). Carga el volcado sintetico {@code upgrade/siger-0.8.0-sintetico.sql}
 * (V24: la base 0.7.0 de E01 migrada con 0.8.0 y, con sus comandos, catalogos vigentes con clasificaciones y derechos,
 * COM del Formulario adulto con ClaveUnica y correo con REQ y Caso abierto, COM manual con ficha de ingreso, correo y
 * restricciones de contacto con Caso abierto, Caso cerrado con su REQ abierto, vinculo ADMITIDA al Caso, un corte y una
 * vista guardada). Arranca con {@code migrate-only}, como {@code Migrate.ps1}, y luego en modo normal: ninguna tabla
 * existente cambia de cantidad de filas (salvo el historial de Flyway y los marcadores nuevos), los originales del
 * Formulario y sus filas {@code external_*} quedan identicos y el escenario sigue presente. Su par en H2 es
 * {@link H2Upgrade080Test}, con el mismo contenido en un SCRIPT de H2.
 */
class MySql84Upgrade080Test {
    static final String SCHEMA="siger_a_mysql_upgrade_desde080",DUMP="/upgrade/siger-0.8.0-sintetico.sql";
    /** Tablas de la recepcion del Formulario que ningun codigo actualiza ni borra (regla 2 del plan 0.9.1). */
    static final List<String> ORIGINALS=List.of("communication_original","external_person","external_attribute","external_field","external_document");
    /** Marcadores de las rutinas de arranque agregadas despues de 0.8.0 (ninguna todavia). */
    static final List<String> NEW_MARKERS=List.of();
    @BeforeAll static void requireIsolatedMySql(){MySql84IntegrationTest.requireIsolatedMySql();}

    interface Source{Connection open()throws SQLException;}

    static List<String> tables(Connection c,String sql)throws SQLException{
        var out=new ArrayList<String>();
        try(var st=c.createStatement();var rs=st.executeQuery(sql)){while(rs.next())out.add(rs.getString(1).toLowerCase(Locale.ROOT));}
        Collections.sort(out);
        return out;
    }
    static Map<String,Long> counts(Source source,List<String> tables)throws SQLException{
        var out=new TreeMap<String,Long>();
        try(var c=source.open()){for(String t:tables)out.put(t,MySql84UpgradeTest.count(c,"SELECT COUNT(*) FROM "+t));}
        return out;
    }
    /** SHA-256 de todas las columnas de todas las filas, en orden de la clave (primera columna), de cada tabla de los originales. */
    static Map<String,String> fingerprints(Source source)throws Exception{
        var out=new TreeMap<String,String>();
        try(var c=source.open()){
            for(String t:ORIGINALS){
                var digest=MessageDigest.getInstance("SHA-256");
                try(var st=c.createStatement();var rs=st.executeQuery("SELECT * FROM "+t+" ORDER BY 1")){
                    int columns=rs.getMetaData().getColumnCount();
                    while(rs.next()){
                        for(int i=1;i<=columns;i++){String v=rs.getString(i);digest.update((v==null?"\u0000NULL":v).getBytes(StandardCharsets.UTF_8));digest.update((byte)0x1f);}
                        digest.update((byte)0x1e);
                    }
                }
                out.put(t,HexFormat.of().formatHex(digest.digest()));
            }
        }
        return out;
    }
    /** Lo que el volcado debe traer para las pruebas de 0.9.1 (elegibilidad de D58, DI-20, cierre y vinculo al Caso). */
    static void scenario(Db db){
        assertEquals(1,db.count("SELECT COUNT(*) FROM external_person p JOIN communication c ON c.id=p.communication_id JOIN requirement r ON r.communication_id=c.id"
            +" JOIN case_record k ON k.requirement_id=r.id WHERE p.person_role='PRESENTADOR' AND p.authenticated=TRUE AND p.run_source='CLAVE_UNICA_USERINFO' AND p.run_valid=TRUE"
            +" AND c.contact_email IS NOT NULL AND k.state<>'CERRADO'"),"requirente ClaveUnica con correo y Caso abierto");
        assertEquals(1,db.count("SELECT COUNT(*) FROM communication c JOIN intake_record i ON i.communication_id=c.id JOIN requirement r ON r.communication_id=c.id"
            +" JOIN case_record k ON k.requirement_id=r.id WHERE c.contact_restrictions IS NOT NULL AND c.contact_restrictions<>'' AND c.contact_email IS NOT NULL AND k.state<>'CERRADO'"),"restricciones de contacto con Caso abierto");
        assertTrue(db.count("SELECT COUNT(*) FROM case_record k JOIN requirement r ON r.id=k.requirement_id WHERE k.state='CERRADO' AND r.state<>'CERRADO'")>=1,"Caso cerrado con su REQ abierto");
        assertTrue(db.count("SELECT COUNT(*) FROM communication_link WHERE state='ADMITIDA' AND target_case_id IS NOT NULL AND returned_ms IS NULL")>=1,"vinculo admitido al Caso");
        assertEquals(1,db.count("SELECT COUNT(*) FROM stat_cut"));assertEquals(1,db.count("SELECT COUNT(*) FROM stat_view"));
        assertEquals(8,db.count("SELECT COUNT(*) FROM stat_catalog_version WHERE state='VIGENTE'"));
        assertTrue(db.count("SELECT COUNT(*) FROM stat_event")>0);
        assertEquals(0,db.count("SELECT COUNT(*) FROM staff WHERE password_hash<>'volcado-sintetico-sin-acceso'"),"claves invalidadas");
    }

    /** Recorrido comun: carga ya hecha; migrate-only, arranque normal y comprobaciones. */
    static void upgrade(Source source,List<String> before,java.util.function.Function<List<String>,org.springframework.context.ConfigurableApplicationContext> app)throws Exception{
        var unchanged=before.stream().filter(t->!t.equals("flyway_schema_history")&&!t.equals("application_marker")).toList();
        var loaded=counts(source,unchanged);var prints=fingerprints(source);
        long markers=counts(source,List.of("application_marker")).get("application_marker");
        int latest=MySql84Upgrade070Test.latest();
        try(var c=source.open()){
            assertEquals(1,MySql84UpgradeTest.count(c,"SELECT COUNT(*) FROM flyway_schema_history WHERE version='24' AND success=TRUE"),"el volcado llega a V24");
            assertEquals(0,MySql84UpgradeTest.count(c,"SELECT COUNT(*) FROM flyway_schema_history WHERE version='25'"),"y no mas alla");
        }

        // 1. Como Migrate.ps1: sin web, migra y ejecuta las rutinas de arranque; Bootstrap cierra el contexto.
        try(var migrate=app.apply(List.of("siger.migrate-only=true"))){assertFalse(migrate.isActive(),"migrate-only cierra el proceso");}
        assertEquals(loaded,counts(source,unchanged),"migrar no cambia la cantidad de filas de ninguna tabla existente");
        assertEquals(markers+NEW_MARKERS.size(),counts(source,List.of("application_marker")).get("application_marker"),"solo los marcadores nuevos");
        assertEquals(prints,fingerprints(source),"originales del Formulario y external_* identicos");

        // 2. Arranque normal (sin semillas, como la VM): nada cambia y el escenario sigue.
        try(var ctx=app.apply(List.of())){
            var db=ctx.getBean(Db.class);
            assertEquals(String.valueOf(latest),ctx.getBean(Api.class).health().getBody().get("schema"));
            for(int v=1;v<=latest;v++)assertEquals(1,db.count("SELECT COUNT(*) FROM flyway_schema_history WHERE version=? AND success=TRUE",String.valueOf(v)),"V"+v);
            for(String marker:List.of(Bootstrap.AUTHORITY_MARKER,Bootstrap.REGIONAL_HEAD_MARKER,PersonIndex.MARKER,Milestones.MARKER,OriginRegion.MARKER,
                    Attention.SUMMARY_MARKER,Attention.RESULT_MARKER,PersonDims.MARKER))
                assertEquals(1,db.count("SELECT COUNT(*) FROM application_marker WHERE code=?",marker),marker);
            for(var o:db.rows("SELECT document,document_sha256 FROM communication_original"))
                assertEquals(s(o,"document_sha256"),ExternalOriginal.sha256(s(o,"document").getBytes(StandardCharsets.UTF_8)));
            assertEquals(0,Milestones.omitted(db));
            scenario(db);
        }
        assertEquals(loaded,counts(source,unchanged),"el arranque normal no cambia nada");
        assertEquals(prints,fingerprints(source));
    }

    @Test void actualizacionDesdeUnVolcadoSintetico080()throws Exception{
        MySql84UpgradeTest.load(SCHEMA,DUMP);
        Source source=()->MySql84UpgradeTest.connect(SCHEMA);
        List<String> before;
        try(var c=source.open()){
            var st=c.prepareStatement("SELECT table_name FROM information_schema.tables WHERE table_schema=? ORDER BY table_name");
            st.setString(1,SCHEMA);before=new ArrayList<>();
            try(var rs=st.executeQuery()){while(rs.next())before.add(rs.getString(1).toLowerCase(Locale.ROOT));}
            assertEquals(4,MySql84UpgradeTest.count(c,"SELECT COUNT(*) FROM communication_original"));
        }
        upgrade(source,before,extra->MySql84IntegrationTest.app(SCHEMA,extra));
    }
}
