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
        Visibility(Actor a){
            this.a=a;var scopes=db.rows("SELECT * FROM org_scope WHERE enabled=TRUE");
            heads=grants(a,"JEFATURA","DIRECCION");professionals=grants(a,"JEFATURA","DIRECCION","PROFESIONAL");readers=grants(a,"LECTURA");registrars=grants(a,"JEFATURA","DIRECCION","PROFESIONAL","ADMINISTRATIVO");
            receivers=scopes.stream().filter(s->b(s,"regional")?a.head(n(s,"id")):s(s,"code").equals("UPR-REQ")?a.at("JEFATURA",n(s,"id")):a.at("DIRECCION",n(s,"id"))).map(s->n(s,"id")).toList();
            regionalHeads=scopes.stream().filter(s->b(s,"regional")&&a.head(n(s,"id"))).map(s->n(s,"id")).toList();
            matters=scopes.stream().filter(s->a.at("DIRECCION",n(s,"id"))&&Set.of("UPR","UPD").contains(s(s,"unit_code"))).map(s->s(s,"unit_code")).distinct().toList();
        }
        String caseRead(String c){
            String transverse=matters.isEmpty()?"1=0":"("+c+".matter IN ("+matters.stream().map(m->"'"+m+"'").collect(Collectors.joining(","))+") AND EXISTS(SELECT 1 FROM org_scope vs WHERE vs.id="+c+".scope_id AND vs.regional=TRUE AND vs.enabled=TRUE))";
            return "("+at(c+".scope_id",heads)+" OR "+at(c+".scope_id",readers)+" OR ("+at(c+".scope_id",professionals)+" AND ("+c+".responsible_id="+a.id()+" OR EXISTS(SELECT 1 FROM case_member vm WHERE vm.case_id="+c+".id AND vm.user_id="+a.id()+"))) OR "+transverse+")";
        }
        String reqMain(String r){return "("+at(r+".principal_scope",heads)+" OR ("+r+".attention_user="+a.id()+" AND "+at(r+".attention_scope",registrars)+"))";}
        String attentionReceiver(String r){return "EXISTS(SELECT 1 FROM attention_handoff ha WHERE ha.requirement_id="+r+".id AND ha.state='PENDIENTE' AND ((ha.recipient_kind='JEFATURA' AND "+at("ha.target_scope",receivers)+") OR (ha.recipient_kind='PROFESIONAL' AND ha.target_user="+a.id()+" AND "+at("ha.target_scope",professionals)+")))";}
        String reqRead(String r){return "("+attentionReceiver(r)+" OR ("+r+".created_by="+a.id()+" AND EXISTS(SELECT 1 FROM attention_record ar WHERE ar.requirement_id="+r+".id)) OR "+reqMain(r)+" OR "+at(r+".principal_scope",readers)+" OR EXISTS(SELECT 1 FROM case_record vc WHERE vc.requirement_id="+r+".id AND "+caseRead("vc")+"))";}
        String distribution(String c){return "("+at(c+".origin_scope",regionalHeads)+(a.role("DISTRIBUIDOR")?" OR EXISTS(SELECT 1 FROM org_scope vo WHERE vo.id="+c+".origin_scope AND vo.regional=FALSE)":"")+")";}
        String comRead(String c){return "("+c+".created_by="+a.id()+" OR "+distribution(c)+" OR EXISTS(SELECT 1 FROM solicitation vs WHERE vs.communication_id="+c+".id AND vs.state='PENDIENTE' AND "+at("vs.destination_scope",receivers)+") OR EXISTS(SELECT 1 FROM requirement vr WHERE vr.communication_id="+c+".id AND ("+reqMain("vr")+" OR "+attentionReceiver("vr")+")) OR EXISTS(SELECT 1 FROM communication_link vl JOIN requirement lr ON lr.id=vl.requirement_id WHERE vl.communication_id="+c+".id AND vl.returned_ms IS NULL AND ("+at("lr.principal_scope",heads)+" OR (vl.state='ADMITIDA' AND ((vl.target_case_id IS NULL AND "+reqMain("lr")+") OR EXISTS(SELECT 1 FROM case_record lc WHERE lc.id=vl.target_case_id AND "+caseRead("lc")+"))))))";}
    }
    static List<Long> grants(Actor a,String...roles){var wanted=Set.of(roles);return a.grants().stream().filter(g->g.scope()!=null&&wanted.contains(g.role())).map(Access.Grant::scope).distinct().toList();}
    private record Spec(String select,String from,String permission,String scope,String search,String order){}
    private Spec spec(Visibility v,String kind){
        Actor a=v.a;
        return switch(kind){
            case "atenciones"->new Spec("r.id,r.folio,r.principal_scope,r.attention_user,r.attention_scope,r.created_by,r.created_ms,r.revision,r.state AS req_state,c.subject,t.modality,t.updated_ms,"+Attention.stateSql("r","t")+" AS state", "requirement r JOIN attention_record t ON t.requirement_id=r.id JOIN communication c ON c.id=r.communication_id",v.reqRead("r"),"r.principal_scope","LOWER(CONCAT(COALESCE(r.folio,''),' ',c.subject))","t.updated_ms DESC,r.id DESC");
            case "com"->new Spec("c.id,c.folio,c.subject,c.channel,c.state,c.origin_scope,c.created_ms,c.received_ms,c.revision,c.created_by","communication c",v.comRead("c"),"c.origin_scope","LOWER(CONCAT(COALESCE(c.folio,''),' ',c.subject))","c.id DESC");
            case "sol"->new Spec("s.id,s.communication_id,s.destination_scope,s.requested_area_scope,s.state,s.created_ms,s.revision,c.folio AS com_folio,c.subject","solicitation s JOIN communication c ON c.id=s.communication_id","((s.state='PENDIENTE' AND "+at("s.destination_scope",v.receivers)+") OR (s.state='DEVUELTA' AND "+v.distribution("c")+"))","s.destination_scope","LOWER(CONCAT(COALESCE(c.folio,''),' ',c.subject))","s.created_ms,s.id");
            case "req"->new Spec("r.id,r.folio,r.communication_id,r.principal_scope,r.attention_user,r.attention_scope,r.complexity,r.state,r.created_ms,r.revision,(SELECT COUNT(*) FROM case_record rc WHERE rc.requirement_id=r.id AND rc.state<>'CERRADO') AS open_cases,(SELECT COUNT(*) FROM case_record tc WHERE tc.requirement_id=r.id) AS total_cases","requirement r",v.reqRead("r"),"r.principal_scope","LOWER(COALESCE(r.folio,''))","r.id DESC");
            case "cas"->new Spec("c.id,c.folio,c.requirement_id,c.scope_id,c.responsible_id,c.objective,c.state,c.created_ms,c.revision,r.folio AS req_folio,r.complexity AS req_complexity,(SELECT COUNT(*) FROM task_item t WHERE t.case_id=c.id AND t.state IN ('PENDIENTE','EN_CURSO')) AS pending_tasks","case_record c JOIN requirement r ON r.id=c.requirement_id",v.caseRead("c"),"c.scope_id","LOWER(CONCAT(COALESCE(c.folio,''),' ',COALESCE(r.folio,''),' ',c.objective))","c.id DESC");
            case "flujos"->new Spec("f.id,f.kind,f.requirement_id,f.origin_case_id,f.origin_scope,f.target_scope,f.state,f.created_ms,f.revision,r.folio AS req_folio","flow_request f JOIN requirement r ON r.id=f.requirement_id","("+at("f.origin_scope",v.heads)+" OR "+at("f.target_scope",v.heads)+")","f.target_scope","LOWER(CONCAT(COALESCE(r.folio,''),' ',f.kind))","f.created_ms,f.id");
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
            case "sol:pending"->"s.state='PENDIENTE'";
            case "sol:returned"->"s.state='DEVUELTA'";
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
        if(operative||a.role("LECTURA"))sections.add(section("overview","Resumen de mi alcance","Estas cifras incluyen solo registros que puedes consultar. No son totales institucionales ni indicadores oficiales.",List.of(metric(v,"Comunicaciones visibles","com","all","Incluye antecedentes"),metric(v,"REQ abiertos","req","open","Una atencion, varios Casos posibles"),metric(v,"Casos abiertos","cas","open","Lineas de gestion"),metric(v,"Casos cerrados","cas","closed","Historia conservada"))));
        if(a.role("AUTORIDAD")){
            String scopes=ids(grants(a,"AUTORIDAD"));long now=System.currentTimeMillis();
            long active=db.count("SELECT COUNT(*) FROM substitution x JOIN substitution_designation d ON d.id=x.designation_id WHERE d.scope_id IN ("+scopes+") AND d.enabled=TRUE AND x.ended_ms IS NULL AND x.from_ms<=? AND x.until_ms>?",now,now);
            long future=db.count("SELECT COUNT(*) FROM substitution x JOIN substitution_designation d ON d.id=x.designation_id WHERE d.scope_id IN ("+scopes+") AND d.enabled=TRUE AND x.ended_ms IS NULL AND x.from_ms>?",now);
            sections.add(section("authority","Autoridad habilitante","Activa funciones predesignadas; no se transfiere la cartera.",List.of(Map.of("label","Subrogancias activas","count",active,"route","subrogancias","filter","","hint","Periodo vigente"),Map.of("label","Subrogancias programadas","count",future,"route","subrogancias","filter","","hint","Inicio futuro"))));
        }
        if(a.role("TI"))sections.add(section("technical","Administracion tecnica","Usuarios y operacion. Este perfil no concede lectura de expedientes.",List.of(Map.of("label","Cuentas habilitadas","count",db.count("SELECT COUNT(*) FROM staff WHERE enabled=TRUE"),"route","administracion","filter","","hint","Sin credenciales ni contenido"),Map.of("label","Archivos en cuarentena","count",db.count("SELECT COUNT(*) FROM document_item WHERE quarantined=TRUE"),"route","auditoria","filter","","hint","Solo metadatos tecnicos"))));
        if(a.role("AUDITORIA"))sections.add(section("audit","Auditoria autorizada","Consulta de trazabilidad limitada a los ambitos concedidos.",List.of(Map.of("label","Eventos en mi alcance","count",db.count("SELECT COUNT(*) FROM audit_event WHERE scope_id IN ("+ids(grants(a,"AUDITORIA"))+")"),"route","auditoria","filter","","hint","No concede acceso al expediente"))));
        if(sections.isEmpty())sections.add(section("personal","Mi espacio","No hay una funcion operativa asignada. Contacta a la autoridad habilitada.",List.of(metric(v,"Avisos sin leer","avisos","unread","Informacion personal"))));
        return Map.of("sections",sections,"queues",queues,"generated_ms",System.currentTimeMillis(),"substituting",!a.substitutions().isEmpty());
    }
}
