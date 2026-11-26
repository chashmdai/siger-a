package cl.nexbyte.sigera;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static cl.nexbyte.sigera.Db.*;
import static cl.nexbyte.sigera.StatClassificationTest.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Filtro "Con datos estadisticos pendientes" de las bandejas COM, SOL y REQ (0.8.0, gate E13; GO y su precision del
 * 2026-11-26): COM computables con alguna clasificacion PENDIENTE entre las de catalogo VIGENTE (tipo de solicitud,
 * requirente y via; en el REQ, ademas derecho y contexto consolidados), con las reglas de lectura de cada bandeja. Las
 * cifras estan escritas a mano (matriz MATRIX) y se comparan con PENDIENTE de COMPLETITUD del motor, con una cuenta cuya
 * lectura y alcance de Reportes coinciden (institucional y una sede).
 *
 * Poblacion (julio de 2025; central = UPR-REQ, sede = REG-DEMO):
 *   A NUEVA, solicitud y requirente informados.          B NUEVA, sin clasificar.
 *   C SOL pendiente, solo solicitud.                      D REQ, solicitud y requirente, derecho; contexto pendiente.
 *   E REQ completo (derecho y contexto con estado).       F REQ sin clasificar.
 *   G DUPLICADA sin clasificar (cuenta).                  H INVALIDA (no cuenta).  I VINCULADA (no cuenta).
 *   K REQ: solicitud, requirente y derecho; contexto solo en su Caso (consolidado informado).
 *   L NUEVA con estados especiales: solicitud SIN_INFORMACION y requirente NO_DECLARADO (resueltos, no pendientes).
 *   R1 sede, NUEVA, solo solicitud.                       R2 sede, REQ con solicitud y requirente; sin derecho ni contexto.
 * La via deriva del canal (informada) en todas.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_filtro_pendientes;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.seed-demo=true","siger.data=./target/test-filtro-pendientes-${random.uuid}","siger.scheduling=false"})
@ActiveProfiles("test")
class StatPendingFilterTest extends StatFixture {
    static final Map<String,Object> PERIOD=period("CUENTA_PUBLICA","2025-07-01");
    static final String INST="consulta.e13",SEDE="coordinacion.regional",SEDE_PROF="profesional.regional";
    static final String DER="DERECHO",CTX="CONTEXTO",FILTER="stat_pending";
    static final List<String> NOT_PENDING=List.of("INFORMADO","SIN_INFORMACION","NO_DECLARADO","NO_APLICA");
    static final Map<String,List<String>> POPULATION=Map.of(
        "com",List.of(),
        "sol",List.of("ESTADO_SOL","PENDIENTE","DEVUELTA","ASUMIDA","DESCARTADA"),
        "req",List.of("ESTADO_REQ","ABIERTO","CERRADO"));
    /**
     * Matriz escrita a mano: cuenta, bandeja y variable vigente (o TODAS) -> registros con el filtro. Sale de la poblacion
     * del Javadoc, no del codigo.
     */
    static final Object[][] MATRIX={
        {INST,"com","TIPO_SOLICITUD",3},   // B, F, G
        {INST,"com","TIPO_REQUIRENTE",5},  // B, C, F, G, R1
        {INST,"com","VIA_INGRESO",0},
        {INST,"com","TODAS",5},            // B, C, F, G, R1
        {INST,"sol","TIPO_SOLICITUD",1},   // F
        {INST,"sol","TIPO_REQUIRENTE",2},  // C, F
        {INST,"sol","VIA_INGRESO",0},
        {INST,"sol","TODAS",2},
        {INST,"req","TIPO_SOLICITUD",1},   // F
        {INST,"req","TIPO_REQUIRENTE",1},  // F
        {INST,"req","VIA_INGRESO",0},
        {INST,"req",DER,2},                // F, R2
        {INST,"req",CTX,3},                // D, F, R2 (K tiene contexto en su Caso)
        {INST,"req","TODAS",3},            // D, F, R2
        {SEDE,"com","TIPO_SOLICITUD",0},
        {SEDE,"com","TIPO_REQUIRENTE",1},  // R1
        {SEDE,"com","TODAS",1},
        {SEDE,"req",DER,1},                // R2
        {SEDE,"req",CTX,1},                // R2
        {SEDE,"req","TODAS",1},
    };
    @Autowired BoardQueries boards;

