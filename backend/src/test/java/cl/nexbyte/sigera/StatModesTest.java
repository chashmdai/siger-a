package cl.nexbyte.sigera;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import static cl.nexbyte.sigera.Db.*;
import static cl.nexbyte.sigera.FormularioDocs.*;
import static cl.nexbyte.sigera.StatClassificationTest.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Modos temporales, territorio, demografia y calidad del motor estadistico (0.8.0, gate E08). Cada prueba usa su propio
 * mes de recepcion, por lo que sus poblaciones no se mezclan. Escenarios 2 y 3 (historia y horario), 4, 6, 7 y 14
 * (agregado), D31, D32, D36, D37 y el corte "al constituirse el REQ".
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_motor_modos;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.seed-demo=true","siger.data=./target/test-motor-modos-${random.uuid}","siger.scheduling=false"})
@ActiveProfiles("test")
class StatModesTest extends StatFixture {
    static final String INST="rep.inst";
    @Autowired StatCompleteness completeness;
    @Autowired Lifecycle life;

    @BeforeEach void data()throws Exception{
        if(db.count("SELECT COUNT(*) FROM staff WHERE username=?",INST)>0)return;
        catalogs(CATALOGS);
        staff(INST,"REPORTES");
    }
    static Map<String,Object> month(String from){return period("MES",from);}
    long manual(String received,Map<String,Object> intake,Map<String,Object> stat){return com(ADMIN,"UPR-REQ","CORREO",received,intake,stat);}
    long reqOf(long com,Map<String,Object> stat){route(DIST,com,"UPR-REQ",null);return assume(HEAD,com,PROF,stat);}
    void complete(long com,String region){on(ADMIN,"COM_COMPLETE","communication",com,Map.of("intake",form(region),"reason","Complemento"));}
    /** Un requerimiento por resultado: clave de la dimension en el modo. */
    Map<String,Long> mode(String dimension,String month,Object... time){
        var r=query(INST,"INGRESOS",List.of(dimension),List.of(),month(month),time);
        return cells(r);
    }
    static Object[] at(long ms){return new Object[]{"time_mode","FECHA","as_of",Instant.ofEpochMilli(ms).toString()};}
    static final Object[] INGRESO={"time_mode","AL_INGRESO"},AL_REQ={"time_mode","AL_REQ"},VIGENTE={};
    long end(long req){return n(db.one("SELECT MIN(created_ms) AS t FROM command_log WHERE result_kind='REQ' AND result_id=?",req),"t");}

    // --- D25 a una fecha y horario de Chile ----------------------------------------------------------------------------
    @Test void estadoHistoricoD25(){
        String march="2024-03-01";
        long f=manual("2024-03-05T15:00:00Z",form(null),null),g=manual("2024-03-05T16:00:00Z",form(null),null);
        long target=reqOf(manual("2024-02-05T15:00:00Z",form(null),null),null);
        route(DIST,f,"UPR-REQ",null);discard(HEAD,f,"INVALIDA");
        on(DIST,"COM_LINK","communication",g,Map.of("folio",folio(target),"reason","Antecedente"));
        long link=last("communication_link");
        on(HEAD,"LINK_DECIDE","communication_link",link,Map.of("decision","RECHAZADA","reason","Otro"));
        on(HEAD,"LINK_RETURN","communication_link",link,Map.of("reason","Reclasificar"));
        registered(f,"2024-03-05T15:30:00Z");registered(g,"2024-03-05T16:30:00Z");
        db.update("UPDATE milestone SET at_ms=? WHERE communication_id=? AND event='SOL_DISTRIBUIDA'",ms("2024-03-06T15:00:00Z"),f);
        db.update("UPDATE solicitation SET created_ms=? WHERE communication_id=?",ms("2024-03-06T15:00:00Z"),f);
        db.update("UPDATE milestone SET at_ms=? WHERE communication_id=? AND event='SOL_DESCARTADA'",ms("2024-03-10T15:00:00Z"),f);
        db.update("UPDATE communication_link SET proposed_ms=?,returned_ms=? WHERE id=?",ms("2024-03-12T15:00:00Z"),ms("2024-03-20T15:00:00Z"),link);
        assertEquals(of("ENVIADA",1,"NUEVA",1),mode("ESTADO_COM",march,at(ms("2024-03-08T15:00:00Z"))));
        assertEquals(of("NUEVA",1),mode("ESTADO_COM",march,at(ms("2024-03-11T15:00:00Z"))),"F descartada");
        assertEquals(1,grand(query(INST,"EXCLUIDOS",List.of(),List.of(),month(march),at(ms("2024-03-11T15:00:00Z")))));
        assertEquals(of(),mode("ESTADO_COM",march,at(ms("2024-03-15T15:00:00Z"))),"G vinculada");
        assertEquals(1,grand(query(INST,"ANTECEDENTES",List.of(),List.of(),month(march),at(ms("2024-03-15T15:00:00Z")))));
        assertEquals(of("NUEVA",1),mode("ESTADO_COM",march,at(ms("2024-03-20T15:00:00Z"))),"G devuelta");
        assertEquals(of(),mode("ESTADO_COM",march,at(ms("2024-03-05T15:29:59Z"))),"antes de registrarse");
        // Estados de la SOL a la fecha, por hitos.
        assertEquals(of("PENDIENTE",1,"NO_APLICA",1),mode("ESTADO_SOL",march,at(ms("2024-03-08T15:00:00Z"))));
        assertEquals(1,grand(query(INST,"INGRESOS",List.of(),List.of(),month(march))),"vigente: solo G");
    }
    @Test void horarioChile(){
        for(String at:List.of("2026-04-05T02:30:00Z","2026-04-05T03:30:00Z","2026-04-05T04:00:00Z"))manual(at,form(null),null);
        var r=query(INST,"INGRESOS",List.of("PERIODO_DIA"),List.of(),days("2026-04-04","2026-04-05"));
        assertEquals(of("2026-04-04",2,"2026-04-05",1),cells(r));
        // Cambio de septiembre: el dia 6 empieza a las 04:00Z y el 7 a las 03:00Z.
        for(String at:List.of("2026-09-06T03:59:59Z","2026-09-06T04:00:00Z","2026-09-07T02:59:59Z","2026-09-07T03:00:00Z"))manual(at,form(null),null);
        assertEquals(of("2026-09-05",1,"2026-09-06",2,"2026-09-07",1),cells(query(INST,"INGRESOS",List.of("PERIODO_DIA"),List.of(),days("2026-09-04","2026-09-08"))));
        assertEquals(3,grand(query(INST,"INGRESOS",List.of(),List.of(),days("2026-09-06","2026-09-07"))));
    }

