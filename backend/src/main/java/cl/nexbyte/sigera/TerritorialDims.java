package cl.nexbyte.sigera;

import java.util.Map;
import org.springframework.stereotype.Service;

/** Dimensiones territoriales para una fila COM, con REQ y SOL opcionales. */
@Service
public class TerritorialDims {
    enum Mode { INGRESO, REQ, VIGENTE, HISTORICO }
    private final Db db;

    public TerritorialDims(Db db){this.db=db;}

    /**
     * Instante en que termina el comando que constituyo el REQ (0.8.0, E08): el primero de command_log con ese resultado.
     * Lo que escribe ese comando (ficha, clasificacion y derechos del acto) pertenece a la constitucion; lo del comando
     * siguiente es siempre posterior (Workflow.afterPreviousCommand).
     */
    static String constitutedSql(String req){
        return "COALESCE((SELECT MIN(l.created_ms) FROM command_log l WHERE l.result_kind='REQ' AND l.result_id="+req
            +".id),"+req+".created_ms)";
    }

    static String versionSql(String com,String req,Mode mode,Long at){
        String cutoff=switch(mode){
            case INGRESO,VIGENTE -> "";
            case REQ -> " AND d.registered_ms<="+constitutedSql(req);
            case HISTORICO -> " AND d.registered_ms<="+java.util.Objects.requireNonNull(at);
        };
        return "(SELECT d.id FROM stat_declared_version d WHERE d.communication_id="+com+".id"+cutoff
            +" ORDER BY d.registered_ms "+(mode==Mode.INGRESO?"ASC,d.id ASC":"DESC,d.id DESC")+" LIMIT 1)";
    }

    static String fieldSql(String com,String group,String key,String personKey){
        return "(SELECT f.value_text FROM external_field f WHERE f.communication_id="+com+".id AND f.field_group='"+group
            +"' AND f.field_key='"+key+"'"+(personKey==null?"":" AND f.person_key="+personKey)+" ORDER BY f.id LIMIT 1)";
    }

    private static String declaredSql(String version,String column){
        return "(SELECT d."+column+" FROM stat_declared_version d WHERE d.id="+version+")";
    }

    static String externalEvidenceSql(String com,String req,Mode mode,Long at){
        return com+".external_source IS NOT NULL"+switch(mode){
            case INGRESO,VIGENTE -> "";
            case REQ -> " AND "+req+".id IS NOT NULL";
            case HISTORICO -> " AND "+com+".received_ms<="+java.util.Objects.requireNonNull(at);
        };
    }

    static String locationSql(String com,String req,Mode mode,Long at,String column){
        String fromForm=switch(column){
            case "requester_region" -> OriginRegion.formularioSql(com);
            case "requester_commune" -> "CASE WHEN "+com+".channel='FORMULARIO_WEB_NNA' THEN "+fieldSql(com,"CAMPO","nna-commune",null)
                +" ELSE "+fieldSql(com,"CAMPO","adult-commune",null)+" END";
            case "facts_region" -> fieldSql(com,"CAMPO","hechos-region",null);
            case "facts_commune" -> fieldSql(com,"CAMPO","hechos-commune",null);
            default -> throw new IllegalArgumentException("Dimensión territorial desconocida.");
        };
        return "CASE WHEN "+externalEvidenceSql(com,req,mode,at)+" THEN "+fromForm
            +" WHEN "+com+".external_source IS NULL THEN "+declaredSql(versionSql(com,req,mode,at),column)+" END";
    }

    private static String firstScope(String com,String req,String sol){
        String firstReq="(SELECT m.scope_id FROM milestone m WHERE m.requirement_id="+req+".id AND m.event='REQ_CREADO'"
            +" ORDER BY m.at_ms,m.id LIMIT 1)";
        String firstSol="(SELECT m.scope_id FROM milestone m WHERE m.communication_id="+com+".id AND m.event='SOL_DISTRIBUIDA'"
            +" ORDER BY m.at_ms,m.id LIMIT 1)";
        firstSol="COALESCE("+firstSol+",CASE WHEN "+sol+".revision=0 THEN "+sol+".destination_scope END)";
        return "CASE WHEN "+req+".id IS NOT NULL THEN "+firstReq+" ELSE "+firstSol+" END";
    }

