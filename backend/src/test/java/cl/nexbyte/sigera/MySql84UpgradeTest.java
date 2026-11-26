package cl.nexbyte.sigera;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static cl.nexbyte.sigera.Db.*;

/**
 * Actualizacion 0.4.0 -> version actual sobre un MySQL 8.4 AISLADO y desechable (G13; mismas propiedades que
 * {@link MySql84IntegrationTest}; sin ellas se omite). Carga en un esquema nuevo el volcado sintetico
 * {@code upgrade/siger-0.4.0-sintetico.sql} (V10, semillas demo de 0.4.0 y registros creados con sus comandos: COM, SOL
 * devuelta, redistribuida y descartada, REQ cerrado y reactivado, Casos con acuse, tareas, espera y revision de cierre,
 * colaboracion, derivacion y transferencia rechazada, primera atencion GENERAL 1.1 y NNA, una subrogancia activa sobre
 * la JEFATURA de REG-DEMO y las 14 AUTORIDAD de las semillas). Arranca primero con {@code migrate-only}, como
 * {@code Migrate.ps1}, y luego en modo normal, y verifica migraciones, marcadores, supresion, conversion, indice de
 * personas y la reconstruccion de hitos con sus omisiones. La verificacion con datos reales se hace en la VM (G14).
 */
class MySql84UpgradeTest {
    static final String SCHEMA="siger_a_mysql_upgrade",DUMP="/upgrade/siger-0.4.0-sintetico.sql";
    @BeforeAll static void requireIsolatedMySql(){MySql84IntegrationTest.requireIsolatedMySql();}

