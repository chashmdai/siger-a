package cl.nexbyte.sigera;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.node.ObjectNode;

import static cl.nexbyte.sigera.Db.*;
import static cl.nexbyte.sigera.FormularioDocs.*;
import static cl.nexbyte.sigera.StatClassificationTest.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Apoyo de las pruebas del motor estadistico (E08): sedes y cuentas sinteticas, cadenas creadas con comandos, Formularios
 * con vectores sinteticos firmados e instantes de borde fijados por SQL. Las cifras esperadas estan escritas a mano en
 * src/test/resources/stats (poblacion.csv describe los ingresos y esperados.csv las celdas).
 */
abstract class StatFixture extends E07Fixture {
    static final Path DIR=Path.of("src","test","resources","stats");
    static final String[] CATALOGS={"tipo-solicitud-v1.json","tipo-requirente-v1.json","via-ingreso-v1.json","derechos-2022-v2.json","contexto-v1.json","caracterizacion-v1.json",
        "rol-estado-v1.json","grupos-prioritarios-v1.json","tipo-actuacion-v1.json","tipo-institucion-v1.json","tramo-edad-v1.json"};
    @Autowired StatEngine engine;
    @Autowired Reports reports;

    // --- Organizacion ---------------------------------------------------------------------------------------------
    long site(String code,String region,String state){
        if(db.count("SELECT COUNT(*) FROM org_scope WHERE code=?",code)==0)
            db.insert("INSERT INTO org_scope(code,name,unit_code,area_code,site_code,regional,region_code,state,enabled) VALUES(?,?,'REG',NULL,?,TRUE,?,?,?)",
                code,"Sede sintetica "+code,"SEDE-"+code,region,state,state.equals("ACTIVA"));
        return scope(code);
    }
    /** Cuenta con funciones "ROL" (institucional) o "ROL@AMBITO". */
    String staff(String username,String... grants){
        if(db.count("SELECT COUNT(*) FROM staff WHERE username=?",username)==0){
            long id=db.insert("INSERT INTO staff(username,name,password_hash,home_scope,must_change) VALUES(?,?,'x',?,FALSE)",username,"Cuenta "+username,scope("UGI"));
            for(var g:grants){
                var p=g.split("@");
                db.update("INSERT INTO role_assignment(user_id,role_code,scope_id,valid_from,basis) VALUES(?,?,?,0,'Prueba sintetica')",id,p[0],p.length>1?scope(p[1]):null);
            }
        }
        return username;
    }

    // --- Cadenas --------------------------------------------------------------------------------------------------
    Map<String,Object> form(String region){
        var presenter=new LinkedHashMap<String,Object>(Map.of("first_name","Persona"));
        if(region!=null){presenter.put("region_code",region);presenter.put("commune",AdultIdentity.COMMUNES.get(region).getFirst());}
        return Map.of("presenter",presenter);
    }
    long com(String who,String scope,String channel,String received,Map<String,Object> intake,Map<String,Object> stat){
        var data=map("scope_id",scope(scope),"received_at",received,"channel",channel,"subject","Ingreso sintetico","body","Antecedentes sinteticos","intake",intake);
        if(stat!=null)data.put("stat",stat);
        return run(who,"COM_CREATE",null,null,data).id();
    }
    static Map<String,Object> stat(String type,String requester){
        var m=new LinkedHashMap<String,Object>();
        if(type!=null)m.put(TS,StatCatalogs.SPECIAL.containsKey(type)?status(type):code(type));
        if(requester!=null)m.put(TR,StatCatalogs.SPECIAL.containsKey(requester)?status(requester):code(requester));
        return m;
    }
    long sol(long com){return n(db.one("SELECT id FROM solicitation WHERE communication_id=?",com),"id");}
    long req(long com){return n(db.one("SELECT id FROM requirement WHERE communication_id=?",com),"id");}
    long comOf(long req){return n(db.one("SELECT communication_id FROM requirement WHERE id=?",req),"communication_id");}
    String folio(long req){return s(db.one("SELECT folio FROM requirement WHERE id=?",req),"folio");}
    long last(String table){return n(db.one("SELECT MAX(id) AS id FROM "+table),"id");}
    void route(String who,long com,String scope,String matter){
        var data=map("scope_id",scope(scope),"reason","Para gestion");
        if(matter!=null)data.put("suggested_matter",matter);
        on(who,"COM_ROUTE","communication",com,data);
    }
    long assume(String who,long com,String responsible,Map<String,Object> stat){
        var data=map("responsible_id",user(responsible),"reason","Recibido");
        if(stat!=null)data.put("stat",stat);
        return on(who,"SOL_ASSUME","solicitation",sol(com),data).id();
    }
    void discard(String who,long com,String disposition){
        on(who,"SOL_DISCARD","solicitation",sol(com),Map.of("disposition",disposition,"reason","Descarte sintetico"));
    }
    void rights(String who,String record,long id,String variable,Map<String,Object> change){
        long revision=db.count("SELECT COUNT(*) FROM stat_event WHERE record_type=? AND record_id=?",record,id);
        run(who,"RIGHTS_CLASSIFY",id,revision,Map.of("record",record,"variables",Map.of(variable,change),"reason","Motivo sintetico"));
    }
    static Map<String,Object> add(String... codes){return Map.of("add",List.of(codes));}
    static Map<String,Object> remove(String... codes){return Map.of("remove",List.of(codes));}
    long attention(String who,String scope,String channel,String received,Map<String,Object> form,Object... extra){
        var full=new LinkedHashMap<String,Object>(Map.of("schema_version","1.2","modality","GENERAL","course","EN_ATENCION","motive","Consulta sintetica"));
        full.putAll(form);
        var data=map("scope_id",scope(scope),"channel",channel,"received_at",received,"form",full);
        for(int i=0;i<extra.length;i+=2)data.put((String)extra[i],extra[i+1]);
        return run(who,"ATTENTION_CREATE",null,null,data).id();
    }
    long formulario(String scope,Instant received,String region,String commune,Map<String,String> fields){
        var document=cuVulneration();
        document.put("recibidaEn",received.toString());
        var campos=(ObjectNode)document.path("datos").path("campos");
        if(region!=null){campos.put("adult-region",region);campos.put("adult-commune",commune);}
        fields.forEach(campos::put);
        return flow.conserveExternal(ADMIN,scope(scope),sign(document)).id();
    }
    static long ms(String iso){return Instant.parse(iso).toEpochMilli();}
    /** Instante de registro de la COM (con su hito), fijado para los bordes. */
    void registered(long com,String iso){
        db.update("UPDATE communication SET created_ms=? WHERE id=?",ms(iso),com);
        db.update("UPDATE milestone SET at_ms=? WHERE communication_id=? AND event='COM_REGISTRADA'",ms(iso),com);
    }

