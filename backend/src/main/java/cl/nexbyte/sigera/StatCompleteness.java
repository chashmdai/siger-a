package cl.nexbyte.sigera;

import java.util.*;
import org.springframework.stereotype.Service;

import static cl.nexbyte.sigera.Db.*;

/** Regla de completitud compartida por las fichas y las transiciones de cierre. */
@Service
public class StatCompleteness {
    enum Closing { REQUIREMENT, IMMEDIATE, DUPLICATE }
    private static final List<String> INTAKE=List.of("TIPO_SOLICITUD","TIPO_REQUIRENTE","VIA_INGRESO");
    private static final List<String> VULNERATION=List.of("DERECHO","CONTEXTO");
    private final Db db;
    private final Rights rights;

    public StatCompleteness(Db db,Rights rights){
        this.db=db;
        this.rights=rights;
    }

    Map<String,Object> requirements(){
        var enabled=enabled();
        var intake=INTAKE.stream().filter(enabled::contains).toList();
        return Map.of("intake",intake,"vulnerability",VULNERATION.stream().filter(enabled::contains).toList(),
            "duplicate",intake);
    }

    List<String> blockers(Closing closing,long com,Long req){
        var enabled=enabled();
        if(Collections.disjoint(enabled,INTAKE)&&
            (closing==Closing.DUPLICATE||Collections.disjoint(enabled,VULNERATION)))return List.of();
        var expressions=new ArrayList<String>();
        for(String variable:INTAKE)
            expressions.add(StatValues.stateSql("c",variable,null)+" AS "+variable);
        expressions.add(StatValues.codeSql("c","TIPO_SOLICITUD",null)+" AS request_code");
        var values=db.one("SELECT "+String.join(",",expressions)+" FROM communication c WHERE c.id=?",com);
        var blockers=new ArrayList<String>();
        for(String variable:INTAKE){
            if(enabled.contains(variable)&&s(values,variable.toLowerCase(Locale.ROOT)).equals(StatValues.PENDING))
                blockers.add("Completa "+label(variable)+".");
        }
        if(closing!=Closing.DUPLICATE&&s(values,"request_code").equals("VULNERACION")){
            for(String variable:VULNERATION){
                if(enabled.contains(variable)&&(req==null||!rights.resolved(req,variable)))
                    blockers.add("Completa "+label(variable)+" para la vulneración.");
            }
        }
        return List.copyOf(blockers);
    }

    private Set<String> enabled(){
        var enabled=new HashSet<String>();
        for(var item:db.rows("SELECT `current_catalog` AS name FROM stat_catalog_version WHERE `current_catalog` IS NOT NULL"))
            enabled.add(s(item,"name"));
        return enabled;
    }

    private static String label(String variable){return StatCatalogs.CATALOGS.get(variable).toLowerCase(Locale.ROOT);}

    void require(Closing closing,long com,Long req){
        var pending=blockers(closing,com,req);
        Problem.require(pending.isEmpty(),String.join(" ",pending));
    }

    /** Una fila por variable, estado y ámbito, dentro del alcance ya resuelto por el servidor. */
    List<Map<String,Object>> indicators(Reports.Reach reach,long from,long to){
        if(reach.empty())return List.of();
        var args=new ArrayList<Object>(List.of(from,to));
        String scope=TerritorialDims.referenceScopeSql("c","r","sol");
        var conditions=new ArrayList<String>();
        if(!reach.all()){
            if(!reach.scopes().isEmpty()){
                conditions.add("s.id IN ("+marks(reach.scopes().size())+")");
                args.addAll(reach.scopes());
            }
            if(!reach.matters().isEmpty()){
                conditions.add("(s.regional=TRUE AND (CASE WHEN r.id IS NULL THEN sol.suggested_matter ELSE r.matter END) IN ("
                    +marks(reach.matters().size())+"))");
                args.addAll(reach.matters());
            }
        }
        String source=" FROM communication c LEFT JOIN requirement r ON r.communication_id=c.id"
            +" LEFT JOIN solicitation sol ON sol.communication_id=c.id JOIN org_scope s ON s.id="+scope
            +" WHERE c.received_ms>=? AND c.received_ms<? AND c.state NOT IN ('INVALIDA','VINCULADA')"
            +(conditions.isEmpty()?"":" AND ("+String.join(" OR ",conditions)+")");
        var result=new ArrayList<Map<String,Object>>();
        for(String variable:List.of("TIPO_SOLICITUD","TIPO_REQUIRENTE","VIA_INGRESO","REGION_REQUIRENTE","DERECHO","CONTEXTO")){
            String state=switch(variable){
                case "REGION_REQUIRENTE" -> "CASE WHEN "+TerritorialDims.locationSql("c","r",TerritorialDims.Mode.VIGENTE,null,"requester_region")
                    +" IS NULL THEN 'SIN_INFORMACION' ELSE 'INFORMADO' END";
                case "DERECHO","CONTEXTO" -> Rights.consolidatedStateSql("r",variable);
                default -> StatValues.stateSql("c",variable,null);
            };
            result.addAll(db.rows("SELECT '"+variable+"' AS variable,scope_id,stat_state,COUNT(*) AS amount"
                +" FROM (SELECT s.id AS scope_id,"+state+" AS stat_state"+source+") counts"
                +" GROUP BY scope_id,stat_state ORDER BY scope_id,stat_state",args.toArray()));
        }
        return List.copyOf(result);
    }

    private static String marks(int size){return String.join(",",Collections.nCopies(size,"?"));}
}