    // --- D37: modos temporales ---------------------------------------------------------------------------------------------
    @Test void cuatroModos(){
        String month="2024-05-01";
        long com=manual("2024-05-06T15:00:00Z",form("03"),null);
        complete(com,"05");
        reqOf(com,null);
        complete(com,"07");
        long asOf=Lifecycle.now();
        complete(com,"13");
        assertEquals(of("03",1),mode("REGION_REQUIRENTE",month,INGRESO));
        assertEquals(of("05",1),mode("REGION_REQUIRENTE",month,AL_REQ));
        assertEquals(of("07",1),mode("REGION_REQUIRENTE",month,at(asOf)));
        assertEquals(of("13",1),mode("REGION_REQUIRENTE",month,VIGENTE));
        // La region de origen registrada (D21) es la primera con region en todos los modos con evidencia.
        for(var time:List.of(INGRESO,AL_REQ,at(asOf),VIGENTE))assertEquals(of("03",1),mode("REGION_ORIGEN",month,time));
        // Clasificacion: primer valor, valor al constituirse el REQ, a la fecha y vigente.
        String june="2024-06-01";
        long classified=manual("2024-06-06T15:00:00Z",form("06"),stat("PROMOCION","SIN_INFORMACION"));
        run(DIST,"STAT_CLASSIFY",classified,stats.revision(classified),Map.of("stat",map(TS,code("CONSULTA_ORIENTACION"),"reason","Corrige")));
        route(DIST,classified,"UPR-REQ",null);
        assume(HEAD,classified,PROF,map(TS,code("VULNERACION"),"reason","Corrige al asumir"));
        long mid=Lifecycle.now();
        run(HEAD,"STAT_CLASSIFY",classified,stats.revision(classified),Map.of("stat",map(TS,code("OTRO"),"reason","Corrige despues")));
        assertEquals(of("PROMOCION",1),mode("TIPO_SOLICITUD",june,INGRESO));
        assertEquals(of("VULNERACION",1),mode("TIPO_SOLICITUD",june,AL_REQ));
        assertEquals(of("VULNERACION",1),mode("TIPO_SOLICITUD",june,at(mid)));
        assertEquals(of("OTRO",1),mode("TIPO_SOLICITUD",june,VIGENTE));
        assertEquals(of("CORREO",1),mode("VIA_INGRESO",june,INGRESO));
        // Responsable: inicial, al constituirse, a la fecha y vigente.
        long req=req(classified);
        on(HEAD,"FLOW_REQUEST","requirement",req,Map.of("kind","TRANSFERENCIA","target_scope",scope("UPD"),"reason","Traslado"));
        on("direccion.upd","FLOW_DECIDE","flow_request",last("flow_request"),Map.of("decision","ACEPTADA","reason","Aceptada"));
        assertEquals(of("UPD",1),mode("SEDE_VIGENTE",june,VIGENTE));
        for(var time:List.of(INGRESO,AL_REQ,at(mid)))assertEquals(of("UPR-REQ",1),mode("SEDE_VIGENTE",june,time));
        for(var time:List.of(INGRESO,AL_REQ,at(mid),VIGENTE))assertEquals(of("UPR-REQ",1),mode("SEDE_INICIAL",june,time));
        // Sin REQ, "al constituirse el REQ" no tiene evidencia.
        manual("2024-06-07T15:00:00Z",form("06"),stat("OTRO",null));
        assertEquals(of("VULNERACION",1,"PENDIENTE",1),mode("TIPO_SOLICITUD",june,AL_REQ));
        assertEquals(of("06",1,"PENDIENTE",1),mode("REGION_REQUIRENTE",june,AL_REQ));
    }
    /** Lo escrito hasta el fin del comando que constituyo el REQ pertenece a la constitucion; lo del siguiente, no. */
    @Test void corteVersionReq(){
        String month="2024-07-01";
        long x=manual("2024-07-06T15:00:00Z",form("05"),null);
        long reqX=reqOf(x,null);
        complete(x,"07");
        long y=manual("2024-07-07T15:00:00Z",form("08"),null);
        long reqY=reqOf(y,null);
        long t=ms("2024-07-10T15:00:00Z");
        for(long req:List.of(reqX,reqY))db.update("UPDATE command_log SET created_ms=? WHERE result_kind='REQ' AND result_id=?",t,req);
        db.update("UPDATE requirement SET created_ms=? WHERE id IN (?,?)",t-3,reqX,reqY);
        db.update("UPDATE stat_declared_version SET registered_ms=? WHERE communication_id=? AND requester_region='05'",t,x);
        db.update("UPDATE stat_declared_version SET registered_ms=? WHERE communication_id=? AND requester_region='07'",t+1,x);
        db.update("UPDATE stat_declared_version SET registered_ms=? WHERE communication_id=?",t+1,y);
        assertEquals(of("05",1,"PENDIENTE",1),mode("REGION_REQUIRENTE",month,AL_REQ));
        assertEquals(of("07",1,"08",1),mode("REGION_REQUIRENTE",month,VIGENTE));
    }
    /** El acto que constituye el REQ cuenta completo (ficha, clasificacion y derechos) y el comando siguiente nunca, ni con el mismo milisegundo. */
    @Test void actoDeConstitucion(){
        String month="2024-08-01";
        var statement=stat("VULNERACION","NNA");
        var rights=Map.of("variables",Map.of("DERECHO",add("3.1"),"CONTEXTO",add("FAMILIAR")));
        long direct=attention(ADMIN,"UPR-REQ","PRESENCIAL","2024-08-06T15:00:00Z",Map.of("region","07","commune","Talca"),"stat",statement,"resolve_now",true,"effective_at",ago(5),"result","ORIENTACION","reason","Orientacion","rights",rights);
        var body=map("scope_id",scope("UPR-REQ"),"received_at","2024-08-07T15:00:00Z","channel","PRESENCIAL","subject","Inmediata","body","Antecedentes","intake",form("07"),"stat",statement,
            "result","RESPUESTA_DIRECTA","reason","Respuesta","effective_at",ago(5),"rights",rights);
        long immediate=run(ADMIN,"COM_IMMEDIATE",null,null,body).id();
        // El REQ se inserta antes que el resto del acto: la evidencia es posterior a requirement.created_ms.
        db.update("UPDATE requirement SET created_ms=created_ms-5 WHERE id IN (?,?)",direct,immediate);
        for(String dimension:List.of("REGION_REQUIRENTE","TIPO_SOLICITUD","TIPO_REQUIRENTE"))assertEquals(1,mode(dimension,month,AL_REQ).size(),dimension);
        assertEquals(of("07",2),mode("REGION_REQUIRENTE",month,AL_REQ));assertEquals(of("VULNERACION",2),mode("TIPO_SOLICITUD",month,AL_REQ));
        assertEquals(of("NNA",2),mode("TIPO_REQUIRENTE",month,AL_REQ));
        assertEquals(of("3.1",2),mode("DERECHO",month,AL_REQ));assertEquals(of("FAMILIAR",2),mode("CONTEXTO",month,AL_REQ));
        assertEquals(of("ORIENTACION",1,"RESPUESTA_DIRECTA",1),mode("RESULTADO_PRIMERA_ATENCION",month,AL_REQ));
        // Empate forzado: el fin del acto queda en un milisegundo que aun no llega y el comando siguiente parte enseguida.
        String tie="2024-09-01";
        long open=attention(ADMIN,"UPR-REQ","PRESENCIAL","2024-09-06T15:00:00Z",Map.of("region","07","commune","Talca"));
        long end=Lifecycle.now()+40;
        db.update("UPDATE command_log SET created_ms=? WHERE result_kind='REQ' AND result_id=?",end,open);
        Workflow.lastEnd=end;
        long com=comOf(open);
        run(ADMIN,"STAT_CLASSIFY",com,stats.revision(com),Map.of("stat",statement));
        assertTrue(n(db.one("SELECT MIN(recorded_ms) AS t FROM stat_event WHERE communication_id=?",com),"t")>end,"el comando siguiente escribe despues del fin del anterior");
        assertEquals(of("PENDIENTE",1),mode("TIPO_SOLICITUD",tie,AL_REQ));assertEquals(of("VULNERACION",1),mode("TIPO_SOLICITUD",tie,VIGENTE));
        assertEquals(of("07",1),mode("REGION_REQUIRENTE",tie,AL_REQ));
        // Comandos seguidos: instantes de fin siempre crecientes y todo lo escrito despues del fin anterior.
        long previous=Workflow.lastEnd;
        for(int i=0;i<50;i++){
            long c=manual("2024-10-06T15:00:00Z",form("07"),null);
            assertTrue(n(db.one("SELECT created_ms FROM communication WHERE id=?",c),"created_ms")>previous);
            assertTrue(Workflow.lastEnd>previous);previous=Workflow.lastEnd;
        }
        // Con el reloj atrasado la espera tiene un tope y el servicio sigue.
        Workflow.lastEnd=Lifecycle.now()+60_000;
        long started=System.nanoTime();
        manual("2024-10-07T15:00:00Z",form("07"),null);
        long waited=(System.nanoTime()-started)/1_000_000;
        assertTrue(waited>=Workflow.MAX_WAIT_MS&&waited<Workflow.MAX_WAIT_MS+3_000,"espera "+waited+" ms");
        assertTrue(Workflow.lastEnd<=Lifecycle.now());
    }
    @Test void historiaSinEvidencia(){
        String month="2023-03-01";
        long com=manual("2023-03-06T15:00:00Z",form("07"),null);
        long req=reqOf(com,null);
        long before=Lifecycle.now();
        on(HEAD,"REQ_CLASSIFY","requirement",req,Map.of("complexity","ALTA","reason","Clasificacion"));
        var r=query(INST,"INGRESOS",List.of("COMPLEJIDAD"),List.of(),month(month),at(before));
        assertEquals(of("PENDIENTE",1),cells(r));assertTrue(warnings(r).contains("HISTORIA_INCOMPLETA"));
        assertEquals(Map.of("code","HISTORIA_INCOMPLETA","dimensions",List.of("COMPLEJIDAD"),"n",1L),list(r.get("warnings")).stream().filter(w->w.get("code").equals("HISTORIA_INCOMPLETA")).findFirst().orElseThrow());
        assertEquals(of("ALTA",1),mode("COMPLEJIDAD",month,at(Lifecycle.now())));
        assertEquals(of("ALTA",1),mode("COMPLEJIDAD",month,INGRESO),"un solo cambio: el primer valor");
        assertEquals(of("PENDIENTE",1),mode("COMPLEJIDAD",month,AL_REQ));
        on(HEAD,"REQ_CLASSIFY","requirement",req,Map.of("complexity","BAJA","reason","Reclasificacion"));
        assertEquals(of("PENDIENTE",1),mode("COMPLEJIDAD",month,INGRESO));assertEquals(of("BAJA",1),mode("COMPLEJIDAD",month,VIGENTE));
        // Materia de un REQ de sede: rectificada despues de la fecha.
        String april="2023-04-01";
        site("S15","15","ACTIVA");staff("cabeza.s15","DIRECCION@S15","PROFESIONAL@S15");
        long regional=com("cabeza.s15","S15","CORREO","2023-04-06T15:00:00Z",form("15"),null);
        route("cabeza.s15",regional,"S15","UPD");
        long regionalReq=assume("cabeza.s15",regional,"cabeza.s15",null);
        long then=Lifecycle.now();
        assertEquals(of("UPD",1),mode("MATERIA",april,at(then)));
        on("cabeza.s15","REQ_MATTER","requirement",regionalReq,Map.of("matter","UPR","reason","Rectifica"));
        assertEquals(of("PENDIENTE",1),mode("MATERIA",april,at(then)));assertEquals(of("UPR",1),mode("MATERIA",april,VIGENTE));
        assertEquals(of("PENDIENTE",1),mode("MATERIA",april,AL_REQ));
        // Modalidad: vale mientras no haya una version posterior de la ficha.
        String may="2023-05-01";
        long attention=attention(ADMIN,"UPR-REQ","PRESENCIAL","2023-05-06T15:00:00Z",Map.of("region","07","commune","Talca"));
        long mid=Lifecycle.now();
        assertEquals(of("GENERAL",1),mode("MODALIDAD",may,at(mid)));
        on(ADMIN,"ATTENTION_SAVE","requirement",attention,map("form",map("schema_version","1.2","modality","NNA","course","EN_ATENCION","motive","Consulta sintetica","region","07","commune","Talca"),"reason","Cambia la modalidad"));
        assertEquals(of("PENDIENTE",1),mode("MODALIDAD",may,at(mid)));assertEquals(of("NNA",1),mode("MODALIDAD",may,VIGENTE));
    }

