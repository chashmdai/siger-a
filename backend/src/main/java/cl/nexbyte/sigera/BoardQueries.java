package cl.nexbyte.sigera;

import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import cl.nexbyte.sigera.Access.Actor;
import static cl.nexbyte.sigera.Db.*;

/** Bandejas paginadas y conteos autorizados en SQL. No carga la cartera completa en memoria. */
@Service
public class BoardQueries {
    final Db db;
    public BoardQueries(Db db){this.db=db;}
    static String ids(Collection<Long> ids){return ids.isEmpty()?"NULL":ids.stream().distinct().map(String::valueOf).collect(Collectors.joining(","));}
    static String at(String column,Collection<Long> ids){return column+" IN ("+ids(ids)+")";}
    final class Visibility {
        final Actor a;final List<Long> heads,professionals,readers,registrars,receivers,regionalHeads;final List<String> matters;
        /** Consulta institucional (D4): lectura de todo, como Access.institutionalReader; nunca abre predicados de gestion. */
        final boolean institutional;
        Visibility(Actor a){
            this.a=a;institutional=a.institutionalReader();var scopes=db.rows("SELECT * FROM org_scope WHERE enabled=TRUE");
            heads=grants(a,"JEFATURA","DIRECCION");professionals=grants(a,"JEFATURA","DIRECCION","PROFESIONAL");readers=grants(a,"LECTURA");registrars=grants(a,"JEFATURA","DIRECCION","PROFESIONAL","ADMINISTRATIVO");
            receivers=scopes.stream().filter(s->b(s,"regional")?a.head(n(s,"id")):s(s,"code").equals("UPR-REQ")?a.at("JEFATURA",n(s,"id")):a.at("DIRECCION",n(s,"id"))).map(s->n(s,"id")).toList();
            regionalHeads=scopes.stream().filter(s->b(s,"regional")&&a.head(n(s,"id"))).map(s->n(s,"id")).toList();
            matters=List.copyOf(a.matters());
        }
        /**
         * Lectura nacional por materia (D9), como Access.matterSol/matterReq/matterCase: el registro tiene una materia de
         * la Direccion y su ambito es una sede (tambien cerrada). Los codigos salen del catalogo fijo, nunca del navegador.
         */
        String byMatter(String column,String scopeColumn){
            var codes=matters.stream().filter(Access.NATIONAL_MATTERS::contains).map(m->"'"+m+"'").toList();
            if(codes.isEmpty())return "1=0";
            return "("+column+" IN ("+String.join(",",codes)+") AND EXISTS(SELECT 1 FROM org_scope mo WHERE mo.id="+scopeColumn+" AND mo.regional=TRUE))";
        }
        String caseRead(String c){
            if(institutional)return "1=1";
            return "("+at(c+".scope_id",heads)+" OR "+at(c+".scope_id",readers)+" OR ("+at(c+".scope_id",professionals)+" AND ("+c+".responsible_id="+a.id()+" OR EXISTS(SELECT 1 FROM case_member vm WHERE vm.case_id="+c+".id AND vm.user_id="+a.id()+"))) OR "+byMatter(c+".matter",c+".scope_id")+")";
        }
        String matterReq(String r){return byMatter(r+".matter",r+".principal_scope");}
        String matterSol(String s){return byMatter(s+".suggested_matter",s+".destination_scope");}
        /** COM de una cadena legible por materia: por su SOL o su REQ, nunca solo por un Caso (Access.matterCom). */
        String matterCom(String c){
            if(matters.isEmpty())return "1=0";
            return "(EXISTS(SELECT 1 FROM solicitation ms WHERE ms.communication_id="+c+".id AND "+matterSol("ms")+") OR EXISTS(SELECT 1 FROM requirement mr WHERE mr.communication_id="+c+".id AND "+matterReq("mr")+"))";
        }
        String reqMain(String r){return "("+at(r+".principal_scope",heads)+" OR ("+r+".attention_user="+a.id()+" AND "+at(r+".attention_scope",registrars)+"))";}
        String attentionReceiver(String r){return "EXISTS(SELECT 1 FROM attention_handoff ha WHERE ha.requirement_id="+r+".id AND ha.state='PENDIENTE' AND ((ha.recipient_kind='JEFATURA' AND "+at("ha.target_scope",receivers)+") OR (ha.recipient_kind='PROFESIONAL' AND ha.target_user="+a.id()+" AND "+at("ha.target_scope",professionals)+")))";}
        String reqRead(String r){if(institutional)return "1=1";return "("+matterReq(r)+" OR "+attentionReceiver(r)+" OR ("+r+".created_by="+a.id()+" AND EXISTS(SELECT 1 FROM attention_record ar WHERE ar.requirement_id="+r+".id)) OR "+reqMain(r)+" OR "+at(r+".principal_scope",readers)+" OR EXISTS(SELECT 1 FROM case_record vc WHERE vc.requirement_id="+r+".id AND "+caseRead("vc")+"))";}
        String distribution(String c){return "("+at(c+".origin_scope",regionalHeads)+(a.role("DISTRIBUIDOR")?" OR EXISTS(SELECT 1 FROM org_scope vo WHERE vo.id="+c+".origin_scope AND vo.regional=FALSE)":"")+")";}
        String comRead(String c){if(institutional)return "1=1";return "("+c+".created_by="+a.id()+" OR "+distribution(c)+" OR "+matterCom(c)+" OR EXISTS(SELECT 1 FROM solicitation vs WHERE vs.communication_id="+c+".id AND vs.state='PENDIENTE' AND "+at("vs.destination_scope",receivers)+") OR EXISTS(SELECT 1 FROM requirement vr WHERE vr.communication_id="+c+".id AND ("+reqMain("vr")+" OR "+attentionReceiver("vr")+")) OR EXISTS(SELECT 1 FROM communication_link vl JOIN requirement lr ON lr.id=vl.requirement_id WHERE vl.communication_id="+c+".id AND vl.returned_ms IS NULL AND ("+at("lr.principal_scope",heads)+" OR (vl.state='ADMITIDA' AND ((vl.target_case_id IS NULL AND "+reqMain("lr")+") OR EXISTS(SELECT 1 FROM case_record lc WHERE lc.id=vl.target_case_id AND "+caseRead("lc")+"))))))";}
        /** SOL: la receptora ve las pendientes y Distribucion las devueltas; la consulta institucional, todas; la Direccion, las de su materia en sedes (todo estado). */
        String solRead(String s,String c){return institutional?"1=1":"(("+s+".state='PENDIENTE' AND "+at(s+".destination_scope",receivers)+") OR ("+s+".state='DEVUELTA' AND "+distribution(c)+") OR "+matterSol(s)+")";}
        /**
         * Ficha de primera atencion del REQ, como Attention.mayRead: lectura amplia (consulta o materia del REQ), quien la
         * gestiona, el destinatario de una entrega pendiente o la jefatura o un integrante de un Caso del REQ
         * (Access.caseSourceDocuments). Con un REQ nulo (LEFT JOIN) es falso.
         */
        String attentionRead(String r){
            if(institutional)return "1=1";
            return "("+matterReq(r)+" OR "+reqMain(r)+" OR "+attentionReceiver(r)+" OR EXISTS(SELECT 1 FROM case_record sc WHERE sc.requirement_id="+r+".id AND ("+at("sc.scope_id",heads)+" OR ("+at("sc.scope_id",professionals)+" AND (sc.responsible_id="+a.id()+" OR EXISTS(SELECT 1 FROM case_member sm WHERE sm.case_id=sc.id AND sm.user_id="+a.id()+"))))))";
        }
        /** SOL de una cadena (ficha de la COM, buscador), como Queries.solVisible: la de la bandeja o la que origino un REQ legible. */
        String solChain(String s,String c){
            if(institutional)return "1=1";
            return "("+solRead(s,c)+" OR EXISTS(SELECT 1 FROM requirement sr WHERE sr.solicitation_id="+s+".id AND "+reqRead("sr")+"))";
        }
        String flowRead(String f){return institutional?"1=1":"("+at(f+".origin_scope",heads)+" OR "+at(f+".target_scope",heads)+" OR EXISTS(SELECT 1 FROM requirement fr WHERE fr.id="+f+".requirement_id AND "+matterReq("fr")+"))";}
    }
    /** Predicados de lectura de la cuenta, para otras consultas autorizadas (buscador). */
    Visibility visibility(Actor a){return new Visibility(a);}
    static List<Long> grants(Actor a,String...roles){var wanted=Set.of(roles);return a.grants().stream().filter(g->g.scope()!=null&&wanted.contains(g.role())).map(Access.Grant::scope).distinct().toList();}
    private record Spec(String select,String from,String permission,String scope,String search,String order){}
    /**
     * Marca territorial y de riesgo de la bandeja de comunicaciones (0.7.0, D23; gate T03b), solo presentacion:
     * origin_region = region de origen vigente de la COM con la regla del selector de la COM (Access.suggestedRegion(id,null):
     * Formulario nna-region y luego adult-region; nunca la ficha, de lectura protegida), risk = el primer campo CAMPO
     * riesgo del Formulario es afirmativo (si o true sin distinguir mayusculas; se decide en Java porque MySQL compara sin
     * tildes) y to_distribute = la COM cumple la condicion del filtro "Por distribuir" (la misma expresion). El valor del
     * campo no sale del servidor.
     */
    static final String RISK_SQL="(SELECT rf.value_text FROM external_field rf WHERE rf.id=(SELECT MIN(rg.id) FROM external_field rg WHERE rg.communication_id=c.id AND rg.field_group='CAMPO' AND rg.field_key='riesgo'))";
    static boolean riskAffirmative(Object value){return value instanceof String v&&List.of("si","true").contains(v.toLowerCase(Locale.ROOT));}
    private static Map<String,Object> comMark(Map<String,Object> row){
        var out=new LinkedHashMap<String,Object>(row);
        out.put("risk",riskAffirmative(out.remove("risk_value")));out.put("to_distribute",b(row,"to_distribute"));
        return out;
    }
    private Spec spec(Visibility v,String kind){
        Actor a=v.a;
        return switch(kind){
            case "atenciones"->new Spec("r.id,r.folio,r.principal_scope,r.attention_user,r.attention_scope,r.created_by,r.created_ms,r.revision,r.state AS req_state,c.subject,t.modality,t.updated_ms,"+Attention.stateSql("r","t")+" AS state", "requirement r JOIN attention_record t ON t.requirement_id=r.id JOIN communication c ON c.id=r.communication_id",v.reqRead("r"),"r.principal_scope","LOWER(CONCAT(COALESCE(r.folio,''),' ',c.subject))","t.updated_ms DESC,r.id DESC");
            // Marca de la bandeja (D23): solo columnas de lectura; condiciones, filtros, orden y visibilidad no cambian.
            case "com"->new Spec("c.id,c.folio,c.subject,c.channel,c.state,c.origin_scope,c.created_ms,c.received_ms,c.revision,c.created_by,"
                +OriginRegion.formularioSql("c")+" AS origin_region,"+RISK_SQL+" AS risk_value,CASE WHEN ("+filter(v,"com","pending")+") THEN 1 ELSE 0 END AS to_distribute","communication c",v.comRead("c"),"c.origin_scope","LOWER(CONCAT(COALESCE(c.folio,''),' ',c.subject))","c.id DESC");
            case "sol"->new Spec("s.id,s.communication_id,s.destination_scope,s.requested_area_scope,s.state,s.created_ms,s.revision,c.folio AS com_folio,c.subject","solicitation s JOIN communication c ON c.id=s.communication_id",v.solRead("s","c"),"s.destination_scope","LOWER(CONCAT(COALESCE(c.folio,''),' ',c.subject))","s.created_ms,s.id");
            case "req"->new Spec("r.id,r.folio,r.communication_id,r.principal_scope,r.attention_user,r.attention_scope,r.complexity,r.state,r.created_ms,r.revision,(SELECT COUNT(*) FROM case_record rc WHERE rc.requirement_id=r.id AND rc.state<>'CERRADO') AS open_cases,(SELECT COUNT(*) FROM case_record tc WHERE tc.requirement_id=r.id) AS total_cases","requirement r",v.reqRead("r"),"r.principal_scope","LOWER(COALESCE(r.folio,''))","r.id DESC");
            case "cas"->new Spec("c.id,c.folio,c.requirement_id,c.scope_id,c.responsible_id,c.objective,c.state,c.created_ms,c.revision,r.folio AS req_folio,r.complexity AS req_complexity,(SELECT COUNT(*) FROM task_item t WHERE t.case_id=c.id AND t.state IN ('PENDIENTE','EN_CURSO')) AS pending_tasks","case_record c JOIN requirement r ON r.id=c.requirement_id",v.caseRead("c"),"c.scope_id","LOWER(CONCAT(COALESCE(c.folio,''),' ',COALESCE(r.folio,''),' ',c.objective))","c.id DESC");
            case "flujos"->new Spec("f.id,f.kind,f.requirement_id,f.origin_case_id,f.origin_scope,f.target_scope,f.state,f.created_ms,f.revision,r.folio AS req_folio","flow_request f JOIN requirement r ON r.id=f.requirement_id",v.flowRead("f"),"f.target_scope","LOWER(CONCAT(COALESCE(r.folio,''),' ',f.kind))","f.created_ms,f.id");
            case "tareas"->new Spec("t.id,t.case_id,t.title,t.executor_id,t.state,t.due_ms,t.next_reminder_ms,t.reminder_interval_hours,t.revision,c.folio AS case_folio,c.scope_id","task_item t JOIN case_record c ON c.id=t.case_id","t.executor_id="+a.id()+" AND "+v.caseRead("c"),"c.scope_id","LOWER(CONCAT(t.title,' ',COALESCE(c.folio,'')))","CASE WHEN t.due_ms IS NULL THEN 1 ELSE 0 END,t.due_ms,t.id");
            case "avisos"->new Spec("i.id,i.at_ms,i.text_value,i.resource_type,i.resource_id,i.read_ms","inbox_notice i","i.user_id="+a.id(),null,"LOWER(i.text_value)","i.id DESC");
            default->throw new Problem(400,"Bandeja desconocida.");
        };
    }
    private String filter(Visibility v,String kind,String value){
        String f=value==null?"":value;long now=System.currentTimeMillis();
        if(f.isBlank()||f.equals("all"))return "1=1";
        return switch(kind+":"+f){
            case "atenciones:mine"->"r.attention_user="+v.a.id()+" AND r.state='ABIERTO' AND NOT EXISTS(SELECT 1 FROM case_record ac WHERE ac.requirement_id=r.id)";
            case "atenciones:incoming"->v.attentionReceiver("r");
            case "atenciones:outgoing"->"EXISTS(SELECT 1 FROM attention_handoff ah WHERE ah.requirement_id=r.id AND ah.sent_by="+v.a.id()+" AND ah.state='PENDIENTE')";
            case "atenciones:created"->"r.created_by="+v.a.id();
            case "atenciones:followup"->"r.state='ABIERTO' AND t.followup_required=TRUE AND "+at("r.principal_scope",v.heads)+" AND NOT EXISTS(SELECT 1 FROM case_record ac WHERE ac.requirement_id=r.id)";
            case "atenciones:closed"->"r.state='CERRADO'";
            case "com:pending"->"c.state='NUEVA' AND "+v.distribution("c");
            case "com:mine"->"c.created_by="+v.a.id();
            // Con datos estadisticos pendientes (E13): la misma regla de COMPLETITUD del motor, sobre lo que la bandeja ya deja leer.
            case "com:stat_pending","sol:stat_pending"->statPending("c",null);
            case "req:stat_pending"->"EXISTS(SELECT 1 FROM communication pc WHERE pc.id=r.communication_id AND "+statPending("pc","r")+")";
            // Filtros de decision propia: con la consulta institucional el permiso ya no los acota.
            case "sol:pending"->"s.state='PENDIENTE' AND "+at("s.destination_scope",v.receivers);
            case "sol:returned"->"s.state='DEVUELTA' AND "+v.distribution("c");
            case "req:open"->"r.state='ABIERTO'";
            case "req:closed"->"r.state='CERRADO'";
            case "req:without_case"->"r.state='ABIERTO' AND NOT EXISTS(SELECT 1 FROM case_record wc WHERE wc.requirement_id=r.id)";
            case "req:ready"->ready("r",v);
            case "cas:open"->"c.state<>'CERRADO'";
            case "cas:closed"->"c.state='CERRADO'";
            case "cas:mine"->"c.responsible_id="+v.a.id()+" AND c.state<>'CERRADO'";
            case "cas:review"->"c.state='EN_REVISION' AND "+at("c.scope_id",v.heads);
            case "cas:unacknowledged"->"c.responsible_id="+v.a.id()+" AND c.state<>'CERRADO' AND EXISTS(SELECT 1 FROM case_assignment ca WHERE ca.case_id=c.id AND ca.current_flag=TRUE AND ca.acknowledged_ms IS NULL)";
            case "cas:returned"->"c.responsible_id="+v.a.id()+" AND c.state='EN_GESTION' AND EXISTS(SELECT 1 FROM audit_event ae WHERE ae.resource_type='CAS' AND ae.resource_id=c.id AND ae.action='CASE_RETURN' AND NOT EXISTS(SELECT 1 FROM audit_event later WHERE later.resource_type='CAS' AND later.resource_id=c.id AND later.id>ae.id AND later.action IN ('CASE_REQUEST_CLOSE','CASE_WITHDRAW_CLOSE','CASO_CERRADO_JEFATURA','CASO_CERRADO_PROFESIONAL_BAJA','CASO_CERRADO_JEFATURA_PROPIA')))";
            case "flujos:pending"->"f.state='PENDIENTE'";
            case "flujos:incoming"->"f.state='PENDIENTE' AND "+at("f.target_scope",v.heads);
            case "flujos:outgoing"->"f.state='PENDIENTE' AND "+at("f.origin_scope",v.heads);
            case "tareas:pending"->"t.state IN ('PENDIENTE','EN_CURSO')";
            case "tareas:overdue"->"t.state IN ('PENDIENTE','EN_CURSO') AND t.due_ms<"+now;
            case "tareas:soon"->"t.state IN ('PENDIENTE','EN_CURSO') AND t.due_ms>="+now+" AND t.due_ms<="+(now+7*86400000L);
            case "avisos:unread"->"i.read_ms IS NULL";
            default->throw new Problem(400,"Filtro de bandeja desconocido.");
        };
    }
    /**
     * Filtro "Con datos estadisticos pendientes" (0.8.0, gate E13; precision del GO): COM computable (ni INVALIDA ni
     * VINCULADA) con alguna clasificacion en PENDIENTE entre las que tienen catalogo VIGENTE: tipo de solicitud, tipo de
     * requirente y via (StatValues.stateSql, con la via derivada del canal) y, en el REQ, derecho y contexto consolidados
     * del REQ y de todos sus Casos (Rights.consolidatedStateSql). La region del requirente no entra: en el modo vigente
     * su falta es SIN_INFORMACION, no PENDIENTE. Sin catalogos vigentes no muestra nada. Es la misma regla que el estado
     * PENDIENTE de la medida COMPLETITUD de StatSource: con una cuenta cuya lectura y alcance de Reportes coinciden, las
     * cifras son iguales (StatPendingFilterTest).
     */
    String statPending(String c,String req){
        var current=new HashSet<String>();
        for(var row:db.rows("SELECT `current_catalog` AS name FROM stat_catalog_version WHERE `current_catalog` IS NOT NULL"))current.add(s(row,"name"));
        var pending=new ArrayList<String>();
        for(String variable:List.of("TIPO_SOLICITUD","TIPO_REQUIRENTE","VIA_INGRESO"))
            if(current.contains(variable))pending.add(StatValues.stateSql(c,variable,null)+"='"+StatValues.PENDING+"'");
        if(req!=null)for(String variable:List.of("DERECHO","CONTEXTO"))
            if(current.contains(variable))pending.add(Rights.consolidatedStateSql(req,variable)+"='"+StatValues.PENDING+"'");
        if(pending.isEmpty())return "1=0";
        return c+".state NOT IN ('INVALIDA','VINCULADA') AND ("+String.join(" OR ",pending)+")";
    }
    private String ready(String r,Visibility v){return r+".state='ABIERTO' AND "+at(r+".principal_scope",v.heads)+" AND NOT EXISTS(SELECT 1 FROM case_record cc WHERE cc.requirement_id="+r+".id AND cc.state<>'CERRADO') AND NOT EXISTS(SELECT 1 FROM flow_request ff WHERE ff.requirement_id="+r+".id AND ff.state='PENDIENTE') AND NOT EXISTS(SELECT 1 FROM reopening rr WHERE rr.requirement_id="+r+".id AND rr.state='PENDIENTE') AND NOT EXISTS(SELECT 1 FROM attention_handoff hh WHERE hh.requirement_id="+r+".id AND hh.state='PENDIENTE') AND NOT EXISTS(SELECT 1 FROM attention_record af WHERE af.requirement_id="+r+".id AND af.followup_required=TRUE AND NOT EXISTS(SELECT 1 FROM case_record cf WHERE cf.requirement_id="+r+".id)) AND NOT EXISTS(SELECT 1 FROM task_item tt JOIN case_record tc ON tc.id=tt.case_id WHERE tc.requirement_id="+r+".id AND tt.state IN ('PENDIENTE','EN_CURSO'))";}
    public Map<String,Object> page(Actor a,String kind,int page,int size,String q,String state,Long scope){return page(new Visibility(a),kind,page,size,q,state,scope);}
    private Map<String,Object> page(Visibility v,String kind,int page,int size,String q,String state,Long scope){
        Problem.require(page>=0&&page<=100000&&size>0&&size<=100,"Pagina fuera de rango.");
        Spec s=spec(v,kind);var args=new ArrayList<Object>();String where="("+s.permission()+") AND ("+filter(v,kind,state)+")";
        if(q!=null&&!q.isBlank()){
            Problem.require(q.length()<=160,"Busqueda demasiado extensa.");
            where+=" AND "+s.search()+" LIKE ? ESCAPE '!'";
            args.add("%"+q.strip().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%");
        }
        if(scope!=null&&s.scope()!=null){where+=" AND "+s.scope()+"=?";args.add(scope);}
        String from=" FROM "+s.from()+" WHERE "+where;
        long total=db.count("SELECT COUNT(*)"+from,args.toArray());
        int actual=(int)Math.min(page,Math.max(0,(total-1)/size));args.add(size);args.add((long)actual*size);
        var rows=db.rows("SELECT "+s.select()+from+" ORDER BY "+s.order()+" LIMIT ? OFFSET ?",args.toArray());
        if(kind.equals("com"))rows=rows.stream().map(BoardQueries::comMark).toList();
        return Map.of("items",rows,"total",total,"page",actual,"size",size);
    }
    private long count(Visibility v,String kind,String filter){var s=spec(v,kind);return db.count("SELECT COUNT(*) FROM "+s.from()+" WHERE ("+s.permission()+") AND ("+filter(v,kind,filter)+")");}
    private Map<String,Object> metric(Visibility v,String label,String kind,String filter,String hint){return Map.of("label",label,"count",count(v,kind,filter),"route",kind,"filter",filter,"hint",hint);}
    private Map<String,Object> section(String id,String title,String help,List<Map<String,Object>> metrics){return Map.of("id",id,"title",title,"help",help,"metrics",metrics);}
    private Map<String,Object> queue(Visibility v,String title,String kind,String f){return Map.of("title",title,"route",kind,"filter",f,"page",page(v,kind,0,5,"",f,null));}
    public Map<String,Object> dashboard(Actor a){
        var v=new Visibility(a);var sections=new ArrayList<Map<String,Object>>();var queues=new ArrayList<Map<String,Object>>();
        boolean head=!v.heads.isEmpty(),professional=a.role("PROFESIONAL")||head,operative=professional||a.role("ADMINISTRATIVO")||a.role("DISTRIBUIDOR");
        if(a.role("DISTRIBUIDOR")||!v.regionalHeads.isEmpty()){
            sections.add(section("distribution","Clasificacion y distribucion","Encamina cada ingreso a una sola autoridad receptora.",List.of(metric(v,"COM por distribuir","com","pending","Sin destino funcional"),metric(v,"SOL devueltas","sol","returned","Necesitan nueva clasificacion"),metric(v,"Avisos sin leer","avisos","unread","No equivalen a gestion"))));
            queues.add(queue(v,"Ingresos por distribuir","com","pending"));
        }
        if(head){
            String title=!v.regionalHeads.isEmpty()?"Coordinacion de mi sede":a.role("DIRECCION")?"Direccion de mi unidad":"Coordinacion de Requerimientos";
            sections.add(section("management",title,"Recibir, asignar, decidir y cerrar dentro de tus ambitos vigentes.",List.of(metric(v,"SOL por asumir","sol","pending","Todavia no son REQ"),metric(v,"Cierres por revisar","cas","review","Leer fundamento y condiciones"),metric(v,"Decisiones entrantes","flujos","incoming","Colaboracion o traslado"),metric(v,"REQ sin bloqueos","req","ready","Cierre expreso; no automatico"))));
            queues.add(queue(v,"Solicitudes que requieren tu decision","sol","pending"));queues.add(queue(v,"Cierres para revisar","cas","review"));
        }
        if(professional){
            sections.add(section("personal","Mi trabajo profesional","Tu cartera personal permanece separada de las funciones de jefatura.",List.of(metric(v,"Mis Casos abiertos","cas","mine","Responsabilidad principal"),metric(v,"Asignaciones sin acuse","cas","unacknowledged","Puedes gestionar antes de acusar"),metric(v,"Cierres devueltos","cas","returned","Revisar observaciones"),metric(v,"Recordatorios vencidos","tareas","overdue","Fecha personal, no SLA institucional"))));
            queues.add(queue(v,"Proximos recordatorios (7 dias)","tareas","soon"));
        }
        if(!v.registrars.isEmpty()){
            sections.add(section("intake","Atencion ciudadana","Un REQ desde el primer contacto; datos parciales y entregas sin una SOL artificial.",List.of(metric(v,"Atenciones a mi cargo","atenciones","mine","Continuar ficha y registrar resultado"),metric(v,"Entregas por recibir","atenciones","incoming","Recibir o devolver con fundamento"),metric(v,"Entregas enviadas","atenciones","outgoing","Aun bajo encargo de origen"),head?metric(v,"Seguimiento por asignar","atenciones","followup","Jefatura abre Caso en el mismo REQ"):metric(v,"Atenciones iniciadas por mi","atenciones","created","Seguimiento de tus entregas"))));
            queues.add(queue(v,"Primeras atenciones por recibir","atenciones","incoming"));queues.add(queue(v,"Atenciones a mi cargo","atenciones","mine"));
        }
        if(v.institutional)sections.add(section("institutional","Consulta institucional","Lectura de todos los registros, sin facultades de gestion. No incluye constancias ni documentos reservados.",List.of(metric(v,"Comunicaciones","com","all","Todas las COM registradas"),metric(v,"REQ abiertos","req","open","En toda la institucion"),metric(v,"Casos abiertos","cas","open","En toda la institucion"),metric(v,"Peticiones entre unidades pendientes","flujos","pending","Colaboraciones, derivaciones y transferencias"))));
        // Con la consulta institucional el resumen "de mi alcance" seria institucional: lo reemplaza la seccion anterior.
        else if(operative||a.role("LECTURA"))sections.add(section("overview","Resumen de mi alcance","Estas cifras incluyen solo registros que puedes consultar. No son totales institucionales ni indicadores oficiales.",List.of(metric(v,"Comunicaciones visibles","com","all","Incluye antecedentes"),metric(v,"REQ abiertos","req","open","Una atencion, varios Casos posibles"),metric(v,"Casos abiertos","cas","open","Lineas de gestion"),metric(v,"Casos cerrados","cas","closed","Historia conservada"))));
        if(a.role(Roles.TI)){
            // TI administra cuentas, funciones y subrogancias de toda la instancia (D1); no concede lectura de expedientes.
            long now=System.currentTimeMillis();
            long active=db.count("SELECT COUNT(*) FROM substitution x JOIN substitution_designation d ON d.id=x.designation_id WHERE d.enabled=TRUE AND x.ended_ms IS NULL AND x.from_ms<=? AND x.until_ms>?",now,now);
            long future=db.count("SELECT COUNT(*) FROM substitution x JOIN substitution_designation d ON d.id=x.designation_id WHERE d.enabled=TRUE AND x.ended_ms IS NULL AND x.from_ms>?",now);
            sections.add(section("technical","Administracion","Cuentas, funciones, subrogancias y operacion. Este perfil no concede lectura de expedientes.",List.of(
                Map.of("label","Subrogancias activas","count",active,"route","subrogancias","filter","","hint","Periodo vigente; la cartera no se transfiere"),
                Map.of("label","Subrogancias programadas","count",future,"route","subrogancias","filter","","hint","Inicio futuro"),
                Map.of("label","Cuentas habilitadas","count",db.count("SELECT COUNT(*) FROM staff WHERE enabled=TRUE AND technical=FALSE"),"route","administracion","filter","","hint","Sin credenciales ni contenido"),
                Map.of("label","Archivos en cuarentena","count",db.count("SELECT COUNT(*) FROM document_item WHERE quarantined=TRUE"),"route","auditoria","filter","","hint","Solo metadatos tecnicos"))));
        }
        if(a.role("AUDITORIA"))sections.add(section("audit","Auditoria autorizada","Consulta de trazabilidad limitada a los ambitos concedidos.",List.of(Map.of("label","Eventos en mi alcance","count",db.count("SELECT COUNT(*) FROM audit_event WHERE scope_id IN ("+ids(grants(a,"AUDITORIA"))+")"),"route","auditoria","filter","","hint","No concede acceso al expediente"))));
        if(sections.isEmpty())sections.add(section("personal","Mi espacio","No hay una funcion operativa asignada. Contacta a TI.",List.of(metric(v,"Avisos sin leer","avisos","unread","Informacion personal"))));
        return Map.of("sections",sections,"queues",queues,"generated_ms",System.currentTimeMillis(),"substituting",!a.substitutions().isEmpty());
    }
}
