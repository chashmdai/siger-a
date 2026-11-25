package cl.nexbyte.sigera;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static cl.nexbyte.sigera.Db.*;

/**
 * Ficha de ingreso de la COM manual (0.8.0, D33, gate E03b; DISENO 3.5): unica fuente de la COM que la tiene (columnas
 * de resumen derivadas, sin filas en communication_person, todos los lectores la toman), formato anterior
 * retirado en E05 con las COM anteriores legibles, ingreso institucional sin persona natural, mismas reglas del Formulario (escenario
 * 11), COM_COMPLETE con motivo solo con la cadena abierta (completar no es corregir), auditoria sin valores, version
 * anterior legible y region de origen de la COM manual igual en Java, en SQL y en Access.suggestedRegion.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_ingreso;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.seed-demo=true","siger.data=./target/test-ingreso-${random.uuid}","siger.scheduling=false"})
@ActiveProfiles("test")
class IntakeRecordTest {
    static final JsonMapper JSON=JsonMapper.builder().build();
    @Autowired Workflow flow;@Autowired Db db;@Autowired Access access;@Autowired Queries queries;@Autowired IntakeRecord records;@Autowired OriginRegion regions;@Autowired PersonIndex index;

    @BeforeEach void ready(){db.update("UPDATE staff SET must_change=FALSE");}
    long scope(String code){return n(db.one("SELECT id FROM org_scope WHERE code=?",code),"id");}
    long rev(String table,long id){return n(db.one("SELECT revision FROM "+table+" WHERE id=?",id),"revision");}
    Workflow.Result run(String who,String type,String table,Long id,Map<String,Object> data){
        return flow.run(who,new Workflow.Command(UUID.randomUUID(),type,id,id==null?null:rev(table,id),data));
    }
    static Map<String,Object> map(Object... pairs){var m=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]);return m;}
    Map<String,Object> com(Object... extra){
        var m=map("scope_id",scope("UPR-REQ"),"channel","TELEFONO","received_at",Instant.now().minusSeconds(300).toString(),"subject","Ingreso sintetico","body","Relato sintetico");
        for(int i=0;i<extra.length;i+=2)m.put((String)extra[i],extra[i+1]);return m;
    }
    long create(Map<String,Object> intake){return run("administrativo","COM_CREATE",null,null,com("intake",intake)).id();}
    void complete(long com,Map<String,Object> intake,String reason){run("administrativo","COM_COMPLETE","communication",com,map("intake",intake,"reason",reason));}
    Problem refused(String who,String type,String table,Long id,Map<String,Object> data){return assertThrows(Problem.class,()->run(who,type,table,id,data));}
    @SuppressWarnings("unchecked") Map<String,Object> stored(long com){return JSON.readValue(s(db.one("SELECT data_json FROM intake_record WHERE communication_id=?",com),"data_json"),Map.class);}
    static Map<String,Object> presenter(Object... extra){
        var p=map("first_name","Ana","last_name","Pérez","second_last_name","Soto","identity_type","RUN","run_number","12.345.678","run_dv","5","email","ana.sintetica@ejemplo.cl",
            "mobile","+56912345678","other_contact","Recado en junta vecinal","address","Calle sintetica 123","nationality","Chile","treatment","ella","preferred_name","Anita");
        for(int i=0;i<extra.length;i+=2)p.put((String)extra[i],extra[i+1]);return p;
    }

    @Test void unicaFuenteConColumnasDerivadasYSinPersonasSueltas(){
        long com=create(map("presenter",presenter("region_code","07","commune","Talca"),"contact_restrictions","No llamar despues de las 20 horas",
            "persons",List.of(map("role","NNA","first_name","Benjamín","last_name","Rojas","age_years","9","region_code","07","commune","Talca"),
                map("role","OTRA_PERSONA","first_name","Hugo","last_name","Lagos","relationship_text","Vecino")),
            "facts_region_code","07","facts_commune","Talca","facts_place","Escuela sintetica"));
        var c=db.one("SELECT * FROM communication WHERE id=?",com);
        assertEquals("Ana Pérez Soto",s(c,"presenter"));assertEquals("12.345.678-5",s(c,"identity_document"));
        assertEquals("ana.sintetica@ejemplo.cl",s(c,"contact_email"));assertEquals("+56912345678",s(c,"contact_phone"));
        assertEquals("Recado en junta vecinal",s(c,"contact"));assertEquals("Calle sintetica 123",s(c,"presenter_domicile"));
        assertEquals("No llamar despues de las 20 horas",s(c,"contact_restrictions"));
        assertEquals(0,db.count("SELECT COUNT(*) FROM communication_person WHERE communication_id=?",com),"una sola fuente");
        var v=db.one("SELECT * FROM intake_revision WHERE communication_id=?",com);
        assertEquals("07",s(v,"region_code"));assertNull(v.get("change_summary"));assertEquals(0,n(v,"revision"));
        // Indice: quien presenta y el NNA; nunca "otra persona".
        assertEquals(List.of("COM:REQUIRENTE","COM_PERSONA:NNA"),db.rows("SELECT source,person_role FROM person_reference WHERE communication_id=? ORDER BY id",com).stream().map(r->s(r,"source")+":"+s(r,"person_role")).toList());
        assertEquals("12345678",s(db.one("SELECT run_number FROM person_reference WHERE communication_id=? AND person_role='REQUIRENTE'",com),"run_number"));
        // La ficha de la COM toma sus personas de la ficha de ingreso.
        var detail=queries.detail(access.actor("administrativo"),"COM",com);
        @SuppressWarnings("unchecked") var persons=(List<Map<String,Object>>)detail.get("persons");
        assertEquals(List.of("Benjamín Rojas","Hugo Lagos"),persons.stream().map(p->s(p,"name")).toList());
        assertEquals("Vecino",s(persons.get(1),"relationship"));
        @SuppressWarnings("unchecked") var intake=(Map<String,Object>)detail.get("intake");
        assertEquals(true,intake.get("can_complete"));assertEquals("07",access.suggestedRegion(com,null));
        // Mezclar la ficha de ingreso con los campos sueltos se rechaza sin escrituras.
        long before=db.count("SELECT COUNT(*) FROM communication");
        refused("administrativo","COM_CREATE",null,null,com("intake",map(),"presenter","Nombre suelto"));
        refused("administrativo","COM_CREATE",null,null,com("intake",map(),"persons",List.of(map("role","NNA","name","Suelto"))));
        assertEquals(before,db.count("SELECT COUNT(*) FROM communication"));
    }

    @Test void formatoAnteriorRetiradoYComAnteriorLegible(){
        // E05: el formato anterior (sin el bloque intake) se rechaza con 400 y sin escrituras, en ambos comandos.
        long before=db.count("SELECT COUNT(*) FROM communication");
        assertEquals(400,refused("administrativo","COM_CREATE",null,null,com("presenter","Nombre libre","identity_document","RUN 12.345.678-5","contact_phone","22 123 4567",
            "persons",List.of(map("role","NNA","name","NNA sintetico","location","Talca")))).status);
        assertEquals(400,refused("administrativo","COM_CREATE",null,null,com()).status);
        assertEquals(400,refused("administrativo","COM_IMMEDIATE",null,null,com("reason","Orientacion entregada","result","ORIENTACION","effective_at",Instant.now().minusSeconds(60).toString())).status);
        assertEquals(before,db.count("SELECT COUNT(*) FROM communication"));
        // Una COM registrada con el formato anterior (datos historicos por SQL) conserva su lectura.
        long com=LegacyCom.create(flow,db,index,"administrativo",com(),map("presenter","Nombre libre","identity_document","RUN 12.345.678-5","contact_phone","22 123 4567",
            "persons",List.of(map("role","NNA","name","NNA sintetico","location","Talca"))));
        assertEquals(1,db.count("SELECT COUNT(*) FROM communication_person WHERE communication_id=?",com));
        assertEquals(0,db.count("SELECT COUNT(*) FROM intake_record WHERE communication_id=?",com));
        assertEquals("22 123 4567",s(db.one("SELECT contact_phone FROM communication WHERE id=?",com),"contact_phone"));
        assertNull(queries.detail(access.actor("administrativo"),"COM",com).get("intake"));
        assertNull(access.suggestedRegion(com,null),"una COM manual sin ficha de ingreso no tiene region");
        assertEquals(409,refused("administrativo","COM_COMPLETE","communication",com,map("intake",map(),"reason","Intento")).status);
    }

    @Test void ingresoInstitucionalSinPersonaNatural(){
        long com=create(map("presenter",map("role","REQUIRENTE"),"institutions_list",List.of(map("name","Juzgado de Familia sintetico","role","Remite el oficio","region_code","13","commune","Santiago"))));
        assertEquals(Map.of(),stored(com).get("presenter"),"sin persona natural inventada");
        @SuppressWarnings("unchecked") var list=(List<Map<String,Object>>)stored(com).get("institutions_list");
        assertEquals("Remite el oficio",list.getFirst().get("role"));assertEquals("",list.getFirst().get("type_code"));
        assertEquals("",s(db.one("SELECT presenter FROM communication WHERE id=?",com),"presenter"));
        assertEquals(0,db.count("SELECT COUNT(*) FROM person_reference WHERE communication_id=?",com),"una institucion no es una persona del indice");
        assertNull(access.suggestedRegion(com,null),"la region de origen es la de quien presenta, no la de la institucion");
    }

    @Test void escenario11MismasReglasEnElIngreso(){
        String[][] bad={{"email","a@b","Quien presenta: Correo"},{"mobile","912345678","Quien presenta: Celular"},{"run_dv","6","Quien presenta: El numero y el digito"},
            {"nationality","chilena","Quien presenta: Nacionalidad"},{"region_code","07","commune","Ñuñoa","Quien presenta: Comuna"},{"legacy_name","Nombre libre","Quien presenta: los datos historicos"},
            {"role","NNA","Opcion invalida"}};
        long before=db.count("SELECT COUNT(*) FROM communication");
        for(String[] c:bad){
            var p=presenter();for(int i=0;i<c.length-1;i+=2)p.put(c[i],c[i+1]);
            var e=refused("administrativo","COM_CREATE",null,null,com("intake",map("presenter",p)));
            assertTrue(e.getMessage().contains(c[c.length-1]),e.getMessage());
        }
        assertTrue(refused("administrativo","COM_CREATE",null,null,com("intake",map("persons",List.of(map("role","NNA")))))
            .getMessage().contains("Persona 1: registra algun antecedente disponible"),"una persona sin datos no se registra");
        assertTrue(refused("administrativo","COM_CREATE",null,null,com("intake",map("persons",List.of(map("role","NNA","age_years","18")))))
            .getMessage().contains("Persona 1: Edad"));
        assertTrue(refused("administrativo","COM_CREATE",null,null,com("intake",map("presenter",presenter("relationship_version",1)))).getMessage().contains("no reconocidos"));
        assertTrue(refused("administrativo","COM_CREATE",null,null,com("intake",map("otro","x"))).getMessage().contains("no reconocidos"));
        assertEquals(before,db.count("SELECT COUNT(*) FROM communication"),"todo o nada");
    }

    @Test void completarNoEsCorregirConMotivoYSinValoresEnAuditoria(){
        long com=create(map("presenter",presenter()));
        assertNull(access.suggestedRegion(com,null));
        complete(com,map("presenter",presenter("region_code","07","commune","Talca")),"Region informada por telefono");
        var regions=origin(com);assertEquals(Arrays.asList("07","07",false),regions,"completar no es corregir");
        complete(com,map("presenter",presenter("region_code","06","commune","Rancagua","mobile","+56987654321")),"Rectifica la region y el celular");
        assertEquals(Arrays.asList("07","06",true),origin(com));
        assertEquals("+56987654321",s(db.one("SELECT contact_phone FROM communication WHERE id=?",com),"contact_phone"),"columnas de resumen derivadas");
        var summaries=db.rows("SELECT change_summary FROM intake_revision WHERE communication_id=? ORDER BY id",com);
        assertTrue(s(summaries.get(1),"change_summary").contains("\"RECTIFICACION\":[]"),s(summaries.get(1),"change_summary"));
        assertTrue(s(summaries.get(2),"change_summary").contains("presenter.region_code"));
        var notes=db.rows("SELECT note FROM audit_event WHERE action='FICHA_INGRESO_COMPLEMENTADA' AND resource_id=? ORDER BY id",com).stream().map(r->s(r,"note")).toList();
        assertEquals(2,notes.size());
        assertTrue(notes.get(0).endsWith("Region informada por telefono Campos complementados: 2; rectificados: 0."),notes.get(0));
        assertTrue(notes.get(1).contains("Region de origen modificada.")&&notes.get(1).endsWith("Campos complementados: 0; rectificados: 3."),notes.get(1));
        for(String value:List.of("12345678","12.345.678","ana.sintetica","Pérez Soto","+5698","Talca","Rancagua"))
            assertEquals(0,db.count("SELECT COUNT(*) FROM audit_event WHERE note LIKE ?","%"+value+"%"),value);
        // Sin motivo, sin cambios o con una revision vencida no se registra nada.
        long versions=db.count("SELECT COUNT(*) FROM intake_revision");
        assertEquals(400,refused("administrativo","COM_COMPLETE","communication",com,map("intake",map("presenter",presenter()))).status);
        assertEquals(409,refused("administrativo","COM_COMPLETE","communication",com,map("intake",map("presenter",presenter("region_code","06","commune","Rancagua","mobile","+56987654321")),"reason","Igual")).status);
        assertThrows(Problem.class,()->flow.run("administrativo",new Workflow.Command(UUID.randomUUID(),"COM_COMPLETE",com,0L,map("intake",map(),"reason","Revision vencida"))));
        assertEquals(versions,db.count("SELECT COUNT(*) FROM intake_revision"));
        // Una version anterior se lee completa y la lectura queda auditada.
        long first=n(db.one("SELECT MIN(id) AS id FROM intake_revision WHERE communication_id=?",com),"id");
        var v=records.version(access.actor("administrativo"),com,first);
        var firstPresenter=(Map<?,?>)((Map<?,?>)v.get("form")).get("presenter");
        assertEquals("",firstPresenter.get("region_code"),"la primera version, sin region");
        assertEquals(1,db.count("SELECT COUNT(*) FROM audit_event WHERE action='FICHA_INGRESO_VERSION_CONSULTADA' AND resource_id=?",com));
        assertEquals(404,assertThrows(Problem.class,()->records.version(access.actor("administrativo"),com,first+999)).status);
    }
    /** Region registrada, vigente y corregida de la COM (sin REQ) con la regla en SQL. */
    List<Object> origin(long com){
        var r=db.one("SELECT "+OriginRegion.registeredSql("c","r")+" AS reg,"+OriginRegion.currentSql("c","r")+" AS cur FROM communication c LEFT JOIN requirement r ON r.communication_id=c.id WHERE c.id=?",com);
        Object reg=r.get("reg")==null?null:s(r,"reg"),cur=r.get("cur")==null?null:s(r,"cur");
        return Arrays.asList(reg,cur,OriginRegion.corrected(reg,cur));
    }

    @Test void soloConLaCadenaAbierta(){
        // Resuelta en el acto: COM atendida y REQ cerrado.
        long req=run("administrativo","COM_IMMEDIATE",null,null,com("intake",map("presenter",presenter()),"reason","Orientacion entregada","result","ORIENTACION","effective_at",Instant.now().minusSeconds(60).toString())).id();
        long immediate=n(db.one("SELECT communication_id FROM requirement WHERE id=?",req),"communication_id");
        assertEquals(1,db.count("SELECT COUNT(*) FROM intake_record WHERE communication_id=?",immediate));
        assertTrue(refused("administrativo","COM_COMPLETE","communication",immediate,map("intake",map("contact_restrictions","x"),"reason","Intento")).getMessage().contains("reapertura formal"));
        // Distribuida, asumida (REQ abierto): se complementa; descartada: no.
        long com=create(map("presenter",presenter()));
        long sol=run("distribucion","COM_ROUTE","communication",com,map("scope_id",scope("UPR-REQ"),"reason","Clasificacion sintetica")).id();
        complete(com,map("presenter",presenter(),"contact_restrictions","Solo correo"),"Complemento con SOL pendiente");
        long assumed=run("coordinacion.requerimientos","SOL_ASSUME","solicitation",sol,map("responsible_id",n(db.one("SELECT id FROM staff WHERE username='profesional'"),"id"),"reason","Asume")).id();
        assertEquals("ABIERTO",s(db.one("SELECT state FROM requirement WHERE id=?",assumed),"state"));
        complete(com,map("presenter",presenter(),"contact_restrictions","Solo correo institucional"),"Complemento con REQ abierto");
        long discarded=create(map("presenter",presenter()));
        long sol2=run("distribucion","COM_ROUTE","communication",discarded,map("scope_id",scope("UPR-REQ"),"reason","Clasificacion sintetica")).id();
        run("coordinacion.requerimientos","SOL_DISCARD","solicitation",sol2,map("disposition","DUPLICADA","reason","Duplicada sintetica"));
        assertEquals(409,refused("administrativo","COM_COMPLETE","communication",discarded,map("intake",map("contact_restrictions","x"),"reason","Intento")).status);
        // Sin facultad de registro, distribucion ni jefatura del REQ: 403.
        assertEquals(403,refused("profesional.regional","COM_COMPLETE","communication",com,map("intake",map(),"reason","Intento")).status);
    }

    @Test void regionDeLaComManualIgualEnJavaSqlYPropuesta(){
        var coms=new ArrayList<Long>();
        coms.add(create(map("presenter",presenter())));
        coms.add(create(map("presenter",presenter("region_code","07","commune","Talca"))));
        long fixed=create(map("presenter",presenter("region_code","07")));complete(fixed,map("presenter",presenter("region_code","05")),"Rectifica");coms.add(fixed);
        long removed=create(map("presenter",presenter("region_code","13")));complete(removed,map("presenter",presenter()),"Quita la region");coms.add(removed);
        long legacy=LegacyCom.create(flow,db,index,"administrativo",com(),map("presenter","Libre"));coms.add(legacy);
        var expected=Map.of(coms.get(0),Arrays.asList(null,null,false),coms.get(1),Arrays.asList("07","07",false),fixed,Arrays.asList("07","05",true),
            removed,Arrays.asList("13",null,true),legacy,Arrays.asList(null,null,false));
        for(long com:coms){
            assertEquals(expected.get(com),origin(com),"SQL "+com);
            assertEquals(origin(com).get(1),access.suggestedRegion(com,null),"Java y propuesta "+com);
            var java=OriginRegion.current(null,db.rows(OriginRegion.FORM_FIELDS,com));
            assertEquals(origin(com).get(1),java,"OriginRegion.current "+com);
        }
        // Con REQ (SOL asumida) la regla del REQ da lo mismo.
        long sol=run("distribucion","COM_ROUTE","communication",fixed,map("scope_id",scope("UPR-REQ"),"reason","Clasificacion sintetica")).id();
        long req=run("coordinacion.requerimientos","SOL_ASSUME","solicitation",sol,map("responsible_id",n(db.one("SELECT id FROM staff WHERE username='profesional'"),"id"),"reason","Asume")).id();
        assertEquals(Map.of("registered","07","current","05","corrected",true),regions.ofRequirement(req));
    }
}
