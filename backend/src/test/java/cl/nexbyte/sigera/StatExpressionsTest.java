package cl.nexbyte.sigera;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Las expresiones SQL de E05 a E07 no cambian al agregar las sobrecargas de E08 (GO, condicion B): se comparan caracter por
 * caracter con stats/expresiones-e07.txt, escrito con el codigo de c9abb9a antes de editar. De TerritorialDims y
 * PersonDims solo puede cambiar el modo REQ (condicion A), que por eso no figura en el archivo.
 */
class StatExpressionsTest {
    static final Path FILE=Path.of("src","test","resources","stats","expresiones-e07.txt");

    static Map<String,String> statValues(){
        var out=new TreeMap<String,String>();
        for(Long at:Arrays.asList(null,1234567890123L)){
            String t=at==null?"vigente":"fecha";
            for(String v:StatValues.SINGLE){
                out.put("stateSql."+v+"."+t,StatValues.stateSql("c",v,at));
                out.put("codeSql."+v+"."+t,StatValues.codeSql("c",v,at));
                out.put("versionSql."+v+"."+t,StatValues.versionSql("c",v,at));
                out.put("originSql."+v+"."+t,StatValues.originSql("c",v,at));
            }
            for(String v:StatValues.MULTIPLE)for(String type:List.of("REQ","CAS")){
                out.put("setSql."+type+"."+v+"."+t,StatValues.setSql(type,"x.id",v,at));
                out.put("setStateSql."+type+"."+v+"."+t,StatValues.setStateSql(type,"x.id",v,at));
            }
        }
        for(String v:StatValues.MULTIPLE)out.put("consolidatedStateSql."+v,Rights.consolidatedStateSql("r",v));
        return out;
    }
    static Map<String,String> dims(){
        var out=new TreeMap<String,String>();
        for(var mode:List.of(TerritorialDims.Mode.INGRESO,TerritorialDims.Mode.VIGENTE,TerritorialDims.Mode.HISTORICO)){
            Long at=mode==TerritorialDims.Mode.HISTORICO?1234567890123L:null;
            out.put("versionSql."+mode,TerritorialDims.versionSql("c","r",mode,at));
            out.put("scopeSql."+mode,TerritorialDims.scopeSql("c","r","sol",mode,at));
            for(String column:List.of("requester_region","requester_commune","facts_region","facts_commune"))
                out.put("locationSql."+column+"."+mode,TerritorialDims.locationSql("c","r",mode,at,column));
            for(String field:List.of("age_years","nationality","state"))
                out.put("requesterSql."+field+"."+mode,PersonDims.requesterSql("c","r",mode,at,field));
            out.put("affectedSql."+mode,PersonDims.affectedSql(mode,at));
        }
        out.put("referenceScopeSql",TerritorialDims.referenceScopeSql("c","r","sol"));
        out.put("coverageSql",TerritorialDims.coverageSql("x.region","x.at"));
        out.put("registeredSql",OriginRegion.registeredSql("c","r"));
        return out;
    }
    static Map<String,String> all(){var out=new TreeMap<>(statValues());out.putAll(dims());return out;}

    @Test void expresionesIgualesALasDeE07()throws Exception{
        var expected=new TreeMap<String,String>();
        for(String line:Files.readAllLines(FILE,StandardCharsets.UTF_8)){
            int tab=line.indexOf('\t');
            expected.put(line.substring(0,tab),line.substring(tab+1));
        }
        var actual=all();
        assertEquals(expected.keySet(),actual.keySet());
        for(var e:expected.entrySet())assertEquals(e.getValue(),actual.get(e.getKey()),e.getKey());
    }
}