    static String scopeSql(String com,String req,String sol,Mode mode,Long at){
        if(mode==Mode.INGRESO)return firstScope(com,req,sol);
        if(mode==Mode.VIGENTE)return "COALESCE("+req+".principal_scope,"+sol+".destination_scope)";
        if(mode==Mode.REQ)return "CASE WHEN "+req+".id IS NOT NULL THEN "+firstScope(com,req,sol)+" END";
        String date=String.valueOf(java.util.Objects.requireNonNull(at));
        String currentReq="(SELECT m.scope_id FROM milestone m LEFT JOIN flow_request x ON x.id=m.resource_id"
            +" AND m.resource_type='FLUJO' WHERE m.requirement_id="+req+".id AND m.at_ms<="+date
            +" AND (m.event='REQ_CREADO' OR (m.event='FLUJO_DECIDIDO' AND m.detail='ACEPTADA' AND x.kind='TRANSFERENCIA'))"
            +" ORDER BY m.at_ms DESC,m.id DESC LIMIT 1)";
        String currentSol="(SELECT m.scope_id FROM milestone m WHERE m.communication_id="+com+".id AND m.at_ms<="+date
            +" AND m.event IN ('SOL_DISTRIBUIDA','SOL_REDIRIGIDA') ORDER BY m.at_ms DESC,m.id DESC LIMIT 1)";
        return "COALESCE("+currentReq+","+currentSol+")";
    }

    static String referenceScopeSql(String com,String req,String sol){
        return "COALESCE("+scopeSql(com,req,sol,Mode.VIGENTE,null)+","+com+".origin_scope)";
    }

    static String coverageSql(String region,String at){
        String active="s.regional=TRUE AND s.state<>'PROYECTADA'"
            +" AND NOT EXISTS(SELECT 1 FROM audit_event a WHERE a.resource_type='AMBITO' AND a.resource_id=s.id"
            +" AND ((a.action='AMBITO_ACTIVADO' AND a.at_ms>"+at+") OR (a.action='AMBITO_CERRADO' AND a.at_ms<="+at+")))";
        String period="p.region_code="+region+" AND p.from_ms<="+at+" AND (p.until_ms IS NULL OR p.until_ms>"+at+")";
        return "CASE WHEN "+region+" IS NULL THEN 'SIN_INFORMACION' ELSE COALESCE("
            +"(SELECT s.code FROM org_scope s WHERE "+active+" AND s.region_code="+region+" ORDER BY s.id LIMIT 1),"
            +"(SELECT s.code FROM org_scope s JOIN territorial_coverage p ON p.scope_id=s.id WHERE "+active+" AND "+period
            +" ORDER BY s.id LIMIT 1),CASE WHEN EXISTS(SELECT 1 FROM territorial_coverage p WHERE p.scope_id IS NULL AND "+period
            +") THEN 'NIVEL_CENTRAL' ELSE 'SIN_ATENCION' END) END";
    }

    Map<String,Object> read(long com,Mode mode,Long at){
        return db.one("SELECT "+locationSql("c","r",mode,at,"requester_region")+" AS requester_region,"
            +locationSql("c","r",mode,at,"requester_commune")+" AS requester_commune,"
            +locationSql("c","r",mode,at,"facts_region")+" AS facts_region,"
            +locationSql("c","r",mode,at,"facts_commune")+" AS facts_commune,"
            +OriginRegion.registeredSql("c","r")+" AS origin_region,"
            +scopeSql("c","r","sol",mode,at)+" AS scope_id,"
            +referenceScopeSql("c","r","sol")+" AS reference_scope,"
            +coverageSql(OriginRegion.registeredSql("c","r"),"c.received_ms")+" AS coverage"
            +" FROM communication c LEFT JOIN requirement r ON r.communication_id=c.id"
            +" LEFT JOIN solicitation sol ON sol.communication_id=c.id WHERE c.id=?",com);
    }
}