    // --- Derechos: jerarquia y retiro -------------------------------------------------------------------------------------
    @Test void jerarquiaYRetiro(){
        String month="2023-06-01";
        long com=manual("2023-06-06T15:00:00Z",form("07"),null);
        long req=reqOf(com,null);
        rights(HEAD,"REQ",req,"DERECHO",add("3.1","3.1.01"));
        long both=Lifecycle.now();
        rights(HEAD,"REQ",req,"DERECHO",remove("3.1.01"));
        assertEquals(of("3.1",1),mode("DERECHO",month,at(both)));assertEquals(of("3.1",1),mode("DERECHO",month,VIGENTE));
        assertEquals(of("3",1),mode("DERECHO_EJE",month,at(both)));
        assertEquals(of("3.1.01",1),mode("DERECHO_SUBCATEGORIA",month,at(both)));assertEquals(of("SIN_DESGLOSE",1),mode("DERECHO_SUBCATEGORIA",month,VIGENTE));
        assertEquals(2,grand(query(INST,"CLASIFICACIONES_DERECHO",List.of(),List.of(),month(month),at(both))));
        assertEquals(of("3.1.01",1),mode("DERECHO_SUBCATEGORIA",month,INGRESO),"primer conjunto registrado");
        assertEquals(of("PENDIENTE",1),mode("DERECHO",month,AL_REQ),"clasificado despues de constituirse el REQ");
        assertEquals(1,grand(query(INST,"MENCIONES",List.of("DERECHO"),List.of(),month(month),at(both))));
        // Un listado nuevo no cambia lo registrado y la consulta avisa si conviven versiones.
        rights(HEAD,"REQ",req,"CONTEXTO",add("ESCOLAR"));
        assertFalse(warnings(query(INST,"INGRESOS",List.of("CONTEXTO"),List.of(),month(month))).contains("VERSIONES_DISTINTAS"));
        long old=n(db.one("SELECT id FROM stat_catalog_version WHERE `current_catalog`='CONTEXTO'"),"id");
        String content="{\"format\":\"siger-catalogo-1\",\"catalog\":\"CONTEXTO\",\"label\":\"Contexto 2\",\"source\":\"Prueba\",\"items\":[{\"code\":\"ESCOLAR\",\"label\":\"Escolar nuevo\"},{\"code\":\"NUEVO\",\"label\":\"Nuevo\"}]}";
        long next=run("ti","CATALOG_IMPORT",null,null,Map.of("content",content,"reason","Prueba")).id();
        run("ti","CATALOG_ACTIVATE",next,0L,Map.of("reason","Prueba"));
        try{
            rights(HEAD,"REQ",req,"CONTEXTO",add("NUEVO"));
            var r=query(INST,"INGRESOS",List.of("CONTEXTO"),List.of(),month(month));
            assertEquals(of("ESCOLAR",1,"NUEVO",1),cells(r));assertTrue(warnings(r).contains("VERSIONES_DISTINTAS"));
            assertEquals(Set.of(old,next),obj(obj(r.get("metadata")).get("catalog_versions")).get("CONTEXTO"));
        }finally{
            db.update("UPDATE stat_catalog_version SET `current_catalog`=NULL,state='RETIRADA',retired_ms=? WHERE id=?",Lifecycle.now(),next);
            db.update("UPDATE stat_catalog_version SET `current_catalog`='CONTEXTO',state='VIGENTE',retired_ms=NULL WHERE id=?",old);
        }
    }

