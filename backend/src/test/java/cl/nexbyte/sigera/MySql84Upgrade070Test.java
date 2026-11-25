package cl.nexbyte.sigera;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static cl.nexbyte.sigera.Db.*;

/**
 * Actualizacion 0.7.0 -> version actual sobre un MySQL 8.4 AISLADO y desechable (E01 de 0.8.0; mismas propiedades que
 * {@link MySql84IntegrationTest}; sin ellas se omite). Carga el volcado sintetico {@code upgrade/siger-0.7.0-sintetico.sql}
 * (V16, semillas demo de 0.7.0 y registros creados con sus comandos: recorrido completo de hitos, Formulario adulto y los
 * dos vectores NNA SRCeI de E00, fichas 1.0 y 1.1, transferencia, derivacion, vinculos admitido y devuelto, COM invalida y
 * pendientes, REQ cerrados y reactivado, cobertura de sede y del nivel central y una sede proyectada). Arranca con
 * {@code migrate-only}, como {@code Migrate.ps1}, y luego en modo normal: las migraciones nuevas se aplican, ninguna
 * tabla existente cambia de cantidad de filas (salvo el historial de Flyway), los originales del Formulario y sus filas
 * {@code external_*} quedan identicos y ningun catalogo estadistico queda sembrado.
 */
class MySql84Upgrade070Test {
    static final String SCHEMA="siger_a_mysql_upgrade_desde070",DUMP="/upgrade/siger-0.7.0-sintetico.sql";
    /** Tablas de la recepcion del Formulario que ningun codigo actualiza ni borra (seccion 2 del plan 0.8.0). */
    static final List<String> ORIGINALS=List.of("communication_original","external_person","external_attribute","external_field");
    /**
     * Marcadores de las rutinas de arranque agregadas despues de 0.7.0: es lo unico que cambia la cantidad de filas de
     * una tabla existente (application_marker) al actualizar (E03: resumen de cambios de la ficha, V18).
     */
    static final List<String> NEW_MARKERS=List.of(Attention.SUMMARY_MARKER,Attention.RESULT_MARKER,PersonDims.MARKER);
    @BeforeAll static void requireIsolatedMySql(){MySql84IntegrationTest.requireIsolatedMySql();}