    @Test void mismaReglaQueCompletitudConLecturaYAlcanceCoincidentes()throws Exception{
        staff(INST,"CONSULTA_INSTITUCIONAL","REPORTES");
        db.update("INSERT INTO role_assignment(user_id,role_code,scope_id,valid_from,basis) VALUES(?,?,?,0,'Prueba sintetica')",user(SEDE),"REPORTES",scope("REG-DEMO"));
        db.update("UPDATE staff SET must_change=FALSE");

        // Antes de los catalogos: actos que no los necesitan.
        long a=com(ADMIN,"UPR-REQ","PRESENCIAL","2025-07-01T15:00:00Z",Map.of(),null);
        long b=com(ADMIN,"UPR-REQ","CORREO","2025-07-02T15:00:00Z",Map.of(),null);
        long c=com(ADMIN,"UPR-REQ","TELEFONO","2025-07-03T15:00:00Z",Map.of(),null);
        long d=com(ADMIN,"UPR-REQ","CORREO","2025-07-04T15:00:00Z",Map.of(),null);
        long e=com(ADMIN,"UPR-REQ","PRESENCIAL","2025-07-05T15:00:00Z",Map.of(),null);
        long f=com(ADMIN,"UPR-REQ","TELEFONO","2025-07-06T15:00:00Z",Map.of(),null);
        long g=com(ADMIN,"UPR-REQ","CORREO","2025-07-07T15:00:00Z",Map.of(),null);
        long h=com(ADMIN,"UPR-REQ","CORREO","2025-07-08T15:00:00Z",Map.of(),null);
        long i=com(ADMIN,"UPR-REQ","CORREO","2025-07-09T15:00:00Z",Map.of(),null);
        long k=com(ADMIN,"UPR-REQ","CORREO","2025-07-10T15:00:00Z",Map.of(),null);
        long l=com(ADMIN,"UPR-REQ","PRESENCIAL","2025-07-10T16:00:00Z",Map.of(),null);
        long r1=com(SEDE_PROF,"REG-DEMO","CORREO","2025-07-11T15:00:00Z",Map.of(),null);
        long r2=com(SEDE_PROF,"REG-DEMO","TELEFONO","2025-07-12T15:00:00Z",Map.of(),null);
        route(DIST,c,"UPR-REQ",null);
        for(long x:List.of(d,e,f,k)){route(DIST,x,"UPR-REQ",null);assume(HEAD,x,PROF,null);}
        route(SEDE,r2,"REG-DEMO",null);assume(SEDE,r2,SEDE_PROF,null);
        on(DIST,"COM_DISCARD","communication",g,Map.of("disposition","DUPLICADA","reason","Duplicada sintetica"));
        on(DIST,"COM_DISCARD","communication",h,Map.of("disposition","INVALIDA","reason","Invalida sintetica"));
        on(DIST,"COM_LINK","communication",i,Map.of("folio",folio(req(d))));
        long caseK=on(HEAD,"REQ_CASE_CREATE","requirement",req(k),Map.of("scope_id",scope("UPR-REQ"),"responsible_id",user(PROF),"objective","Gestion sintetica","matter","UPR")).id();

        // Sin ningun catalogo vigente el filtro no muestra nada (la region no cuenta).
        for(String who:List.of(INST,SEDE))for(String kind:POPULATION.keySet())assertEquals(0,count(who,kind),who+" "+kind);

        catalogs("tipo-solicitud-v1.json","tipo-requirente-v1.json","via-ingreso-v1.json","derechos-2022-v2.json","contexto-v1.json");
        classify(DIST,a,stat("CONSULTA_ORIENTACION","PERSONA_NATURAL_ADULTA"));
        classify(HEAD,c,stat("VULNERACION",null));
        classify(HEAD,d,stat("VULNERACION","PERSONA_NATURAL_ADULTA"));
        classify(HEAD,e,stat("CONSULTA_ORIENTACION","NNA"));
        classify(HEAD,k,stat("CONSULTA_ORIENTACION","PERSONA_NATURAL_ADULTA"));
        classify(DIST,l,stat("SIN_INFORMACION","NO_DECLARADO"));
        classify(SEDE,r1,stat("CONSULTA_ORIENTACION",null));
        classify(SEDE,r2,stat("VULNERACION","NNA"));
        rights(HEAD,"REQ",req(d),DER,add("3.1"));
        rights(HEAD,"REQ",req(e),DER,Map.of("status","SIN_INFORMACION"));
        rights(HEAD,"REQ",req(e),CTX,Map.of("status","NO_APLICA"));
        rights(HEAD,"REQ",req(k),DER,Map.of("status","NO_APLICA"));
        rights(HEAD,"CAS",caseK,CTX,add("FAMILIAR"));

        var all=new ArrayList<String>();
        for(var row:db.rows("SELECT `current_catalog` AS name FROM stat_catalog_version WHERE `current_catalog` IS NOT NULL"))all.add(s(row,"name"));
        try{
            for(var m:MATRIX){
                String who=(String)m[0],kind=(String)m[1],variable=(String)m[2];long expected=((Number)m[3]).longValue();
                only(variable.equals("TODAS")?all:List.of(variable));
                assertEquals(expected,count(who,kind),"bandeja "+who+" "+kind+" "+variable);
                if(variable.equals("TODAS")){
                    // Union de las variables: ingresos de la bandeja menos los que no tienen ninguna pendiente; en el REQ, la
                    // "caracterizacion completa" del Panorama de E12 (seis variables); en COM y SOL, las tres del ingreso.
                    assertEquals(expected,ingresos(who,kind,false)-ingresos(who,kind,true),"panorama "+who+" "+kind);
                }else{
                    assertEquals(expected,pending(who,kind,variable),"completitud "+who+" "+kind+" "+variable);
                }
            }
        }finally{only(all);}

        // Lo pendiente de cada cuenta es exactamente lo legible de su bandeja que es pendiente para la institucion.
        staff("lectura.e13","LECTURA@UPR-REQ");
        for(String kind:POPULATION.keySet()){
            var institution=ids(INST,kind,FILTER);
            for(String who:List.of(DIST,HEAD,PROF,ADMIN,SEDE,SEDE_PROF,"lectura.e13","direccion.upd")){
                var mine=ids(who,kind,FILTER);var visible=new HashSet<>(ids(who,kind,"all"));visible.retainAll(institution);
                assertEquals(visible,mine,who+" "+kind);
            }
        }
        assertEquals(Set.of(b,c,f,g,r1),ids(INST,"com",FILTER).stream().map(id->id).collect(java.util.stream.Collectors.toSet()));
        assertFalse(ids(INST,"com",FILTER).contains(h));
        assertFalse(ids(INST,"com",FILTER).contains(i));
        // Los demas filtros y conteos de las bandejas no cambian.
        assertEquals(13,count(INST,"com","all"));
        assertFalse(ids(INST,"com",FILTER).contains(l),"un estado especial esta resuelto");
    }