    // --- Territorio (D32) ------------------------------------------------------------------------------------------------------
    void covered(String received,String region){manual(received,form(region),null);}
    void audit(long scope,String action,String at){db.audit(user("ti"),action,"AMBITO",scope,scope,"Prueba sintetica",ms(at));}
    void coverage(Long scope,String region,String from,String until){
        db.update("INSERT INTO territorial_coverage(scope_id,region_code,from_ms,from_by,from_basis,until_ms) VALUES(?,?,?,?,'Prueba',?)",scope,region,ms(from),user("ti"),until==null?null:ms(until));
    }
    @Test void sedeActivadaYCerrada(){
        String month="2023-07-01";
        long s06=site("S06","06","CERRADA");
        coverage(s06,"07","2020-01-01T00:00:00Z",null);
        audit(s06,"AMBITO_ACTIVADO","2023-07-10T15:00:00Z");audit(s06,"AMBITO_CERRADO","2023-07-20T15:00:00Z");
        covered("2023-07-05T15:00:00Z","07");covered("2023-07-15T15:00:00Z","07");covered("2023-07-20T15:00:00Z","07");
        assertEquals(of("S06",1,"SIN_ATENCION",2),mode("TERRITORIO_COBERTURA",month));
        assertEquals(3,grand(query(INST,"INGRESOS",List.of(),List.of(),month(month))),"la sede cerrada no oculta la historia");
        db.update("DELETE FROM territorial_coverage WHERE scope_id=?",s06);
    }
    @Test void sedeProyectada(){
        String month="2023-08-01";
        long s05=site("S05","05","PROYECTADA");
        coverage(s05,"08","2020-01-01T00:00:00Z",null);
        db.update("UPDATE org_scope SET region_code='09' WHERE code='REG-DEMO'");
        try{
            covered("2023-08-05T15:00:00Z","05");covered("2023-08-06T15:00:00Z","08");
            assertEquals(of("SIN_ATENCION",2),mode("TERRITORIO_COBERTURA",month));
        }finally{
            db.update("UPDATE org_scope SET region_code='05' WHERE code='REG-DEMO'");
            db.update("DELETE FROM territorial_coverage WHERE scope_id=?",s05);
        }
    }
    @Test void coberturaCentral(){
        String month="2023-09-01";
        coverage(null,"13","2023-09-10T15:00:00Z","2023-09-20T15:00:00Z");
        covered("2023-09-10T14:59:59.999Z","13");covered("2023-09-10T15:00:00Z","13");covered("2023-09-20T15:00:00Z","13");
        assertEquals(of("NIVEL_CENTRAL",1,"SIN_ATENCION",2),mode("TERRITORIO_COBERTURA",month));
        covered("2023-09-25T15:00:00Z",null);
        assertEquals(1,mode("TERRITORIO_COBERTURA",month).get("SIN_INFORMACION"));
    }
    @Test void formularioTerritorio(){
        String month="2023-10-01";
        formulario("UPR-REQ",Instant.parse("2023-10-05T15:00:00Z"),"07","Talca",Map.of("hechos-region","06","hechos-commune","Rancagua"));
        var nna=nna();
        ((ObjectNode)nna).put("recibidaEn","2023-10-06T15:00:00Z");
        var fields=(ObjectNode)nna.path("datos").path("campos");fields.put("nna-region","05");fields.put("nna-commune","Viña del Mar");
        flow.conserveExternal(ADMIN,scope("UPR-REQ"),sign(nna));
        assertEquals(of("Talca",1,"Viña del Mar",1),mode("COMUNA_REQUIRENTE",month));
        assertEquals(of("07",1,"05",1),sortedCells(mode("REGION_REQUIRENTE",month),"07","05"));
        assertEquals(of("06",1,"SIN_INFORMACION",1),mode("REGION_HECHOS",month));
        assertEquals(of("Rancagua",1,"SIN_INFORMACION",1),mode("COMUNA_HECHOS",month));
        // NNA afectado del detalle PERSONA; el Formulario NNA no trae personas afectadas.
        assertEquals(of("13",1,"SIN_INFORMACION",1),mode("REGION_NNA",month));
        assertEquals(of("13|07",1,"SIN_INFORMACION|05",1),cells(query(INST,"INGRESOS",List.of("REGION_NNA"),List.of("REGION_REQUIRENTE"),month(month))));
    }
    static Map<String,Long> sortedCells(Map<String,Long> cells,String... keys){var m=new LinkedHashMap<String,Long>();for(String k:keys)m.put(k,cells.get(k));return m;}