    /** Mayor version de las migraciones del codigo (Vn__*.sql). */
    static int latest()throws Exception{
        try(var files=Files.list(Path.of("src/main/resources/db/migration"))){
            return files.map(p->p.getFileName().toString()).filter(f->f.matches("V\\d+__.+\\.sql")).mapToInt(f->Integer.parseInt(f.substring(1,f.indexOf("__")))).max().orElseThrow();
        }
    }
    static List<String> tables(Connection c)throws SQLException{
        var out=new ArrayList<String>();
        try(var st=c.prepareStatement("SELECT table_name FROM information_schema.tables WHERE table_schema=? ORDER BY table_name")){
            st.setString(1,SCHEMA);try(var rs=st.executeQuery()){while(rs.next())out.add(rs.getString(1).toLowerCase(Locale.ROOT));}
        }
        return out;
    }
    /** Filas por tabla de las tablas dadas. */
    static Map<String,Long> counts(List<String> tables)throws SQLException{
        var out=new TreeMap<String,Long>();
        try(var c=MySql84UpgradeTest.connect(SCHEMA)){for(String t:tables)out.put(t,MySql84UpgradeTest.count(c,"SELECT COUNT(*) FROM "+t));}
        return out;
    }
    /** SHA-256 de todas las columnas de todas las filas, en orden de id, de cada tabla de los originales. */
    static Map<String,String> fingerprints()throws Exception{return fingerprints(SCHEMA);}
    static Map<String,String> fingerprints(String schema)throws Exception{
        var out=new TreeMap<String,String>();
        try(var c=MySql84UpgradeTest.connect(schema)){
            for(String t:ORIGINALS){
                var digest=MessageDigest.getInstance("SHA-256");
                try(var st=c.createStatement();var rs=st.executeQuery("SELECT * FROM "+t+" ORDER BY id")){
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

    @Test void actualizacionDesdeUnVolcadoSintetico070()throws Exception{
        MySql84UpgradeTest.load(SCHEMA,DUMP);
        List<String> before;Map<String,Long> loaded;Map<String,String> prints;
        try(var c=MySql84UpgradeTest.connect(SCHEMA)){
            assertEquals(16,MySql84UpgradeTest.count(c,"SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success=TRUE"));
            before=tables(c);
            assertEquals(3,MySql84UpgradeTest.count(c,"SELECT COUNT(*) FROM communication_original"));
            assertEquals(2,MySql84UpgradeTest.count(c,"SELECT COUNT(*) FROM external_person WHERE run_source='SRCEI' AND authenticated=FALSE"));
        }
        var unchanged=before.stream().filter(t->!t.equals("flyway_schema_history")&&!t.equals("application_marker")).toList();
        loaded=counts(unchanged);prints=fingerprints();
        long markers=counts(List.of("application_marker")).get("application_marker");
        int latest=latest();

        // 1. Como Migrate.ps1: sin web, migra y ejecuta las rutinas de arranque; Bootstrap cierra el contexto.
        try(var migrate=MySql84IntegrationTest.app(SCHEMA,List.of("siger.migrate-only=true"))){assertFalse(migrate.isActive(),"migrate-only cierra el proceso");}
        assertEquals(loaded,counts(unchanged),"migrar no cambia la cantidad de filas de ninguna tabla existente");
        assertEquals(markers+NEW_MARKERS.size(),counts(List.of("application_marker")).get("application_marker"),"solo los marcadores nuevos");
        assertEquals(prints,fingerprints(),"originales del Formulario y external_* identicos");
        var migrated=counts(unchanged);
        var projected=counts(List.of("stat_declared_version","stat_declared_person"));
        assertEquals(loaded.get("attention_revision"),projected.get("stat_declared_version"));
        assertTrue(projected.get("stat_declared_person")>0);

        // 2. Arranque normal (sin semillas, como la VM): nada cambia.
        try(var ctx=MySql84IntegrationTest.app(SCHEMA,List.of())){
            var db=ctx.getBean(Db.class);
            assertEquals(String.valueOf(latest),ctx.getBean(Api.class).health().getBody().get("schema"));
            for(int v=1;v<=latest;v++)assertEquals(1,db.count("SELECT COUNT(*) FROM flyway_schema_history WHERE version=? AND success=TRUE",String.valueOf(v)),"V"+v);
            for(String marker:List.of(Bootstrap.AUTHORITY_MARKER,Bootstrap.REGIONAL_HEAD_MARKER,PersonIndex.MARKER,Milestones.MARKER,OriginRegion.MARKER))
                assertEquals(1,db.count("SELECT COUNT(*) FROM application_marker WHERE code=?",marker),marker);
            for(String marker:NEW_MARKERS)assertEquals(1,db.count("SELECT COUNT(*) FROM application_marker WHERE code=?",marker),marker);
            // V18: cada version de la ficha con una anterior tiene su resumen de cambios; la primera de cada REQ, no.
            if(latest>=18){
                assertEquals(db.count("SELECT COUNT(*) FROM attention_revision v WHERE EXISTS(SELECT 1 FROM attention_revision w WHERE w.requirement_id=v.requirement_id AND w.id<v.id)"),
                    db.count("SELECT COUNT(*) FROM attention_revision WHERE change_summary IS NOT NULL"));
                assertTrue(db.count("SELECT COUNT(*) FROM attention_revision WHERE change_summary IS NOT NULL")>0,"el volcado trae una ficha complementada");
                assertEquals(0,db.count("SELECT COUNT(*) FROM intake_record"),"ninguna COM anterior recibe ficha de ingreso");
            }
            // V20 (E06): el resultado de las dos primeras atenciones resueltas del volcado, desde su constancia exacta.
            if(latest>=20){
                assertEquals(2,db.count("SELECT COUNT(*) FROM requirement r WHERE EXISTS(SELECT 1 FROM closure_record c WHERE c.requirement_id=r.id AND c.case_id IS NULL AND c.mode='PRIMERA_ATENCION')"));
                assertEquals(2,db.count("SELECT COUNT(*) FROM requirement WHERE immediate_result='ORIENTACION' AND immediate_result_reconstructed=TRUE"));
                assertEquals(0,n(db.one("SELECT amount FROM application_marker WHERE code=?",Attention.RESULT_MARKER),"amount"),"nada omitido");
                assertEquals(0,db.count("SELECT COUNT(*) FROM management_note WHERE action_type IS NOT NULL"),"la historia queda sin tipificar");
            }
            // Cada original conserva su SHA-256 recalculado sobre el texto guardado.
            for(var o:db.rows("SELECT document,document_sha256 FROM communication_original"))
                assertEquals(s(o,"document_sha256"),ExternalOriginal.sha256(s(o,"document").getBytes(StandardCharsets.UTF_8)));
            // Ninguna migracion siembra catalogos estadisticos (V17 en adelante).
            if(latest>=17)for(String t:List.of("stat_catalog_version","stat_catalog_item","stat_catalog_mapping"))assertEquals(0,db.count("SELECT COUNT(*) FROM "+t),t);
            assertEquals(0,Milestones.omitted(db));
        }
        assertEquals(migrated,counts(unchanged),"el arranque normal tras migrate-only no cambia nada");
        assertEquals(projected,counts(List.of("stat_declared_version","stat_declared_person")));
        assertEquals(prints,fingerprints());

        // 3. E05 (V19): con los catalogos de referencia vigentes, la via y las propuestas de las COM del Formulario
        // anteriores a 0.8.0 se calculan (Java = SQL) sin escribir eventos ni tocar los originales.
        if(latest>=19)try(var ctx=MySql84IntegrationTest.app(SCHEMA,List.of())){
            var db=ctx.getBean(Db.class);var flow=ctx.getBean(Workflow.class);var stats=ctx.getBean(StatValues.class);var proposals=ctx.getBean(StatProposals.class);
            assertEquals(0,db.count("SELECT COUNT(*) FROM stat_event"),"ninguna migracion escribe eventos de clasificacion");
            db.update("UPDATE staff SET must_change=FALSE WHERE username='ti'");
            for(String file:List.of("tipo-solicitud-v1.json","tipo-requirente-v1.json","via-ingreso-v1.json")){
                String content=Files.readString(ReferenceCatalogsTest.DIR.resolve(file)).replace("\r\n","\n");
                long v=flow.run("ti",new Workflow.Command(UUID.randomUUID(),"CATALOG_IMPORT",null,null,Map.of("content",content,"reason","Ensayo sintetico"))).id();
                flow.run("ti",new Workflow.Command(UUID.randomUUID(),"CATALOG_ACTIVATE",v,0L,Map.of("reason","Ensayo sintetico")));
            }
            var coms=db.rows("SELECT id,channel FROM communication WHERE external_source IS NOT NULL ORDER BY id");
            assertEquals(3,coms.size());
            for(var c:coms){
                long id=n(c,"id");
                var via=stats.current(id,StatValues.VIA_INGRESO);
                assertEquals(StatValues.INFORMED,via.state());assertEquals(StatValues.SYSTEM,via.origin());assertEquals(StatValues.CHANNEL_VIA.get(s(c,"channel")),via.code());
                for(String variable:StatValues.SINGLE){
                    var java=stats.current(id,variable);
                    var sql=db.one("SELECT "+StatValues.stateSql("c",variable,null)+" AS st,"+StatValues.codeSql("c",variable,null)+" AS cd FROM communication c WHERE c.id=?",id);
                    assertEquals(java.state(),s(sql,"st"),variable);assertEquals(java.code(),sql.get("cd")==null?null:s(sql,"cd"),variable);
                }
                var proposed=proposals.of(id);
                for(String variable:List.of(StatValues.TIPO_SOLICITUD,StatValues.TIPO_REQUIRENTE)){
                    var p=db.one("SELECT "+StatProposals.sqlCode("c",variable)+" AS p FROM communication c WHERE c.id=?",id).get("p");
                    assertEquals(proposed.get(variable),p==null?null:p.toString(),"propuesta "+variable);
                }
                assertEquals(s(c,"channel").equals(StatProposals.NNA)?"NNA":"PERSONA_NATURAL_ADULTA",proposed.get(StatValues.TIPO_REQUIRENTE));
                assertEquals(StatValues.PENDING,stats.current(id,StatValues.TIPO_REQUIRENTE).state(),"la propuesta no cuenta como informado");
            }
            assertEquals(0,db.count("SELECT COUNT(*) FROM stat_event"),"calcular no escribe");
        }
        assertEquals(prints,fingerprints(),"originales del Formulario y external_* identicos despues de calcular");
    }
}
