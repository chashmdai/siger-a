package cl.nexbyte.sigera;

import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import static org.junit.jupiter.api.Assertions.*;
import static cl.nexbyte.sigera.Db.*;
import static cl.nexbyte.sigera.FormularioDocs.*;

/**
 * Clasificacion estadistica de la COM (0.8.0, D28-D31 y D35, gate E05; DISENO 3.2, 4.2 y 4.3): via derivada del canal
 * con los codigos de via-ingreso-v1.json, propuestas version 1 (escenario 4) calculadas sin escribir, STAT_CLASSIFY con
 * historia sin borrado, motivo al corregir, cada estado especial, revision = eventos de la COM (concurrencia), catalogo
 * vigente obligatorio sin bloquear el ingreso, facultades de la cadena (nunca lectura, REPORTES, AUDITORIA ni TI),
 * inmutabilidad (descartada, vinculada, REQ cerrado) y RETROSPECTIVA tras la reapertura existente, bloque "stat" en los
 * comandos de ingreso y distribucion, ficha con la caracterizacion, y la misma regla en Java y en SQL (vigente y a una
 * fecha, tambien la via derivada, las propuestas y el conjunto de una variable multiple).
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_clasificacion;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.seed-demo=true","siger.data=./target/test-clasificacion-${random.uuid}","siger.scheduling=false"})
@ActiveProfiles("test")
class StatClassificationTest {
    @DynamicPropertySource static void trustedKeys(DynamicPropertyRegistry registry){FormularioDocs.trustedKeys(registry);}
    static final JsonMapper JSON=JsonMapper.builder().build();
    static final String TS="TIPO_SOLICITUD",TR="TIPO_REQUIRENTE",VIA="VIA_INGRESO";
    static final String HEAD="coordinacion.requerimientos",DIST="distribucion",ADMIN="administrativo",PROF="profesional";
    @Autowired Workflow flow;@Autowired Db db;@Autowired Access access;@Autowired Queries queries;@Autowired StatValues stats;@Autowired StatProposals proposals;

    @BeforeEach void ready(){db.update("UPDATE staff SET must_change=FALSE");}
    long scope(String code){return n(db.one("SELECT id FROM org_scope WHERE code=?",code),"id");}
    long user(String name){return n(db.one("SELECT id FROM staff WHERE username=?",name),"id");}
    long rev(String table,long id){return n(db.one("SELECT revision FROM "+table+" WHERE id=?",id),"revision");}
    Workflow.Result run(String who,String type,Long id,Long rev,Map<String,Object> data){return flow.run(who,new Workflow.Command(UUID.randomUUID(),type,id,rev,data));}
    Workflow.Result on(String who,String type,String table,long id,Map<String,Object> data){return run(who,type,id,rev(table,id),data);}
    Problem refused(Runnable r){return assertThrows(Problem.class,r::run);}
    static Map<String,Object> map(Object... pairs){var m=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]);return m;}
    static String ago(long s){return java.time.Instant.now().minusSeconds(s).toString();}
    static Map<String,Object> code(String code){return Map.of("code",code);}
    static Map<String,Object> status(String status){return Map.of("status",status);}

    // --- Datos ------------------------------------------------------------------------------------------------------
    String reference(String name)throws Exception{return Files.readString(ReferenceCatalogsTest.DIR.resolve(name)).replace("\r\n","\n");}
    long importActivate(String content){
        long v=run("ti","CATALOG_IMPORT",null,null,Map.of("content",content,"reason","Fundamento sintetico")).id();
        run("ti","CATALOG_ACTIVATE",v,n(db.one("SELECT revision FROM stat_catalog_version WHERE id=?",v),"revision"),Map.of("reason","Activacion sintetica"));
        return v;
    }
    /** Catalogos de referencia version 1 de las tres variables de la COM, vigentes (si ya lo estan, no se tocan). */
    void catalogs()throws Exception{
        for(var e:Map.of(TS,"tipo-solicitud-v1.json",TR,"tipo-requirente-v1.json",VIA,"via-ingreso-v1.json").entrySet()){
            var current=db.rows("SELECT v.id FROM stat_catalog_version v WHERE v.`current_catalog`=?",e.getKey());
            if(!current.isEmpty()&&db.count("SELECT COUNT(*) FROM stat_catalog_item WHERE version_id=?",n(current.getFirst(),"id"))==JSON.readTree(reference(e.getValue())).path("items").size())continue;
            importActivate(reference(e.getValue()));
        }
    }
    long manual(String who,String channel,Object... extra){
        var data=map("scope_id",scope("UPR-REQ"),"channel",channel,"received_at",ago(300),"subject","Ingreso sintetico","body","Relato sintetico","intake",Map.of());
        for(int i=0;i<extra.length;i+=2)data.put((String)extra[i],extra[i+1]);
        return run(who,"COM_CREATE",null,null,data).id();
    }
    long formulario(ObjectNode document){return flow.conserveExternal(ADMIN,scope("UPR-REQ"),sign(document)).id();}
    static ObjectNode adult(String motive,String organization){
        var c=cuVulneration();var campos=(ObjectNode)c.path("datos").path("campos");
        campos.put("motivo",motive);if(organization!=null)campos.put("organizacion",organization);
        return c;
    }
    static ObjectNode nna(){
        var campos=new LinkedHashMap<String,String>();
        campos.put("nna-region","07");campos.put("nna-commune","Talca");campos.put("nna-story","Relato NNA sintetico");campos.put("nna-primary-method","unsafe");
        return com("NNA",RECEIVED,campos,List.of(person("presentador-nna","PRESENTADOR",false,null,null,"primerNombre",attr("Camila",DECL,NOCONTR),"edad",attr("14",DECL,NOCONTR))),List.of(),List.of());
    }
    long classify(String who,long com,Map<String,Object> stat){return run(who,"STAT_CLASSIFY",com,stats.revision(com),Map.of("stat",stat)).id();}
    StatValues.Value value(long com,String variable){return stats.current(com,variable);}
    /** Lector con una sola funcion (sin otras), creado por SQL como en las pruebas de busqueda. */
    String account(String username,String role,Long scope){
        if(db.count("SELECT COUNT(*) FROM staff WHERE username=?",username)==0){
            long id=db.insert("INSERT INTO staff(username,name,password_hash,home_scope,must_change) VALUES(?,?,'test-non-login',?,FALSE)",username,"Cuenta sintetica "+username,scope("UGI"));
            db.update("INSERT INTO role_assignment(user_id,role_code,scope_id,valid_from,basis) VALUES(?,?,?,0,'PRUEBA AUTOMATICA')",id,role,scope);
        }
        return username;
    }
    /** Huella de los originales del Formulario y de su proyeccion (nunca se escriben). */
    Map<String,Object> originals(){
        var out=new TreeMap<String,Object>();
        for(String t:List.of("communication_original","external_person","external_attribute","external_field"))out.put(t,db.rows("SELECT * FROM "+t+" ORDER BY 1").toString());
        return out;
    }

    // --- Via derivada y archivos de referencia -------------------------------------------------------------------------
    @Test void viaDerivadaConLosCodigosDelArchivoDeReferencia()throws Exception{
        var items=JSON.readTree(reference("via-ingreso-v1.json")).path("items");
        var fromFile=new LinkedHashMap<String,String>();
        for(JsonNode i:items){
            // E06 (decision del responsable del 2026-11-25): las agrupaciones son items padre no seleccionables, sin canal.
            if(i.has("selectable")&&!i.path("selectable").booleanValue())continue;
            String ref=i.path("source_ref").asString();
            assertTrue(ref.startsWith("D31; canal "),ref);
            fromFile.put(ref.substring("D31; canal ".length()),i.path("code").asString());
        }
        fromFile.forEach((channel,code)->assertEquals(code,StatValues.CHANNEL_VIA.get(channel),"via del canal "+channel+" = via-ingreso-v1.json"));
        assertEquals(new TreeSet<>(fromFile.keySet()),new TreeSet<>(StatValues.CHANNEL_VIA.keySet()),"todos los canales (manuales y del Formulario) tienen su via");
        var manual=new TreeSet<>(Arrays.asList(Lifecycle.CHANNELS));
        assertTrue(manual.containsAll(List.of("REDES_SOCIALES","INTEGRANTE_DEFENSORIA")),"canales nuevos de D31");
        manual.add(StatProposals.ADULT);manual.add(StatProposals.NNA);
        assertEquals(manual,new TreeSet<>(StatValues.CHANNEL_VIA.keySet()));
    }

    // --- Sin catalogo vigente: nada bloquea el ingreso ---------------------------------------------------------------
    @Test void sinCatalogoVigenteElIngresoNoSeBloquea(){
        for(String t:List.of("stat_event","stat_catalog_mapping","stat_catalog_item","stat_catalog_version"))db.update("DELETE FROM "+t);
        long com=manual(ADMIN,"TELEFONO");
        for(String v:StatValues.SINGLE)assertEquals(StatValues.PENDING,value(com,v).state(),v);
        assertEquals(Map.of(),proposals.of(formulario(adult("vulneracion",null))),"sin catalogo vigente no hay propuesta");
        // Un codigo o un estado especial exigen el catalogo vigente.
        var p=refused(()->manual(ADMIN,"TELEFONO","stat",Map.of(TS,status("SIN_INFORMACION"))));
        assertEquals(409,p.status);assertTrue(p.getMessage().contains("no hay un listado vigente"),p.getMessage());
        assertEquals(409,refused(()->classify(DIST,com,Map.of(TS,code("VULNERACION")))).status);
        assertEquals(0,db.count("SELECT COUNT(*) FROM stat_event"));
        // El canal nuevo se registra; sin catalogo la via queda pendiente.
        long social=manual(ADMIN,"REDES_SOCIALES");
        assertEquals("REDES_SOCIALES",s(db.one("SELECT channel FROM communication WHERE id=?",social),"channel"));
    }

    // --- Escenario 4: propuestas y via derivada, sin escribir ---------------------------------------------------------
    @Test void escenario4PropuestasYViaCalculadasSinEscribir()throws Exception{
        catalogs();
        long vulneration=formulario(adult("vulneracion",null)),consultation=formulario(adult("consulta",null));
        long organization=formulario(adult("promocion","si")),promotion=formulario(adult("promocion","no")),nna=formulario(nna());
        long direct=n(db.one("SELECT communication_id FROM requirement WHERE id=?",run(ADMIN,"ATTENTION_CREATE",null,null,Map.of("scope_id",scope("UPR-REQ"),"channel","PRESENCIAL","received_at",ago(60),
            "form",Map.of("schema_version","1.0","modality","GENERAL","course","EN_ATENCION","motive","Consulta sintetica"))).id()),"communication_id");
        long manual=manual(ADMIN,"OFICIO");
        var before=originals();long events=db.count("SELECT COUNT(*) FROM stat_event");
        assertEquals(Map.of(TS,"VULNERACION",TR,"PERSONA_NATURAL_ADULTA"),proposals.of(vulneration));
        assertEquals(Map.of(TS,"CONSULTA_ORIENTACION",TR,"PERSONA_NATURAL_ADULTA"),proposals.of(consultation));
        assertEquals(Map.of(TS,"PROMOCION",TR,"PERSONA_JURIDICA_ORGANISMO"),proposals.of(organization));
        assertEquals(Map.of(TS,"PROMOCION",TR,"PERSONA_NATURAL_ADULTA"),proposals.of(promotion));
        assertEquals(Map.of(TR,"NNA"),proposals.of(nna),"NNA: sin propuesta de tipo de solicitud y con requirente NNA");
        assertEquals(Map.of(),proposals.of(direct));assertEquals(Map.of(),proposals.of(manual));
        // La propuesta no cuenta como informado; la via es la del canal (sistema).
        for(long com:List.of(vulneration,nna)){
            assertEquals(StatValues.PENDING,value(com,TS).state());assertEquals(StatValues.PENDING,value(com,TR).state());
            assertEquals(StatValues.INFORMED,value(com,VIA).state());assertEquals(StatValues.SYSTEM,value(com,VIA).origin());assertNull(value(com,VIA).event());
        }
        assertEquals("FORMULARIO_PUBLICO_ADULTO",value(vulneration,VIA).code());assertEquals("FORMULARIO_PUBLICO_NNA",value(nna,VIA).code());
        assertEquals("PRESENCIAL",value(direct,VIA).code());assertEquals("OFICIO",value(manual,VIA).code());
        // La ficha muestra la propuesta aparte; calcular y mostrar no escribe nada.
        var detail=queries.detail(access.actor(DIST),"COM",vulneration);
        var variables=variables(detail);
        assertEquals(StatValues.PENDING,variables.get(TS).get("state"));
        @SuppressWarnings("unchecked") var proposal=(Map<String,Object>)variables.get(TS).get("proposal");
        assertEquals("VULNERACION",proposal.get("code"));assertEquals(StatProposals.RULE,proposal.get("rule"));
        assertEquals(before,originals(),"communication_original y external_* identicos");
        assertEquals(events,db.count("SELECT COUNT(*) FROM stat_event"),"la via y las propuestas no escriben eventos");
    }

    // --- STAT_CLASSIFY: historia, motivo, estados especiales --------------------------------------------------------
    @Test void clasificarCorregirConMotivoYSinBorrarHistoria()throws Exception{
        catalogs();
        long com=manual(ADMIN,"TELEFONO");
        long audits=db.count("SELECT COUNT(*) FROM audit_event WHERE action LIKE 'CLASIFICACION_%'");
        classify(DIST,com,Map.of(TS,code("VULNERACION"),TR,status("NO_DECLARADO")));
        assertEquals(StatValues.INFORMED,value(com,TS).state());assertEquals("VULNERACION",value(com,TS).code());assertEquals(StatValues.OFFICIAL,value(com,TS).origin());
        assertEquals("NO_DECLARADO",value(com,TR).state());assertNull(value(com,TR).code());
        var note=s(db.one("SELECT note FROM audit_event WHERE action=? AND resource_id=? ORDER BY id DESC LIMIT 1",StatValues.REGISTERED,com),"note");
        assertEquals("Variables: TIPO_SOLICITUD, TIPO_REQUIRENTE.",note,"auditoria solo con variables");
        // Corregir un valor informado exige motivo; sin motivo no se escribe nada.
        long events=stats.revision(com);
        var p=refused(()->classify(DIST,com,Map.of(TS,code("CONSULTA_ORIENTACION"))));
        assertEquals(400,p.status);assertTrue(p.getMessage().contains("motivo"),p.getMessage());
        assertEquals(400,refused(()->classify(DIST,com,Map.of(TS,status("SIN_INFORMACION")))).status,"pasar de informado a un estado especial tambien corrige");
        assertEquals(events,stats.revision(com));
        String reason="Motivo con datos de Ana Perez, RUN 12.345.678-5";
        classify(DIST,com,Map.of(TS,code("CONSULTA_ORIENTACION"),"reason",reason));
        assertEquals("CONSULTA_ORIENTACION",value(com,TS).code());
        var corrected=s(db.one("SELECT note FROM audit_event WHERE action=? AND resource_id=?",StatValues.CORRECTED,com),"note");
        assertEquals("Variables: TIPO_SOLICITUD.",corrected);
        assertEquals(0,db.count("SELECT COUNT(*) FROM audit_event WHERE note LIKE '%Ana Perez%' OR note LIKE '%CONSULTA_ORIENTACION%' OR note LIKE '%VULNERACION%'"),"ni motivo ni codigos en la auditoria");
        assertEquals(audits+2,db.count("SELECT COUNT(*) FROM audit_event WHERE action LIKE 'CLASIFICACION_%'"));
        // Historia sin borrado: ambos SET siguen en la tabla, con el motivo en el evento.
        assertEquals(List.of("VULNERACION","CONSULTA_ORIENTACION"),db.rows("SELECT code FROM stat_event WHERE communication_id=? AND variable=? ORDER BY id",com,TS).stream().map(r->s(r,"code")).toList());
        assertEquals(reason,s(db.one("SELECT basis FROM stat_event WHERE communication_id=? AND variable=? AND code='CONSULTA_ORIENTACION'",com,TS),"basis"));
        // Sin cambios: 409 y nada escrito.
        events=stats.revision(com);
        assertEquals(409,refused(()->classify(DIST,com,Map.of(TS,code("CONSULTA_ORIENTACION")))).status);
        assertEquals(events,stats.revision(com));
        // Cada estado especial; PENDIENTE no se fija; codigo ajeno al listado y variable desconocida se rechazan.
        for(String special:StatValues.SPECIAL){classify(DIST,com,Map.of(TR,status(special)));assertEquals(special,value(com,TR).state());}
        assertEquals(400,refused(()->classify(DIST,com,Map.of(TR,status("PENDIENTE")))).status);
        assertEquals(409,refused(()->classify(DIST,com,Map.of(TR,code("INEXISTENTE")))).status);
        assertEquals(400,refused(()->classify(DIST,com,Map.of("DERECHO",code("3.1")))).status,"variables multiples: E06");
        assertEquals(400,refused(()->classify(DIST,com,Map.of(TR,Map.of("code","NNA","status","NO_APLICA")))).status);
        assertEquals(400,refused(()->run(DIST,"STAT_CLASSIFY",com,stats.revision(com),Map.of("stat",Map.of(TR,code("NNA")),"origin","SISTEMA"))).status,"el cliente no fija origen");
        assertEquals(400,refused(()->classify(DIST,com,Map.of(TR,Map.of("code","NNA","origin","RETROSPECTIVA")))).status);
        // La via derivada es un valor informado: corregirla exige motivo y no cambia el canal.
        assertEquals(400,refused(()->classify(DIST,com,Map.of(VIA,code("CORREO")))).status);
        classify(DIST,com,Map.of(VIA,code("CORREO"),"reason","Llego por correo"));
        assertEquals("CORREO",value(com,VIA).code());assertEquals(StatValues.OFFICIAL,value(com,VIA).origin());
        assertEquals("TELEFONO",s(db.one("SELECT channel FROM communication WHERE id=?",com),"channel"),"el canal original no cambia");
    }

    // --- Revision: dos clasificaciones con la misma revision -------------------------------------------------------
    @Test void dosClasificacionesConLaMismaRevisionDejanSoloLaPrimera()throws Exception{
        catalogs();
        long com=manual(ADMIN,"TELEFONO");
        long revision=stats.revision(com);
        assertEquals(0,revision);
        var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            var futures=new ArrayList<Future<Object>>();
            for(String code:List.of("VULNERACION","PROMOCION"))futures.add(pool.submit(()->{
                start.await();
                try{return run(DIST,"STAT_CLASSIFY",com,revision,Map.of("stat",Map.of(TS,code(code))));}catch(Problem p){return p;}
            }));
            start.countDown();
            var results=new ArrayList<Object>();for(var f:futures)results.add(f.get(30,TimeUnit.SECONDS));
            assertEquals(1,results.stream().filter(r->r instanceof Workflow.Result).count(),results.toString());
            var rejected=(Problem)results.stream().filter(r->r instanceof Problem).findFirst().orElseThrow();
            assertEquals(409,rejected.status);
        }finally{pool.shutdownNow();}
        assertEquals(1,db.count("SELECT COUNT(*) FROM stat_event WHERE communication_id=?",com),"solo la primera");
        // Una revision anterior tambien se rechaza.
        assertEquals(409,refused(()->run(DIST,"STAT_CLASSIFY",com,0L,Map.of("stat",Map.of(TR,code("NNA"))))).status);
        assertEquals(409,refused(()->run(DIST,"STAT_CLASSIFY",com,null,Map.of("stat",Map.of(TR,code("NNA"))))).status);
    }

    // --- Facultades: quien hoy actua sobre la cadena; nunca lectura, REPORTES, AUDITORIA ni TI -----------------------
    @Test void facultadesDeLaCadenaYCuentasSinEscritura()throws Exception{
        catalogs();
        long upr=scope("UPR-REQ");
        String reader=account("lector.institucional.e05",Roles.CONSULTA_INSTITUCIONAL,null),lectura=account("lectura.e05","LECTURA",upr);
        String reports=account("reportes.e05","REPORTES",null),audit=account("auditoria.e05","AUDITORIA",null);
        long com=manual(ADMIN,"TELEFONO");
        // NUEVA: distribucion si; quien registro, la jefatura del origen sin funcion de distribuir y los lectores no.
        for(String who:List.of(ADMIN,reader,lectura,reports,audit,"ti"))
            assertEquals(403,refused(()->classify(who,com,Map.of(TS,code("VULNERACION")))).status,who);
        classify(DIST,com,Map.of(TS,code("VULNERACION")));
        // SOL pendiente: su receptor si; distribucion ya no.
        long sol=on(DIST,"COM_ROUTE","communication",com,Map.of("scope_id",upr,"reason","Clasificacion sintetica")).id();
        assertEquals(403,refused(()->classify(DIST,com,Map.of(TR,code("NNA")))).status);
        classify(HEAD,com,Map.of(TR,code("NNA")));
        // SOL devuelta: distribucion la redistribuye y clasifica.
        on(HEAD,"SOL_RETURN","solicitation",sol,Map.of("reason","Devuelta sintetica"));
        classify(DIST,com,Map.of(TR,status("SIN_INFORMACION"),"reason","Correccion sintetica"));
        on(DIST,"SOL_ROUTE","solicitation",sol,Map.of("scope_id",upr,"reason","Redistribucion sintetica"));
        // REQ abierto: jefatura principal y encargado si; el lector institucional y TI no.
        on(HEAD,"SOL_ASSUME","solicitation",sol,Map.of("responsible_id",user(PROF),"reason","Asume"));
        classify(PROF,com,Map.of(TR,code("PERSONA_NATURAL_ADULTA"),"reason","Correccion del encargado"));
        classify(HEAD,com,Map.of(TR,code("NNA"),"reason","Correccion de la jefatura"));
        for(String who:List.of(reader,lectura,reports,audit,"ti",DIST))
            assertEquals(403,refused(()->classify(who,com,Map.of(TR,code("PERSONA_NATURAL_ADULTA"),"reason","Intento"))).status,who);
        assertTrue(access.statWritable(access.actor(PROF),db.one("SELECT * FROM communication WHERE id=?",com)));
        assertFalse(access.statWritable(access.actor(reader),db.one("SELECT * FROM communication WHERE id=?",com)));
    }

    // --- Inmutabilidad (D35) y RETROSPECTIVA con la reapertura existente (DI-07) --------------------------------------
    @Test void registroCerradoRechazaYTrasLaReaperturaEsRetrospectiva()throws Exception{
        catalogs();
        long upr=scope("UPR-REQ");
        long com=manual(HEAD,"TELEFONO");
        long req=on(HEAD,"COM_ASSUME","communication",com,Map.of("responsible_id",user(PROF),"reason","Gestion propia")).id();
        classify(HEAD,com,Map.of(TS,code("VULNERACION")));
        assertEquals(StatValues.OFFICIAL,s(db.one("SELECT origin FROM stat_event WHERE communication_id=? ORDER BY id DESC LIMIT 1",com),"origin"),"REQ sin cierre anterior");
        on(HEAD,"REQ_CLOSE","requirement",req,Map.of("reason","Cierre sintetico","stat",Map.of(TR,status("SIN_INFORMACION"))));
        long events=stats.revision(com);
        var p=refused(()->classify(HEAD,com,Map.of(TR,code("NNA"))));
        assertEquals(409,p.status,"sin excepcion de funcion");assertTrue(p.getMessage().contains("reapertura formal"),p.getMessage());
        assertEquals(events,stats.revision(com));
        // Reapertura formal existente (REOPEN_REQUEST + REOPEN_DECIDE), sin modalidades nuevas.
        on(HEAD,"REOPEN_REQUEST","requirement",req,Map.of("reason","Clasificacion retrospectiva"));
        long reopening=n(db.one("SELECT id FROM reopening WHERE requirement_id=?",req),"id");
        on(HEAD,"REOPEN_DECIDE","reopening",reopening,Map.of("decision","ACEPTADA","reason","Aceptada"));
        classify(HEAD,com,Map.of(TR,code("NNA"),"reason","Antecedentes nuevos"));
        var e=db.one("SELECT origin,reopening_id FROM stat_event WHERE communication_id=? ORDER BY id DESC LIMIT 1",com);
        assertEquals(StatValues.RETROSPECTIVE,s(e,"origin"));assertEquals(reopening,n(e,"reopening_id"));
        assertTrue(s(db.one("SELECT note FROM audit_event WHERE action=? AND resource_id=? ORDER BY id DESC LIMIT 1",StatValues.REGISTERED,com),"note").endsWith("Retrospectiva."));
        // Atencion resuelta en el acto: REQ cerrado en el mismo comando, COM ATENDIDA congelada.
        long immediate=n(db.one("SELECT communication_id FROM requirement WHERE id=?",run(ADMIN,"COM_IMMEDIATE",null,null,map("scope_id",upr,"channel","PRESENCIAL","received_at",ago(600),
            "subject","Orientacion","body","Relato sintetico","intake",Map.of(),"reason","Orientacion entregada","effective_at",ago(300),"result","ORIENTACION",
                "stat",Map.of(TS,code("CONSULTA_ORIENTACION"),TR,status("SIN_INFORMACION")))).id()),"communication_id");
        assertEquals(409,refused(()->classify(ADMIN,immediate,Map.of(TS,code("CONSULTA_ORIENTACION")))).status);
        // COM vinculada como antecedente: 409; con el vinculo devuelto vuelve a NUEVA y se clasifica.
        long child=manual(ADMIN,"CORREO");
        on(DIST,"COM_LINK","communication",child,Map.of("folio",s(db.one("SELECT folio FROM requirement WHERE id=?",req),"folio"),"reason","Posible antecedente"));
        assertEquals(409,refused(()->classify(DIST,child,Map.of(TS,code("VULNERACION")))).status);
        long link=n(db.one("SELECT id FROM communication_link WHERE communication_id=?",child),"id");
        on(HEAD,"LINK_DECIDE","communication_link",link,Map.of("decision","RECHAZADA","reason","Asunto distinto"));
        on(HEAD,"LINK_RETURN","communication_link",link,Map.of("reason","Vuelve a clasificacion"));
        classify(DIST,child,Map.of(TS,code("VULNERACION")));
        // COM descartada: queda como se registro al descartarla.
        long invalid=manual(ADMIN,"TELEFONO");
        long sol=on(DIST,"COM_ROUTE","communication",invalid,Map.of("scope_id",upr,"reason","Clasificacion sintetica")).id();
        on(HEAD,"SOL_DISCARD","solicitation",sol,Map.of("disposition","INVALIDA","reason","Sin materia"));
        assertEquals(409,refused(()->classify(DIST,invalid,Map.of(TS,code("OTRO")))).status);
    }

    // --- Equivalencia Java/SQL (vigente y a una fecha; via derivada, propuestas y conjunto) ----------------------------
    @Test void mismaReglaEnJavaYEnSql()throws Exception{
        catalogs();
        long t0=Lifecycle.now()-1;
        long adultCom=formulario(adult("vulneracion",null)),nnaCom=formulario(nna()),orgCom=formulario(adult("promocion","si"));
        long plain=manual(ADMIN,"TELEFONO"),social=manual(ADMIN,"REDES_SOCIALES"),member=manual(ADMIN,"INTEGRANTE_DEFENSORIA");
        Thread.sleep(5);long t1=Lifecycle.now();Thread.sleep(5);
        classify(DIST,plain,Map.of(TS,code("VULNERACION"),TR,status("NO_DECLARADO")));
        Thread.sleep(5);long t2=Lifecycle.now();Thread.sleep(5);
        classify(DIST,plain,Map.of(TS,code("PROMOCION"),VIA,code("CORREO"),"reason","Correccion sintetica"));
        classify(DIST,adultCom,Map.of(TR,code("PERSONA_NATURAL_ADULTA")));
        Thread.sleep(5);long t3=Lifecycle.now();Thread.sleep(5);
        // Nueva version de VIA_INGRESO sin TELEFONO: la via derivada de TELEFONO queda pendiente desde ahora, no antes.
        var via=(ObjectNode)JSON.readTree(reference("via-ingreso-v1.json"));
        var items=via.withArrayProperty("items");for(int i=items.size()-1;i>=0;i--)if(items.get(i).path("code").asString().equals("TELEFONO"))items.remove(i);
        via.put("label","Via sin telefono, sintetica");
        importActivate(JSON.writeValueAsString(via));
        long telephone=manual(ADMIN,"TELEFONO");
        Thread.sleep(5);long t4=Lifecycle.now();
        var coms=List.of(adultCom,nnaCom,orgCom,plain,social,member,telephone);
        for(Long at:Arrays.asList(null,t0,t1,t2,t3,t4))for(long com:coms)for(String variable:StatValues.SINGLE){
            var java=at==null?stats.current(com,variable):stats.at(com,variable,at);
            var sql=db.one("SELECT "+StatValues.stateSql("c",variable,at)+" AS st,"+StatValues.codeSql("c",variable,at)+" AS cd,"+StatValues.versionSql("c",variable,at)+" AS vr,"
                +StatValues.originSql("c",variable,at)+" AS og FROM communication c WHERE c.id=?",com);
            String where="COM "+com+" "+variable+" en "+at;
            assertEquals(java.state(),s(sql,"st"),where);assertEquals(java.code(),sql.get("cd")==null?null:s(sql,"cd"),where);
            assertEquals(java.version(),nullableId(sql,"vr"),where);assertEquals(java.origin(),sql.get("og")==null?null:s(sql,"og"),where);
        }
        // Casos esperados (no solo iguales entre si).
        assertEquals("TELEFONO",stats.at(plain,VIA,t1).code());assertEquals("CORREO",stats.current(plain,VIA).code());
        assertEquals(StatValues.PENDING,stats.current(telephone,VIA).state(),"sin TELEFONO en la via vigente");
        assertEquals(StatValues.INFORMED,stats.at(adultCom,VIA,t3).state());assertEquals(StatValues.PENDING,stats.at(plain,VIA,t0).state(),"antes de registrarse la COM");
        assertEquals("VULNERACION",stats.at(plain,TS,t2).code());assertEquals(StatValues.PENDING,stats.at(plain,TS,t1).state());
        assertEquals("INTEGRANTE_DEFENSORIA",stats.current(member,VIA).code());assertEquals("REDES_SOCIALES",stats.current(social,VIA).code());
        // Propuestas: Java = SQL.
        for(long com:coms)for(String variable:List.of(TS,TR))
            assertEquals(proposals.of(com).get(variable),db.one("SELECT "+StatProposals.sqlCode("c",variable)+" AS p FROM communication c WHERE c.id=?",com).get("p")==null?null:
                s(db.one("SELECT "+StatProposals.sqlCode("c",variable)+" AS p FROM communication c WHERE c.id=?",com),"p"),"propuesta "+com+" "+variable);
        // Conjunto de una variable multiple (eventos por SQL; los comandos llegan en E06).
        long author=user(HEAD),base=Lifecycle.now();
        String[][] script={{"ADD","3.1"},{"ADD","3.2"},{"REMOVE","3.1"},{"STATUS",null},{"ADD","3.1"},{"REMOVE","3.2"},{"REMOVE","3.1"}};
        var marks=new ArrayList<Long>();
        for(int i=0;i<script.length;i++){
            db.update("INSERT INTO stat_event(communication_id,record_type,record_id,variable,event,status,code,origin,author_id,recorded_ms) VALUES(?,?,?,?,?,?,?,?,?,?)",
                plain,"REQ",plain,"DERECHO",script[i][0],script[i][0].equals("STATUS")?"SIN_INFORMACION":"INFORMADO",script[i][1],"FUNCIONARIO",author,base+i*10);
            marks.add(base+i*10);
        }
        var expectedSets=List.of(Set.of("3.1"),Set.of("3.1","3.2"),Set.of("3.2"),Set.of("3.2"),Set.of("3.1","3.2"),Set.of("3.1"),Set.<String>of());
        var expectedStates=List.of("INFORMADO","INFORMADO","INFORMADO","INFORMADO","INFORMADO","INFORMADO","SIN_INFORMACION");
        for(int i=0;i<marks.size();i++){
            long at=marks.get(i);
            assertEquals(expectedSets.get(i),stats.set("REQ",plain,"DERECHO",at),"conjunto "+i);
            assertEquals(expectedSets.get(i),new TreeSet<>(db.rows(StatValues.setSql("REQ",String.valueOf(plain),"DERECHO",at)).stream().map(r->s(r,"code")).toList()),"SQL conjunto "+i);
            assertEquals(expectedStates.get(i),stats.setState("REQ",plain,"DERECHO",at),"estado "+i);
            assertEquals(expectedStates.get(i),s(db.one("SELECT "+StatValues.setStateSql("REQ",String.valueOf(plain),"DERECHO",at)+" AS st FROM communication WHERE id=?",plain),"st"),"SQL estado "+i);
        }
        assertEquals(StatValues.PENDING,stats.setState("REQ",plain,"DERECHO",base-1));
        assertEquals(StatValues.PENDING,s(db.one("SELECT "+StatValues.setStateSql("REQ",String.valueOf(plain),"DERECHO",base-1)+" AS st FROM communication WHERE id=?",plain),"st"));
        db.update("DELETE FROM stat_event WHERE record_type='REQ' AND variable='DERECHO'");
        // Se restituye la via de referencia (otra version propuesta identica a la retirada, E01).
        importActivate(reference("via-ingreso-v1.json"));
    }

    // --- Bloque "stat" en los comandos de ingreso y distribucion -----------------------------------------------------
    @Test void bloqueStatEnLosSieteComandos()throws Exception{
        catalogs();
        long upr=scope("UPR-REQ");
        // COM_CREATE: quien registra clasifica en el mismo acto (sin facultad de distribuir).
        long created=manual(ADMIN,"TELEFONO","stat",Map.of(TS,code("VULNERACION"),TR,code("NNA")));
        assertEquals("VULNERACION",value(created,TS).code());assertEquals("NNA",value(created,TR).code());
        // La via derivada esta informada: corregirla en el bloque sin motivo rechaza todo el comando, sin escrituras.
        long coms=db.count("SELECT COUNT(*) FROM communication");
        assertEquals(400,refused(()->manual(ADMIN,"TELEFONO","stat",Map.of(VIA,code("CORREO")))).status);
        assertEquals(coms,db.count("SELECT COUNT(*) FROM communication"));
        long viaFixed=manual(ADMIN,"TELEFONO","stat",Map.of(VIA,code("CORREO"),"reason","Se recibio por correo"));
        assertEquals("CORREO",value(viaFixed,VIA).code());
        // COM_IMMEDIATE: se clasifica antes del cierre en el acto.
        long req=run(ADMIN,"COM_IMMEDIATE",null,null,map("scope_id",upr,"channel","PRESENCIAL","received_at",ago(600),"subject","Orientacion","body","Relato sintetico","intake",Map.of(),
            "reason","Orientacion entregada","effective_at",ago(300),"result","ORIENTACION","stat",Map.of(TS,code("CONSULTA_ORIENTACION"),TR,status("SIN_INFORMACION")))).id();
        long immediate=n(db.one("SELECT communication_id FROM requirement WHERE id=?",req),"communication_id");
        assertEquals("CONSULTA_ORIENTACION",value(immediate,TS).code());assertEquals("CERRADO",s(db.one("SELECT state FROM requirement WHERE id=?",req),"state"));
        // ATTENTION_CREATE (con resolucion en el acto).
        long attention=run(ADMIN,"ATTENTION_CREATE",null,null,map("scope_id",upr,"channel","PRESENCIAL","received_at",ago(60),
            "form",Map.of("schema_version","1.0","modality","GENERAL","course","EN_ATENCION","motive","Consulta sintetica"),"stat",Map.of(TR,code("PERSONA_NATURAL_ADULTA"),TS,code("CONSULTA_ORIENTACION")),
            "resolve_now",true,"result","ORIENTACION","reason","Orientacion","effective_at",ago(30))).id();
        long attentionCom=n(db.one("SELECT communication_id FROM requirement WHERE id=?",attention),"communication_id");
        assertEquals("PERSONA_NATURAL_ADULTA",value(attentionCom,TR).code());
        // COM_ROUTE y SOL_ASSUME.
        long routed=manual(ADMIN,"CORREO");
        long sol=on(DIST,"COM_ROUTE","communication",routed,Map.of("scope_id",upr,"reason","Clasificacion sintetica","stat",Map.of(TS,code("PROMOCION")))).id();
        on(HEAD,"SOL_ASSUME","solicitation",sol,Map.of("responsible_id",user(PROF),"reason","Asume","stat",Map.of(TR,status("NO_APLICA"))));
        assertEquals("PROMOCION",value(routed,TS).code());assertEquals("NO_APLICA",value(routed,TR).state());
        // COM_ASSUME.
        long own=manual(HEAD,"OFICIO");
        on(HEAD,"COM_ASSUME","communication",own,Map.of("responsible_id",user(PROF),"reason","Gestion propia","stat",Map.of(TS,code("OTRO"))));
        assertEquals("OTRO",value(own,TS).code());
        // SOL_DISCARD DUPLICADA: la clasificacion se registra y luego queda congelada.
        long duplicate=manual(ADMIN,"TELEFONO");
        long dsol=on(DIST,"COM_ROUTE","communication",duplicate,Map.of("scope_id",upr,"reason","Clasificacion sintetica")).id();
        on(HEAD,"SOL_DISCARD","solicitation",dsol,Map.of("disposition","DUPLICADA","reason","Ya ingresada","stat",Map.of(TS,code("VULNERACION"),TR,code("NNA"))));
        assertEquals("DUPLICADA",s(db.one("SELECT state FROM communication WHERE id=?",duplicate),"state"));
        assertEquals("VULNERACION",value(duplicate,TS).code());
        assertEquals(409,refused(()->classify(DIST,duplicate,Map.of(TS,code("OTRO"),"reason","Intento"))).status);
        // Cada evento del bloque lleva el origen del servidor y el autor del comando.
        assertEquals(Set.of(StatValues.OFFICIAL),new HashSet<>(db.rows("SELECT DISTINCT origin FROM stat_event WHERE communication_id IN (?,?,?,?,?)",created,immediate,routed,own,duplicate).stream().map(r->s(r,"origin")).toList()));
        assertEquals(user(HEAD),n(db.one("SELECT author_id FROM stat_event WHERE communication_id=? AND variable=?",routed,TR),"author_id"));
    }

    // --- Ficha de COM y de REQ con la caracterizacion -------------------------------------------------------------
    @Test void fichaConCaracterizacionPropuestaEHistorial()throws Exception{
        catalogs();
        long upr=scope("UPR-REQ");
        long com=formulario(adult("vulneracion",null));
        var com0=variables(queries.detail(access.actor(DIST),"COM",com));
        assertEquals(StatValues.PENDING,com0.get(TS).get("state"));assertNull(com0.get(TS).get("code"));
        assertEquals("Vulneración de derechos",((Map<?,?>)com0.get(TS).get("proposal")).get("label"));
        assertEquals(StatValues.INFORMED,com0.get(VIA).get("state"));assertEquals(StatValues.SYSTEM,com0.get(VIA).get("origin"));
        assertEquals("Formulario Público adulto",com0.get(VIA).get("value_label"));
        classify(DIST,com,Map.of(TS,code("VULNERACION")));
        classify(DIST,com,Map.of(TS,code("CONSULTA_ORIENTACION"),"reason","Motivo sintetico de la correccion"));
        var detail=queries.detail(access.actor(DIST),"COM",com);
        @SuppressWarnings("unchecked") var characterization=(Map<String,Object>)detail.get("characterization");
        assertEquals(true,characterization.get("can_classify"));assertEquals(2L,((Number)characterization.get("revision")).longValue());assertNull(characterization.get("blocked"));
        @SuppressWarnings("unchecked") var history=(List<Map<String,Object>>)variables(detail).get(TS).get("history");
        assertEquals(2,history.size());assertEquals("Motivo sintetico de la correccion",history.getFirst().get("basis"));assertEquals("Consulta u orientación",history.getFirst().get("value_label"));
        // REQ: la caracterizacion de su COM; un lector del REQ sin lectura de la COM ni del registro no ve el motivo.
        long sol=on(DIST,"COM_ROUTE","communication",com,Map.of("scope_id",upr,"reason","Clasificacion sintetica")).id();
        long req=on(HEAD,"SOL_ASSUME","solicitation",sol,Map.of("responsible_id",user(PROF),"reason","Asume")).id();
        String lectura=account("lectura.ficha.e05","LECTURA",upr);
        var reqDetail=queries.detail(access.actor(lectura),"REQ",req);
        @SuppressWarnings("unchecked") var reqHistory=(List<Map<String,Object>>)variables(reqDetail).get(TS).get("history");
        assertFalse(reqHistory.getFirst().containsKey("basis"),"sin el motivo");assertEquals("CONSULTA_ORIENTACION",reqHistory.getFirst().get("code"));
        @SuppressWarnings("unchecked") var readerChar=(Map<String,Object>)reqDetail.get("characterization");
        assertEquals(false,readerChar.get("can_classify"));
        var headDetail=queries.detail(access.actor(HEAD),"REQ",req);
        @SuppressWarnings("unchecked") var headChar=(Map<String,Object>)headDetail.get("characterization");
        assertEquals(true,headChar.get("can_classify"));
        @SuppressWarnings("unchecked") var headHistory=(List<Map<String,Object>>)variables(headDetail).get(TS).get("history");
        assertEquals("Motivo sintetico de la correccion",headHistory.getFirst().get("basis"));
        // Cerrado: requiere reapertura formal, sin boton.
        on(HEAD,"REQ_CLOSE","requirement",req,Map.of("reason","Cierre sintetico","stat",Map.of(TR,status("SIN_INFORMACION"))));
        @SuppressWarnings("unchecked") var closed=(Map<String,Object>)queries.detail(access.actor(HEAD),"REQ",req).get("characterization");
        assertEquals(false,closed.get("can_classify"));assertTrue(s(closed,"blocked").contains("reapertura formal"));
    }

    @SuppressWarnings("unchecked")
    static Map<String,Map<String,Object>> variables(Map<String,Object> detail){
        var c=(Map<String,Object>)detail.get("characterization");
        var out=new LinkedHashMap<String,Map<String,Object>>();
        for(var v:(List<Map<String,Object>>)c.get("variables"))out.put(s(v,"variable"),v);
        return out;
    }
}