    // --- Edad y nacionalidad (D38) ---------------------------------------------------------------------------------------------
    @Test void demografiaEstadistica(){
        String month="2025-07-01";
        // Recibida a las 22:00 de Chile del 1 de julio (2 de julio en UTC): todavia tiene 10 anios.
        manual("2025-07-02T02:00:00Z",Map.of("presenter",Map.of("first_name","Persona"),"persons",List.of(Map.of("role","NNA","first_name","Una","birth_date","2014-07-02","nationality","Chile"))),null);
        manual("2025-07-03T15:00:00Z",Map.of("presenter",Map.of("first_name","Persona"),"persons",List.of(Map.of("role","NNA","first_name","Dos","age_years","8","nationality","Colombia"))),null);
        attention(ADMIN,"UPR-REQ","PRESENCIAL","2025-07-04T15:00:00Z",Map.of("schema_version","1.0","modality","NNA","people",List.of(Map.of("role","NNA","name","Tres","age","12","nationality","Peru texto libre"))));
        var age=query(INST,"INGRESOS",List.of("EDAD_NNA"),List.of(),month(month));
        assertEquals(of("8",1,"10",1,"SIN_INFORMACION",1),cells(age));
        assertEquals(of("T006-009",1,"T010-013",1,"SIN_INFORMACION",1),mode("TRAMO_EDAD_NNA",month));
        var nation=query(INST,"INGRESOS",List.of("NACIONALIDAD_NNA"),List.of("EDAD_NNA"),month(month));
        assertEquals(of("Chile",1,"Colombia",1,"SIN_INFORMACION",1),totals(nation,"rows","n"));
        assertEquals(Map.of("with_data",2L,"not_applicable",0L,"total",3L,"percent",66.67),obj(nation.get("data_coverage")).get("NACIONALIDAD_NNA"));
        assertEquals(of("CHILE",1,"EXTRANJERA",1,"SIN_INFORMACION",1),mode("GRUPO_NACIONALIDAD_NNA",month));
        // La edad del requirente solo con su tipo informado como persona natural.
        assertEquals(of("NO_APLICA",3),mode("EDAD_REQUIRENTE",month));
        String json=JsonMapper.builder().build().writeValueAsString(age)+JsonMapper.builder().build().writeValueAsString(nation);
        assertFalse(json.contains("2014-07-02")||json.toLowerCase(Locale.ROOT).contains("birth"),"la fecha de nacimiento no sale");
        // Dos NNA del mismo requerimiento: dos menciones en valores distintos y un requerimiento.
        String october="2025-10-01";
        manual("2025-10-03T15:00:00Z",Map.of("presenter",Map.of("first_name","Persona"),"persons",List.of(Map.of("role","NNA","first_name","A","age_years","8"),Map.of("role","NNA","first_name","B","age_years","8"),Map.of("role","NNA","first_name","C","age_years","5"))),null);
        var several=query(INST,"MENCIONES",List.of("EDAD_NNA"),List.of(),month(october));
        assertEquals(of("5",1,"8",2),cells(several));assertEquals(3,grand(several));assertEquals(1,grand(several,"unique"));
    }

