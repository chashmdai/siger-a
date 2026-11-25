package cl.nexbyte.sigera;

import java.nio.file.Files;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static cl.nexbyte.sigera.Db.*;
import static cl.nexbyte.sigera.StatClassificationTest.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_completitud;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.seed-demo=true","siger.data=./target/test-completitud-${random.uuid}","siger.scheduling=false"})
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StatCompletenessTest extends E07Fixture {
    @Autowired StatCompleteness completeness;

    @Test @Order(1) void escenario7CierraConEstadosEspecialesYNoConUnaPropuesta()throws Exception{
        long old=manual(Map.of());
        long oldReq=assume(old);
        on(HEAD,"REQ_CLOSE","requirement",oldReq,Map.of("reason","Cierre sin listados"));
        catalogs("tipo-solicitud-v1.json","tipo-requirente-v1.json","via-ingreso-v1.json");

        long com=manual(Map.of());
        long req=assume(com);
        var detail=queries.detail(access.actor(HEAD),"REQ",req);
        assertEquals(false,detail.get("can_close"));
        var expected=List.of("Completa tipo de solicitud.","Completa tipo de requirente.");
        assertEquals(expected,detail.get("closure_blockers"));
        Problem ex=assertThrows(Problem.class,()->on(HEAD,"REQ_CLOSE","requirement",req,Map.of("reason","Primera decisión")));
        assertEquals(409,ex.status);
        assertEquals(String.join(" ",expected),ex.getMessage());
        on(HEAD,"REQ_CLOSE","requirement",req,Map.of("reason","Sin antecedentes disponibles",
            "stat",Map.of(TS,status("SIN_INFORMACION"),TR,status("NO_DECLARADO"))));
        assertEquals("CERRADO",s(db.one("SELECT state FROM requirement WHERE id=?",req),"state"));

        long external=flow.conserveExternal(ADMIN,scope("UPR-REQ"),
            FormularioDocs.sign(StatClassificationTest.adult("vulneracion",null))).id();
        assertEquals("VULNERACION",proposals.of(external).get(TS));
        assertTrue(completeness.blockers(StatCompleteness.Closing.DUPLICATE,external,null).contains(expected.getFirst()));
        var onlyRegion=completeness.indicators(new Reports.Reach(false,Set.of(scope("REG-DEMO")),Set.of()),0,Long.MAX_VALUE);
        assertTrue(onlyRegion.isEmpty());
        var local=completeness.indicators(new Reports.Reach(false,Set.of(scope("UPR-REQ")),Set.of()),0,Long.MAX_VALUE);
        assertTrue(local.stream().anyMatch(r->s(r,"variable").equals("REGION_REQUIRENTE")&&s(r,"stat_state").equals("SIN_INFORMACION")));
    }

    @Test void resuelveEnElActoConDerechoYContextoYRevierteUnRechazo()throws Exception{
        catalogs("tipo-solicitud-v1.json","tipo-requirente-v1.json","via-ingreso-v1.json","derechos-2022-v2.json","contexto-v1.json");
        var data=intake(Map.of());
        data.putAll(map("result","ORIENTACION","reason","Atención sintética","effective_at",ago(10),
            "stat",Map.of(TS,code("VULNERACION"),TR,code("NNA"))));
        var before=state();
        Problem missing=assertThrows(Problem.class,()->run(ADMIN,"COM_IMMEDIATE",null,null,data));
        assertEquals(409,missing.status);
        assertTrue(missing.getMessage().contains("derecho"));
        assertTrue(missing.getMessage().contains("contexto"));
        assertEquals(before,state());
        data.put("rights",Map.of("variables",Map.of("DERECHO",Map.of("add",List.of("3.1")),"CONTEXTO",status("NO_DECLARADO"))));
        long req=run(ADMIN,"COM_IMMEDIATE",null,null,data).id();
        assertEquals("CERRADO",s(db.one("SELECT state FROM requirement WHERE id=?",req),"state"));
        assertEquals(Set.of("3.1"),stats.set("REQ",req,"DERECHO",null));
        assertEquals("NO_DECLARADO",stats.setState("REQ",req,"CONTEXTO",null));
        assertEquals("FUNCIONARIO",s(db.one("SELECT origin FROM stat_event WHERE record_type='REQ' AND record_id=? AND variable='DERECHO'",req),"origin"));

        var attention=map("scope_id",scope("UPR-REQ"),"channel","PRESENCIAL","received_at",ago(300),"resolve_now",true,"effective_at",ago(10),
            "result","ORIENTACION","reason","Orientación sintética","form",Map.of("schema_version","1.2","modality","GENERAL","course","EN_ATENCION","motive","Consulta sintética"),
            "stat",data.get("stat"));
        var beforeAttention=state();
        assertEquals(409,assertThrows(Problem.class,()->run(ADMIN,"ATTENTION_CREATE",null,null,attention)).status);
        assertEquals(beforeAttention,state());
        attention.put("rights",data.get("rights"));
        long direct=run(ADMIN,"ATTENTION_CREATE",null,null,attention).id();
        assertEquals("CERRADO",s(db.one("SELECT state FROM requirement WHERE id=?",direct),"state"));
    }

    @Test void soloDuplicadaTieneBloqueosDeDescarte()throws Exception{
        catalogs("tipo-solicitud-v1.json","tipo-requirente-v1.json","via-ingreso-v1.json");
        long com=manual(Map.of());
        long sol=route(com,scope("UPR-REQ"));
        var visible=queries.detail(access.actor(HEAD),"SOL",sol);
        assertEquals(List.of("Completa tipo de solicitud.","Completa tipo de requirente."),visible.get("discard_blockers"));
        assertEquals(409,assertThrows(Problem.class,()->on(HEAD,"SOL_DISCARD","solicitation",sol,Map.of(
            "disposition","DUPLICADA","reason","Ingreso duplicado"))).status);
        on(HEAD,"SOL_DISCARD","solicitation",sol,Map.of("disposition","DUPLICADA","reason","Ingreso duplicado",
            "stat",Map.of(TS,status("SIN_INFORMACION"),TR,status("SIN_INFORMACION"))));
        assertEquals("DUPLICADA",s(db.one("SELECT state FROM communication WHERE id=?",com),"state"));
        long invalid=manual(Map.of());
        on(HEAD,"SOL_DISCARD","solicitation",route(invalid,scope("UPR-REQ")),Map.of("disposition","INVALIDA","reason","Ingreso de ensayo"));
        assertEquals("INVALIDA",s(db.one("SELECT state FROM communication WHERE id=?",invalid),"state"));
    }

    @Test void resolverAtencionUsaElBloqueEstadisticoYLaMismaRegla()throws Exception{
        catalogs("tipo-solicitud-v1.json","tipo-requirente-v1.json","via-ingreso-v1.json");
        long id=run(ADMIN,"ATTENTION_CREATE",null,null,map("scope_id",scope("UPR-REQ"),"channel","PRESENCIAL",
            "received_at",ago(300),"form",Map.of("schema_version","1.0","modality","GENERAL",
                "course","EN_ATENCION","motive","Consulta sintética"))).id();
        var detail=(Map<?,?>)queries.detail(access.actor(ADMIN),"REQ",id).get("attention");
        var expected=List.of("Completa tipo de solicitud.","Completa tipo de requirente.");
        assertEquals(expected,detail.get("resolve_blockers"));
        var data=map("result","ORIENTACION","reason","Cierre sintético","effective_at",ago(10));
        var failure=assertThrows(Problem.class,()->on(ADMIN,"ATTENTION_RESOLVE","requirement",id,data));
        assertEquals(409,failure.status);
        assertEquals(String.join(" ",expected),failure.getMessage());
        data.put("stat",Map.of(TS,code("CONSULTA_ORIENTACION"),TR,status("SIN_INFORMACION")));
        on(ADMIN,"ATTENTION_RESOLVE","requirement",id,data);
        assertEquals("CERRADO",s(db.one("SELECT state FROM requirement WHERE id=?",id),"state"));
    }

    private List<Long> state(){
        return List.of("communication","requirement","stat_event","stat_declared_version","management_note","audit_event","command_log")
            .stream().map(t->db.count("SELECT COUNT(*) FROM "+t)).toList();
    }
}