    // --- Apoyo --------------------------------------------------------------------------------------------------
    /** Deja vigentes (para el filtro) solo los catalogos indicados; la prueba restaura todos al terminar. */
    void only(List<String> variables){
        db.update("UPDATE stat_catalog_version SET `current_catalog`=NULL WHERE `current_catalog` IS NOT NULL");
        for(String v:variables)db.update("UPDATE stat_catalog_version SET `current_catalog`=catalog WHERE catalog=? AND state='VIGENTE'",v);
    }
    void classify(String who,long com,Map<String,Object> stat){
        run(who,"STAT_CLASSIFY",com,db.count("SELECT COUNT(*) FROM stat_event WHERE record_type='COM' AND record_id=?",com),Map.of("stat",stat));
    }
    long count(String who,String kind){return count(who,kind,FILTER);}
    long count(String who,String kind,String filter){return ((Number)boards.page(access.actor(who),kind,0,100,"",filter,null).get("total")).longValue();}
    Set<Long> ids(String who,String kind,String filter){
        var out=new HashSet<Long>();
        for(var r:list(boards.page(access.actor(who),kind,0,100,"",filter,null).get("items")))
            out.add(kind.equals("com")?n(r,"id"):kind.equals("sol")?n(r,"communication_id"):n(r,"communication_id"));
        return out;
    }
    Map<String,Object> population(String kind){
        var p=POPULATION.get(kind);var filters=new LinkedHashMap<String,Object>();
        if(!p.isEmpty())filters.put(p.getFirst(),p.subList(1,p.size()));
        return filters;
    }
    /** PENDIENTE de COMPLETITUD de la variable, sobre la poblacion de la bandeja. */
    long pending(String who,String kind,String variable){
        var r=query(who,"COMPLETITUD",List.of("VARIABLE"),List.of("ESTADO_VARIABLE"),PERIOD,"filters",population(kind));
        return cells(r).getOrDefault(variable+"|PENDIENTE",0L);
    }
    /** INGRESOS de la poblacion de la bandeja; complete = sin variables pendientes (en el REQ, la "caracterizacion completa" del Panorama de E12). */
    long ingresos(String who,String kind,boolean complete){
        var filters=population(kind);
        if(complete){
            var variables=kind.equals("req")?List.of("TIPO_SOLICITUD","TIPO_REQUIRENTE","VIA_INGRESO","DERECHO","CONTEXTO"):List.of("TIPO_SOLICITUD","TIPO_REQUIRENTE","VIA_INGRESO");
            for(String v:variables)filters.put("ESTADO_"+v,NOT_PENDING);
            if(!kind.equals("req"))return grand(query(who,"INGRESOS",List.of(),List.of(),PERIOD,"filters",filters));
            var regions=new ArrayList<String>(AdultIdentity.REGIONS.keySet());regions.add("SIN_INFORMACION");
            filters.put("REGION_REQUIRENTE",regions);
        }
        return grand(query(who,"INGRESOS",List.of(),List.of(),PERIOD,"filters",filters));
    }
}