    // --- Calidad, propuestas y alcance de los Casos (D28) -----------------------------------------------------------------------------
    @Test void calidadYPropuesta()throws Exception{
        String month="2023-11-01";
        site("S09","09","ACTIVA");staff("cabeza.s09","DIRECCION@S09","PROFESIONAL@S09");staff("rep.s09","REPORTES@S09");
        com("cabeza.s09","S09","CORREO","2023-11-05T15:00:00Z",form("09"),stat("CONSULTA_ORIENTACION",null));
        formulario("S09",Instant.parse("2023-11-06T15:00:00Z"),"09","Temuco",Map.of("motivo","vulneracion"));
        com("cabeza.s09","S09","CORREO","2023-11-07T15:00:00Z",form(null),stat("SIN_INFORMACION",null));
        var r=query("rep.s09","COMPLETITUD",List.of("ESTADO_VARIABLE"),List.of(),month(month),"filters",Map.of("VARIABLE",List.of("TIPO_SOLICITUD")),"include_proposals",true);
        assertEquals(of("INFORMADO",1,"PENDIENTE",1,"SIN_INFORMACION",1),cells(r));assertEquals(3,grand(r));
        var proposals=list(r.get("proposals")).stream().filter(p->p.get("variable").equals("TIPO_SOLICITUD")).toList();
        assertEquals(1,proposals.size());assertEquals("VULNERACION",proposals.getFirst().get("code"));assertEquals(1L,proposals.getFirst().get("n"));
        assertEquals(of("CONSULTA_ORIENTACION",1,"PENDIENTE",1,"SIN_INFORMACION",1),mode("TIPO_SOLICITUD",month),"la propuesta no es un valor");
        assertEquals(List.of(),query("rep.s09","INGRESOS",List.of("TIPO_SOLICITUD"),List.of(),month(month)).get("proposals"));
        // Con alcance institucional coincide con los indicadores de E07, por variable, estado y ambito.
        var byEngine=new TreeMap<String,Long>();
        for(var c:list(query(INST,"COMPLETITUD",List.of("VARIABLE","ESTADO_VARIABLE","AMBITO"),List.of(),days("2000-01-01","2099-12-31")).get("cells")))byEngine.put(String.join("/",keys(c.get("row"))),(Long)c.get("n"));
        var byE07=new TreeMap<String,Long>();
        for(var i:completeness.indicators(new Reports.Reach(true,Set.of(),Set.of()),StatEngine.ms(LocalDate.of(2000,1,1)),StatEngine.ms(LocalDate.of(2100,1,1))))
            byE07.put(s(i,"variable")+"/"+s(i,"stat_state")+"/"+s(db.one("SELECT code FROM org_scope WHERE id=?",n(i,"scope_id")),"code"),n(i,"amount"));
        assertEquals(byE07,byEngine);
    }
    /** Definicion C2: una cuenta de sede ve pendiente un derecho registrado solo en un Caso ajeno; el cierre no cambia. */
    @Test void completitudConCasoAjeno(){
        String month="2023-12-01";
        site("S10","10","ACTIVA");site("S11","11","ACTIVA");
        staff("cabeza.s10","DIRECCION@S10","PROFESIONAL@S10");staff("cabeza.s11","DIRECCION@S11","PROFESIONAL@S11");staff("rep.s11","REPORTES@S11");
        long com=com("cabeza.s10","S10","CORREO","2023-12-05T15:00:00Z",form("10"),stat("VULNERACION","PERSONA_NATURAL_ADULTA"));
        route("cabeza.s10",com,"S10",null);
        long req=assume("cabeza.s10",com,"cabeza.s10",null);
        long cas=on("cabeza.s10","REQ_CASE_CREATE","requirement",req,Map.of("scope_id",scope("S10"),"responsible_id",user("cabeza.s10"),"objective","Seguimiento","matter","UPR")).id();
        rights("cabeza.s10","CAS",cas,"DERECHO",add("3.1"));
        on("cabeza.s10","FLOW_REQUEST","requirement",req,Map.of("kind","TRANSFERENCIA","target_scope",scope("S11"),"reason","Traslado"));
        on("cabeza.s11","FLOW_DECIDE","flow_request",last("flow_request"),Map.of("decision","ACEPTADA","reason","Aceptada"));
        var filter=new Object[]{"filters",Map.of("VARIABLE",List.of("DERECHO"))};
        assertEquals(of("PENDIENTE",1),cells(query("rep.s11","COMPLETITUD",List.of("ESTADO_VARIABLE"),List.of(),month(month),filter)));
        assertEquals(of("INFORMADO",1),cells(query(INST,"COMPLETITUD",List.of("ESTADO_VARIABLE"),List.of(),month(month),filter)));
        assertEquals(of("PENDIENTE",1),cells(query("rep.s11","INGRESOS",List.of("ESTADO_DERECHO"),List.of(),month(month))));
        assertFalse(completeness.blockers(StatCompleteness.Closing.REQUIREMENT,com,req).stream().anyMatch(b->b.contains("derecho")),"el cierre usa el expediente completo");
    }
    /** Definicion C4: REMISION_CONCLUIDA conserva su codigo y suma al total de actuaciones. */
    @Test void remisionConcluida(){
        String month="2022-05-01";
        attention(ADMIN,"UPR-REQ","PRESENCIAL","2022-05-06T15:00:00Z",Map.of("region","07","commune","Talca"),"stat",stat("OTRO","NNA"),"resolve_now",true,"effective_at",ago(5),"result","REMISION_CONCLUIDA","reason","Remitida");
        var r=query(INST,"ACTUACIONES",List.of("TIPO_ACTUACION"),List.of(),month(month));
        assertEquals(of("REMISION_CONCLUIDA",1),cells(r));assertEquals(1,grand(r));
        assertEquals(List.of(Map.of("key","REMISION_CONCLUIDA","label","Remisión concluida")),obj(r.get("values")).get("TIPO_ACTUACION"));
        assertEquals(1,grand(query(INST,"ACTUACIONES",List.of(),List.of(),month(month),"filters",Map.of("TIPO_ACTUACION",List.of("REMISION_CONCLUIDA")))));
    }
}