abstract class E07Fixture {
    @org.springframework.test.context.DynamicPropertySource
    static void trustedKeys(org.springframework.test.context.DynamicPropertyRegistry registry){FormularioDocs.trustedKeys(registry);}
    @Autowired Workflow flow;
    @Autowired Db db;
    @Autowired Access access;
    @Autowired Queries queries;
    @Autowired StatValues stats;
    @Autowired StatProposals proposals;

    @BeforeEach void ready(){db.update("UPDATE staff SET must_change=FALSE");}
    long scope(String code){return n(db.one("SELECT id FROM org_scope WHERE code=?",code),"id");}
    long user(String name){return n(db.one("SELECT id FROM staff WHERE username=?",name),"id");}
    Workflow.Result run(String actor,String command,Long id,Long revision,Map<String,Object> data){
        return flow.run(actor,new Workflow.Command(UUID.randomUUID(),command,id,revision,data));
    }
    Workflow.Result on(String actor,String command,String table,long id,Map<String,Object> data){
        return run(actor,command,id,n(db.one("SELECT revision FROM "+table+" WHERE id=?",id),"revision"),data);
    }
    void catalogs(String... files)throws Exception{
        for(String name:files){
            String content=Files.readString(ReferenceCatalogsTest.DIR.resolve(name));
            if(db.count("SELECT COUNT(*) FROM stat_catalog_version WHERE file_sha256=? AND state='VIGENTE'",StatCatalogs.sha(content.replace("\r\n","\n")))>0)continue;
            long id=run("ti","CATALOG_IMPORT",null,null,Map.of("content",content,"reason","Catálogo de prueba")).id();
            run("ti","CATALOG_ACTIVATE",id,0L,Map.of("reason","Activación de prueba"));
        }
    }
    Map<String,Object> intake(Map<String,Object> data){
        return map("scope_id",scope("UPR-REQ"),"received_at",ago(300),"channel","PRESENCIAL",
            "subject","Ingreso sintético","body","Antecedentes sintéticos","intake",data);
    }
    long manual(Map<String,Object> data){return run(ADMIN,"COM_CREATE",null,null,intake(data)).id();}
    long assume(long com){
        long sol=route(com,scope("UPR-REQ"));
        return on(HEAD,"SOL_ASSUME","solicitation",sol,Map.of("responsible_id",user(PROF),"reason","Recibido")).id();
    }
    long route(long com,long dest){return on(DIST,"COM_ROUTE","communication",com,Map.of("scope_id",dest,"reason","Para gestión")).id();}
}