    // --- Consulta y respuesta ---------------------------------------------------------------------------------------
    static Map<String,Object> period(String type,String from){return map("type",type,"from",from);}
    static Map<String,Object> days(String from,String to){return map("type","PERSONALIZADO","from",from,"to",to);}
    Map<String,Object> query(String who,String measure,List<String> rows,List<String> columns,Map<String,Object> period,Object... extra){
        var body=map("measure",measure,"rows",rows,"columns",columns,"period",period);
        for(int i=0;i<extra.length;i+=2)body.put((String)extra[i],extra[i+1]);
        return engine.query(access.actor(who),body);
    }
    @SuppressWarnings("unchecked") static Map<String,Object> obj(Object o){return (Map<String,Object>)o;}
    @SuppressWarnings("unchecked") static List<Map<String,Object>> list(Object o){return (List<Map<String,Object>>)o;}
    @SuppressWarnings("unchecked") static List<String> keys(Object o){return (List<String>)o;}
    /** Celdas como "fila|columna=valor" (claves unidas con "+"), sin las de valor 0 salvo que se pidan. */
    static Map<String,Long> cells(Map<String,Object> r,String field,boolean zeros){
        var out=new LinkedHashMap<String,Long>();
        for(var c:list(r.get("cells"))){
            long v=((Number)c.get(field)).longValue();
            if(zeros||v!=0)out.put(String.join("+",keys(c.get("row")))+(keys(c.get("column")).isEmpty()?"":"|"+String.join("+",keys(c.get("column")))),v);
        }
        return out;
    }
    static Map<String,Long> cells(Map<String,Object> r){return cells(r,"n",false);}
    static Map<String,Object> cell(Map<String,Object> r,List<String> row,List<String> column){
        return list(r.get("cells")).stream().filter(c->c.get("row").equals(row)&&c.get("column").equals(column)).findFirst()
            .orElseThrow(()->new AssertionError("sin celda "+row+column+" en "+cells(r)));
    }
    static double percent(Map<String,Object> r,List<String> row,List<String> column){return ((Number)cell(r,row,column).get("percent")).doubleValue();}
    static long grand(Map<String,Object> r){return grand(r,"n");}
    static long grand(Map<String,Object> r,String field){return ((Number)obj(obj(r.get("totals")).get("grand")).get(field)).longValue();}
    static Map<String,Long> totals(Map<String,Object> r,String side,String field){
        var out=new LinkedHashMap<String,Long>();
        for(var t:list(obj(r.get("totals")).get(side))){long v=((Number)t.get(field)).longValue();if(v!=0)out.put(String.join("+",keys(t.get("key"))),v);}
        return out;
    }
    static Map<String,Long> of(Object... pairs){
        var m=new LinkedHashMap<String,Long>();for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],((Number)pairs[i+1]).longValue());return m;
    }
    static Set<String> warnings(Map<String,Object> r){var out=new TreeSet<String>();for(var w:list(r.get("warnings")))out.add(s(w,"code"));return out;}
    /** Cifras escritas a mano: esperados.csv (consulta;clave;n). */
    static Map<String,Long> expected(String query){
        var out=new LinkedHashMap<String,Long>();
        for(String line:lines("esperados.csv")){
            var f=line.split(";");
            if(f.length==3&&f[0].equals(query))out.put(f[1],Long.valueOf(f[2]));
        }
        assertFalse(out.isEmpty(),query);
        return out;
    }
    /** Filas de poblacion.csv por letra. */
    static Map<String,Map<String,String>> population(){
        var lines=lines("poblacion.csv");
        var head=lines.getFirst().split(";");var out=new LinkedHashMap<String,Map<String,String>>();
        for(String line:lines.subList(1,lines.size())){
            var f=line.split(";",-1);var row=new HashMap<String,String>();
            for(int i=0;i<head.length;i++)row.put(head[i],f[i]);
            out.put(row.get("id"),row);
        }
        return out;
    }
    private static List<String> lines(String name){
        try{return Files.readAllLines(DIR.resolve(name),StandardCharsets.UTF_8);}catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
}