    static Connection connect(String schema)throws SQLException{
        return DriverManager.getConnection(MySql84IntegrationTest.BASE+"/"+schema+"?sslMode=REQUIRED&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true",
            System.getProperty("siger.mysql.user"),System.getProperty("siger.mysql.password"));
    }
    /** El volcado de mysqldump termina cada sentencia en fin de linea (los saltos de un texto van escapados). */
    static void load()throws Exception{load(SCHEMA,DUMP);}
    /** Carga un volcado sintetico en un esquema recreado vacio (tambien el de 0.7.0, MySql84Upgrade070Test). */
    static void load(String schema,String dump)throws Exception{
        try(var c=connect("");var st=c.createStatement()){
            st.execute("DROP DATABASE IF EXISTS "+schema);
            st.execute("CREATE DATABASE "+schema+" CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }
        int statements=0;
        try(var c=connect(schema);var st=c.createStatement();
            var in=new BufferedReader(new InputStreamReader(Objects.requireNonNull(MySql84UpgradeTest.class.getResourceAsStream(dump)),StandardCharsets.UTF_8))){
            var sql=new StringBuilder();
            for(String line;(line=in.readLine())!=null;){
                if(sql.isEmpty()&&(line.isBlank()||line.startsWith("--")))continue;
                sql.append(line).append('\n');
                if(line.endsWith(";")){st.execute(sql.toString());sql.setLength(0);statements++;}
            }
            assertTrue(sql.toString().isBlank(),"sentencia final incompleta");
        }
        assertTrue(statements>300,"sentencias del volcado: "+statements);
    }
    static long count(Connection c,String sql)throws SQLException{
        try(var st=c.createStatement();var rs=st.executeQuery(sql)){rs.next();return rs.getLong(1);}
    }
    /** Lo que las rutinas de arranque escriben: un segundo arranque no debe cambiarlo. */
    static Map<String,Long> state()throws SQLException{
        var out=new TreeMap<String,Long>();
        try(var c=connect(SCHEMA)){
            for(String t:List.of("milestone","person_reference","audit_event","role_assignment","application_marker","substitution_scope","staff","flyway_schema_history","stat_declared_version","stat_declared_person"))
                out.put(t,count(c,"SELECT COUNT(*) FROM "+t));
            out.put("role_assignment.revocadas",count(c,"SELECT COUNT(*) FROM role_assignment WHERE revoked_at IS NOT NULL"));
            out.put("staff.security_version",count(c,"SELECT COALESCE(SUM(security_version),0) FROM staff"));
            out.put("milestone.max_id",count(c,"SELECT COALESCE(MAX(id),0) FROM milestone"));
            out.put("person_reference.max_id",count(c,"SELECT COALESCE(MAX(id),0) FROM person_reference"));
        }
        return out;
    }
    static List<Map<String,Object>> index(Db db){
        return db.rows("SELECT communication_id,requirement_id,source,person_role,display_name,name_key,run_number,run_dv,document_key,authenticated FROM person_reference "
            +"ORDER BY communication_id,source,person_role,display_name,run_number,document_key");
    }

    @Test void actualizacionDesdeUnVolcadoSintetico040()throws Exception{
        load();
        try(var c=connect(SCHEMA)){
            assertEquals(10,count(c,"SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success=TRUE"));
            assertEquals(14,count(c,"SELECT COUNT(*) FROM role_assignment WHERE role_code='AUTORIDAD' AND revoked_at IS NULL"));
            assertEquals(1,count(c,"SELECT COUNT(*) FROM role_assignment g JOIN org_scope o ON o.id=g.scope_id WHERE o.regional=TRUE AND g.role_code='JEFATURA' AND g.revoked_at IS NULL"));
            assertEquals(0,count(c,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='"+SCHEMA+"' AND table_name IN ('person_reference','milestone','business_holiday')"));
        }

        var prints=MySql84Upgrade070Test.fingerprints(SCHEMA);
        // 1. Como Migrate.ps1: sin web, migra y ejecuta las rutinas de arranque; Bootstrap cierra el contexto.
        try(var migrate=MySql84IntegrationTest.app(SCHEMA,List.of("siger.migrate-only=true"))){assertFalse(migrate.isActive(),"migrate-only cierra el proceso");}
        var migrated=state();

        // 2. Arranque normal (sin semillas, como la VM): nada que reconstruir ni convertir de nuevo.
        try(var ctx=MySql84IntegrationTest.app(SCHEMA,List.of())){
            assertEquals(migrated,state(),"el arranque normal tras migrate-only no cambia nada");
            var db=ctx.getBean(Db.class);var access=ctx.getBean(Access.class);
            assertEquals("24",ctx.getBean(Api.class).health().getBody().get("schema"));
            for(int v=11;v<=21;v++)assertEquals(1,db.count("SELECT COUNT(*) FROM flyway_schema_history WHERE version=? AND success=TRUE",String.valueOf(v)),"V"+v);
            for(String marker:List.of(Bootstrap.AUTHORITY_MARKER,Bootstrap.REGIONAL_HEAD_MARKER,PersonIndex.MARKER,Milestones.MARKER,OriginRegion.MARKER))
                assertEquals(1,db.count("SELECT COUNT(*) FROM application_marker WHERE code=?",marker),marker);
            // Resultado de la primera atencion (V20, E06): las dos del volcado, desde su constancia exacta.
            assertEquals(List.of("ORIENTACION","RESPUESTA_DIRECTA"),db.rows("SELECT immediate_result FROM requirement WHERE immediate_result_reconstructed=TRUE ORDER BY id").stream().map(r->s(r,"immediate_result")).toList());
            assertEquals(0,n(db.one("SELECT amount FROM application_marker WHERE code=?",Attention.RESULT_MARKER),"amount"));
            assertEquals(1,db.count("SELECT COUNT(*) FROM application_marker WHERE code=?",PersonDims.MARKER));
            assertEquals(db.count("SELECT COUNT(*) FROM attention_revision"),db.count("SELECT COUNT(*) FROM stat_declared_version"));
            assertEquals(prints,MySql84Upgrade070Test.fingerprints(SCHEMA));
            // Region de cada version de la ficha (V16, D21): la GENERAL con region 13 y la NNA sin region.
            assertEquals("13",s(db.one("SELECT region_code FROM attention_revision WHERE id=1"),"region_code"));
            assertNull(db.one("SELECT region_code FROM attention_revision WHERE id=2").get("region_code"));

            // Supresion de AUTORIDAD: 14 revocadas (ninguna borrada) por la identidad tecnica, con su fundamento.
            long maintenance=n(db.one("SELECT id FROM staff WHERE username='sistema:actualizacion' AND technical=TRUE AND enabled=FALSE"),"id");
            assertEquals(0,db.count("SELECT COUNT(*) FROM role_assignment WHERE role_code='AUTORIDAD' AND revoked_at IS NULL"));
            assertEquals(14,db.count("SELECT COUNT(*) FROM role_assignment WHERE role_code='AUTORIDAD'"));
            assertEquals(14,db.count("SELECT COUNT(*) FROM audit_event WHERE actor_id=? AND action='FUNCION_REVOCADA' AND note LIKE 'AUTORIDAD.%'",maintenance));

            // Coordinacion regional: la JEFATURA de REG-DEMO pasa a DIRECCION y la subrogancia viva la sigue.
            long site=n(db.one("SELECT id FROM org_scope WHERE code='REG-DEMO'"),"id"),coord=n(db.one("SELECT id FROM staff WHERE username='coordinacion.regional'"),"id");
            assertEquals(0,db.count("SELECT COUNT(*) FROM role_assignment WHERE scope_id=? AND role_code='JEFATURA' AND revoked_at IS NULL",site));
            long direction=n(db.one("SELECT id FROM role_assignment WHERE user_id=? AND scope_id=? AND role_code='DIRECCION' AND revoked_at IS NULL",coord,site),"id");
            var repointed=db.one("SELECT grant_id,role_code FROM substitution_scope");
            assertEquals(direction,n(repointed,"grant_id"));assertEquals("DIRECCION",s(repointed,"role_code"));
            assertEquals(1,db.count("SELECT COUNT(*) FROM audit_event WHERE actor_id=? AND action='FUNCION_OTORGADA'",maintenance));
            assertTrue(access.actor("profesional.regional").head(site),"el subrogante ejerce la Direccion de la sede");
            assertFalse(access.actor("coordinacion.regional").head(site),"mientras rige la subrogancia, el titular no ejerce la Direccion");
            assertTrue(access.actor("coordinacion.regional").professional(site),"y conserva su cartera profesional");
            assertEquals("ACTIVA",s(db.one("SELECT state FROM org_scope WHERE id=?",site),"state"));
            assertNull(db.one("SELECT region_code FROM org_scope WHERE id=?",site).get("region_code"),"sin semillas, TI asigna la region de la sede");

            // Indice de personas: las 10 referencias de los roles indexados (la "otra persona" queda fuera) y
            // exactamente lo que escribiria cada comando al refrescar su COM.
            var loaded=index(db);
            assertEquals(10,loaded.size());
            assertEquals(0,db.count("SELECT COUNT(*) FROM person_reference WHERE name_key LIKE '%senalada%'"));
            assertEquals(2,db.count("SELECT COUNT(*) FROM person_reference WHERE run_number='12345678' AND run_dv='5'"),"COM 1 y la ficha (la COM copiada de la ficha no se indexa)");
            assertEquals(1,db.count("SELECT COUNT(*) FROM person_reference WHERE document_key='AB123456'"));
            var persons=ctx.getBean(PersonIndex.class);var tx=new TransactionTemplate(ctx.getBean(PlatformTransactionManager.class));
            tx.executeWithoutResult(st->{db.lock();for(var com:db.rows("SELECT id FROM communication"))persons.refresh(n(com,"id"));});
            assertEquals(loaded,index(db),"la carga inicial reproduce el refresco de los comandos");
            var found=ctx.getBean(PersonSearch.class).search(access.actor("coordinacion.requerimientos"),"12.345.678-5",null,PersonSearch.Filters.of(null,false,null,null,null,null));
            assertTrue(found.toString().contains("COM-A-000001"),"el buscador encuentra la COM por el RUN migrado");

            // Hitos: todos reconstruidos, sin omisiones ni aviso.
            assertEquals(62,db.count("SELECT COUNT(*) FROM milestone"));
            assertEquals(62,db.count("SELECT COUNT(*) FROM milestone WHERE reconstructed=TRUE"));
            assertEquals(25,db.count("SELECT COUNT(DISTINCT event) FROM milestone"));
            assertEquals(0,Milestones.omitted(db));
            for(var e:Map.of("COM_REGISTRADA",8L,"SOL_DISTRIBUIDA",4L,"SOL_ASUMIDA",3L,"REQ_CREADO",6L,"CASO_CREADO",6L,"REQ_CERRADO",3L,"REQ_REACTIVADO",1L,"FLUJO_DECIDIDO",3L).entrySet())
                assertEquals(e.getValue(),db.count("SELECT COUNT(*) FROM milestone WHERE event=?",e.getKey()),e.getKey());
            var codes=Personnel.warnings(db).stream().map(w->s(w,"code")).toList();
            assertFalse(codes.contains("HITOS_OMITIDOS"));assertFalse(codes.contains("JEFATURA_EN_SEDE"));assertFalse(codes.contains("SIN_TI_PERMANENTE"));

            // Reportes sobre los hitos reconstruidos (periodo fijo del volcado).
            long ugi=n(db.one("SELECT id FROM staff WHERE username='direccion.ugi'"),"id");
            db.update("INSERT INTO role_assignment(user_id,role_code,scope_id,valid_from,basis) VALUES(?,?,NULL,0,'PRUEBA DE ACTUALIZACION')",ugi,"REPORTES");
            var reports=ctx.getBean(Reports.class);
            var total=reports.report(access.actor("direccion.ugi"),new Reports.Request("REQ_TOTAL","2026-11-01","2026-11-30",null,null,null,null,null,null,null,null,null,null,false,null,null));
            var rows=(List<?>)total.get("rows");
            assertEquals(1,rows.size());
            var row=(Map<?,?>)rows.getFirst();
            assertEquals(3,((Number)row.get("n")).longValue(),"REQ cerrados en el periodo");
            assertEquals(3,((Number)row.get("reconstructed")).longValue(),"todos sobre hitos reconstruidos");
        }
        assertEquals(migrated.get("milestone"),state().get("milestone"));
    }
}
