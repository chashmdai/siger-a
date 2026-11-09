package cl.nexbyte.sigera;

import java.util.*;
import org.springframework.stereotype.Service;
import cl.nexbyte.sigera.Access.Actor;
import static cl.nexbyte.sigera.Db.*;

/** Proyecciones pequenas para la UI. Toda ficha revalida acceso en servidor. */
@Service
public class Queries {
    final Db db;final Access access;final Attention attention;
    public Queries(Db db,Access access,Attention attention){this.db=db;this.access=access;this.attention=attention;}
    public Map<String,Object> workspace(Actor a){
        // Las carteras se consultan por /worklist. Aqui solo contexto acotado de funciones temporales.
        var notices=db.rows("SELECT * FROM inbox_notice WHERE user_id=? ORDER BY id DESC LIMIT 20",a.id());
        var designations=db.rows("SELECT * FROM substitution_designation ORDER BY id DESC").stream().filter(d->a.authority(n(d,"scope_id"))||a.id()==n(d,"titular_id")||a.id()==n(d,"substitute_id")).toList();
        var subs=db.rows("SELECT x.*,d.scope_id,d.titular_id,d.substitute_id FROM substitution x JOIN substitution_designation d ON d.id=x.designation_id ORDER BY x.id DESC").stream().filter(d->a.authority(n(d,"scope_id"))||a.id()==n(d,"titular_id")||a.id()==n(d,"substitute_id")).map(d->{var m=new LinkedHashMap<>(d);long now=System.currentTimeMillis();m.put("state",d.get("ended_ms")!=null?"TERMINADA":n(d,"until_ms")<=now?"EXPIRADA":n(d,"from_ms")>now?"PROGRAMADA":"ACTIVA");return m;}).toList();
        Map<String,Object> result=new LinkedHashMap<>();result.put("notices",notices);result.put("designations",designations);result.put("substitutions",subs);
        return result;
    }
    public Map<String,Object> catalog(Actor a){
        var catalogScopes=db.rows("SELECT * FROM org_scope ORDER BY id");Map<String,Object> result=new LinkedHashMap<>();result.put("scopes",catalogScopes);result.put("units",db.rows("SELECT * FROM org_unit ORDER BY code"));
        var enabledScopes=catalogScopes.stream().filter(x->b(x,"enabled")).map(x->n(x,"id")).toList();
        boolean registrar=a.grants().stream().anyMatch(g->g.scope()!=null&&a.registrar(g.scope()));
        Map<Long,Actor> actors=new HashMap<>();for(var candidate:access.enabledActors())actors.put(candidate.id(),candidate);
        // Directorio minimo de funcionarios, nunca hashes, correos privados o credenciales.
        result.put("people",db.rows("SELECT id,name,home_scope,enabled FROM staff ORDER BY name").stream().map(u->{
            var person=new LinkedHashMap<>(u);
            Actor candidate=b(u,"enabled")?actors.get(n(u,"id")):null;
            if(candidate!=null){
                person.put("handoff_scopes",registrar?enabledScopes.stream().filter(candidate::professional).toList():List.of());
                person.put("assignable_scopes",enabledScopes.stream().filter(id->a.professional(id)&&candidate.professional(id)).toList());
            }else {
                person.put("handoff_scopes",List.of());
                person.put("assignable_scopes",List.of());
            }
            return person;
        }).toList());return result;
    }
    boolean solVisible(Actor a,Map<String,Object> s){
        if(s(s,"state").equals("DEVUELTA"))return access.canDistribute(a,db.one("SELECT * FROM communication WHERE id=?",n(s,"communication_id")));
        if(s(s,"state").equals("PENDIENTE"))return access.solReceiver(a,n(s,"destination_scope"));
        return db.rows("SELECT * FROM requirement WHERE solicitation_id=?",n(s,"id")).stream().anyMatch(r->access.reqRead(a,r));
    }
    boolean flowVisible(Actor a,Map<String,Object> f){return a.head(n(f,"origin_scope"))||a.head(n(f,"target_scope"));}
    Map<String,Object> reqSummary(Actor a,Map<String,Object> r){
        var out=new LinkedHashMap<>(r);long id=n(r,"id");
        if(!access.reqMain(a,r))out.remove("close_reason");
        out.put("can_head",a.head(n(r,"principal_scope")));out.put("can_attention",access.reqMain(a,r));
        out.put("open_cases",db.count("SELECT COUNT(*) FROM case_record WHERE requirement_id=? AND state<>'CERRADO'",id));
        out.put("total_cases",db.count("SELECT COUNT(*) FROM case_record WHERE requirement_id=?",id));
        if(a.head(n(r,"principal_scope"))){
            long pending=db.count("SELECT COUNT(*) FROM flow_request WHERE requirement_id=? AND state='PENDIENTE'",id)+db.count("SELECT COUNT(*) FROM reopening WHERE requirement_id=? AND state='PENDIENTE'",id);
            long tasks=db.count("SELECT COUNT(*) FROM task_item t JOIN case_record c ON c.id=t.case_id WHERE c.requirement_id=? AND t.state IN ('PENDIENTE','EN_CURSO')",id);
            var blockers=new ArrayList<String>();
            if(s(r,"state").equals("CERRADO"))blockers.add("El Requerimiento ya esta cerrado.");
            if(((Number)out.get("open_cases")).longValue()>0)blockers.add(out.get("open_cases")+" Casos abiertos.");
            pending+=db.count("SELECT COUNT(*) FROM attention_handoff WHERE requirement_id=? AND state='PENDIENTE'",id);
            if(db.count("SELECT COUNT(*) FROM attention_record ar WHERE ar.requirement_id=? AND ar.followup_required=TRUE AND NOT EXISTS(SELECT 1 FROM case_record ac WHERE ac.requirement_id=ar.requirement_id)",id)>0)blockers.add("Primera atencion pendiente de apertura de Caso para seguimiento.");
            if(pending>0)blockers.add(pending+" decisiones de continuidad pendientes.");
            if(tasks>0)blockers.add(tasks+" tareas pendientes.");
            out.put("closure_blockers",blockers);out.put("can_close",blockers.isEmpty());
        }
        return out;
    }
    Map<String,Object> caseSummary(Actor a,Map<String,Object> c){
        var out=new LinkedHashMap<>(c);var r=db.one("SELECT complexity,folio,state,solicitation_id FROM requirement WHERE id=?",n(c,"requirement_id"));
        out.put("req_complexity",r.get("complexity"));out.put("req_folio",r.get("folio"));out.put("req_state",r.get("state"));
        // Origen real del REQ: sin SOL solo nace por primera atencion directa (la jefatura que asume crea una SOL ASUMIDA).
        out.put("req_origin",r.get("solicitation_id")==null?"ATENCION_DIRECTA":"SOLICITUD");
        out.put("can_write",access.caseWrite(a,c));out.put("can_head",a.head(n(c,"scope_id")));out.put("is_owner",a.id()==n(c,"responsible_id"));out.put("own_substitution",a.substitutions().contains(n(c,"scope_id"))&&a.id()==n(c,"responsible_id"));
        long pending=db.count("SELECT COUNT(*) FROM task_item WHERE case_id=? AND state IN ('PENDIENTE','EN_CURSO')",n(c,"id"));out.put("pending_tasks",pending);
        boolean flow=db.count("SELECT COUNT(*) FROM flow_request WHERE origin_case_id=? AND state='PENDIENTE'",n(c,"id"))>0;
        var blockers=new ArrayList<String>();
        try{Rules.closure(s(r,"complexity"),a.id()==n(c,"responsible_id"),a.head(n(c,"scope_id")),a.substitutions().contains(n(c,"scope_id")),s(c,"state"),(int)pending,flow);out.put("can_close",true);}catch(Problem e){out.put("can_close",false);blockers.add(e.getMessage());}
        out.put("closure_blockers",blockers);
        // Solicitar termino (responsable) se evalua aparte de confirmar el cierre (can_close).
        var requestBlockers=new ArrayList<String>();
        try{Rules.closeRequest(a.id()==n(c,"responsible_id"),s(c,"state"),(int)pending,flow);}catch(Problem e){requestBlockers.add(e.getMessage());}
        if(!s(r,"state").equals("ABIERTO"))requestBlockers.add("El REQ esta cerrado; requiere nueva gestion autorizada.");
        out.put("can_request_close",requestBlockers.isEmpty());out.put("close_request_blockers",requestBlockers);
        out.put("assignment_pending",db.count("SELECT COUNT(*) FROM case_assignment WHERE case_id=? AND current_flag=TRUE AND acknowledged_ms IS NULL",n(c,"id"))>0);
        return out;
    }
    public Map<String,Object> detail(Actor a,String kind,long id){
        Map<String,Object> out;long scope;
        switch(kind){
            case "COM" -> {
                var c=access.comForRead(a,id);scope=n(c,"origin_scope");out=new LinkedHashMap<>(c);
                out.put("can_link",s(c,"state").equals("NUEVA")&&db.count("SELECT COUNT(*) FROM communication_link WHERE communication_id=?",id)==0);
                out.put("persons",db.rows("SELECT * FROM communication_person WHERE communication_id=? ORDER BY id",id));
                out.put("can_route",access.canDistribute(a,c)&&s(c,"state").equals("NUEVA")&&db.count("SELECT COUNT(*) FROM solicitation WHERE communication_id=?",id)==0);
                out.put("can_assume",a.head(scope)&&s(c,"state").equals("NUEVA"));out.put("attention_scopes",access.managementScopes(a,scope));
                out.put("solicitations",db.rows("SELECT * FROM solicitation WHERE communication_id=?",id).stream().filter(s->solVisible(a,s)).toList());
                out.put("requirements",db.rows("SELECT * FROM requirement WHERE communication_id=?",id).stream().filter(r->access.reqRead(a,r)).map(r->reqSummary(a,r)).toList());
                out.put("links",db.rows("SELECT l.id,l.requirement_id,l.state,l.returned_ms,r.folio FROM communication_link l JOIN requirement r ON r.id=l.requirement_id WHERE l.communication_id=?",id));
                out.put("documents",documents(a,"COM",id));
                // Solo en la ficha autorizada (comForRead): datos de identificacion y registrales del Formulario.
                if(c.get("external_source")!=null)out.put("external",external(id));
            }
            case "SOL" -> {
                var s=db.one("SELECT * FROM solicitation WHERE id=?",id);if(!solVisible(a,s))Problem.denied();scope=n(s,"destination_scope");out=new LinkedHashMap<>(s);
                out.put("communication",access.comForRead(a,n(s,"communication_id")));out.put("can_decide",access.solReceiver(a,scope)&&s(s,"state").equals("PENDIENTE"));
                out.put("receiving_authority",access.solReceiverLabel(scope));
                out.put("can_redistribute",s(s,"state").equals("DEVUELTA"));
            }
            case "REQ" -> {
                var r=access.reqForRead(a,id);scope=n(r,"principal_scope");out=reqSummary(a,r);
                out.put("attention",attention.view(a,r));
                var areas=db.rows("SELECT id FROM org_scope WHERE unit_code=(SELECT unit_code FROM org_scope WHERE id=?) AND site_code=(SELECT site_code FROM org_scope WHERE id=?) AND enabled=TRUE ORDER BY id",scope,scope);
                out.put("case_scopes",areas.stream().map(x->n(x,"id")).filter(sc->access.canOpenCaseUnder(scope,sc)&&a.head(sc)).toList());
                if(r.get("solicitation_id")!=null)out.put("requested_area_scope",db.one("SELECT requested_area_scope FROM solicitation WHERE id=?",n(r,"solicitation_id")).get("requested_area_scope"));
                var com=db.one("SELECT * FROM communication WHERE id=?",n(r,"communication_id"));out.put("can_read_com",access.comRead(a,com));
                out.put("case_rows",db.rows("SELECT * FROM case_record WHERE requirement_id=? ORDER BY id",id).stream().map(c->{
                    if(access.caseRead(a,c)){var v=caseSummary(a,c);v.put("readable",true);return v;}
                    if(a.head(scope)){var v=pick(c,"id","folio","state","scope_id");v.put("readable",false);return v;}return null;
                }).filter(Objects::nonNull).toList());
                out.put("notes",access.reqMain(a,r)?db.rows("SELECT n.*,u.name AS author FROM management_note n JOIN staff u ON u.id=n.author_id WHERE n.requirement_id=? AND n.case_id IS NULL ORDER BY n.id",id):List.of());
                out.put("documents",documents(a,"REQ",id));out.put("citizen_documents",access.comRead(a,com)?db.rows("SELECT d.id,d.name,d.size_bytes,d.quarantined,d.document_type,d.created_ms,cm.folio AS source_folio FROM document_item d JOIN communication cm ON cm.id=d.communication_id WHERE cm.id=? ORDER BY d.id",n(r,"communication_id")):List.of());out.put("closures",access.reqMain(a,r)?db.rows("SELECT * FROM closure_record WHERE requirement_id=? AND case_id IS NULL ORDER BY id",id):List.of());
                out.put("links",db.rows("SELECT l.*,c.folio AS com_folio FROM communication_link l JOIN communication c ON c.id=l.communication_id WHERE l.requirement_id=? ORDER BY l.id",id).stream().filter(l->a.head(scope)||s(l,"state").equals("ADMITIDA")&&(l.get("target_case_id")==null?access.reqMain(a,r):access.caseRead(a,db.one("SELECT * FROM case_record WHERE id=?",n(l,"target_case_id"))))).toList());
                out.put("flows",db.rows("SELECT * FROM flow_request WHERE requirement_id=? ORDER BY id",id).stream().filter(f->flowVisible(a,f)).toList());
                out.put("reopenings",db.rows("SELECT * FROM reopening WHERE requirement_id=? ORDER BY id",id).stream().filter(p->a.head(scope)||a.id()==n(p,"requested_by")||p.get("case_id")!=null&&a.head(n(db.one("SELECT scope_id FROM case_record WHERE id=?",n(p,"case_id")),"scope_id"))).toList());
            }
            case "CAS" -> {
                var c=access.caseForRead(a,id);scope=n(c,"scope_id");out=caseSummary(a,c);
                out.put("notes",db.rows("SELECT n.*,u.name AS author FROM management_note n JOIN staff u ON u.id=n.author_id WHERE n.case_id=? ORDER BY n.id",id).stream().filter(n->!b(n,"reserved")||access.reserved(a,c)).toList());
                out.put("tasks",db.rows("SELECT * FROM task_item WHERE case_id=? ORDER BY id",id));
                out.put("members",db.rows("SELECT m.*,u.name FROM case_member m JOIN staff u ON u.id=m.user_id WHERE m.case_id=?",id));
                out.put("assignments",db.rows("SELECT x.*,u.name FROM case_assignment x JOIN staff u ON u.id=x.user_id WHERE x.case_id=? ORDER BY x.id",id));
                out.put("documents",documents(a,"CAS",id));out.put("citizen_documents",access.caseSourceDocuments(a,c)?incomingDocuments(id):List.of());out.put("closures",db.rows("SELECT * FROM closure_record WHERE case_id=? ORDER BY id",id));
                out.put("flows",db.rows("SELECT id,kind,target_scope,state,successor_case_id,revision FROM flow_request WHERE origin_case_id=? ORDER BY id",id));
                var r=db.one("SELECT revision,principal_scope FROM requirement WHERE id=?",n(c,"requirement_id"));out.put("req_revision",r.get("revision"));
            }
            case "FLUJO" -> {var f=db.one("SELECT * FROM flow_request WHERE id=?",id);if(!flowVisible(a,f))Problem.denied();scope=n(f,"origin_scope");out=new LinkedHashMap<>(f);out.put("can_accept",a.head(n(f,"target_scope")));out.put("can_withdraw",a.head(scope));out.put("case_scopes",access.managementScopes(a,n(f,"target_scope")));}
            default -> throw new Problem(404,"Tipo de ficha desconocido.");
        }
        out.put("history",kind.equals("REQ")&&!access.reqMain(a,db.one("SELECT * FROM requirement WHERE id=?",id))?List.of():history(a,kind,id,scope));db.audit(a.id(),"CONSULTA",""+kind,id,scope,"");return out;
    }
    private List<Map<String,Object>> history(Actor a,String kind,long id,long scope){
        // Lectores por materia reciben historia del Caso autorizado, no auditoria transversal.
        return db.rows("SELECT e.id,e.at_ms,e.action,e.note,u.name AS actor FROM audit_event e JOIN staff u ON u.id=e.actor_id WHERE e.resource_type=? AND e.resource_id=? AND e.action NOT IN ('CONSULTA','DESCARGA','DENEGADO') ORDER BY e.id DESC LIMIT 100",kind,id);
    }
    /**
     * Proyeccion de una COM externa para su ficha: metadatos del original (sin el JSON), personas con sus
     * atributos y discrepancias con procedencia, y respuestas. La edad actual se calcula al consultar.
     */
    private Map<String,Object> external(long com){
        var out=new LinkedHashMap<String,Object>();
        out.put("original",db.one("SELECT source,external_id,document_version,document_kind,received_ms,content_hash,provenance_hash,custody_key_id,document_sha256,document_bytes,stored_ms FROM communication_original WHERE communication_id=?",com));
        var today=java.time.LocalDate.now(ExternalProjection.ZONE);
        out.put("persons",db.rows("SELECT * FROM external_person WHERE communication_id=? ORDER BY person_index",com).stream().map(p->{
            var m=new LinkedHashMap<String,Object>(p);
            var born=ExternalProjection.date(s(p,"birth_date"));
            m.put("current_age",born==null?null:ExternalProjection.age(born,today));
            m.put("attributes",db.rows("SELECT attr_key,discrepancy,value_text,code_text,meaning,source,level_code,observed_ms,evidence FROM external_attribute WHERE person_id=? ORDER BY id",n(p,"id")));
            return m;
        }).toList());
        out.put("fields",db.rows("SELECT field_group,person_key,item,field_key,value_text FROM external_field WHERE communication_id=? ORDER BY id",com));
        return out;
    }

    /** Original de COM y COM hijas admitidas expresamente para este Caso; sin copiar archivos. */
    private List<Map<String,Object>> incomingDocuments(long caseId){
        return db.rows("SELECT d.id,d.name,d.size_bytes,d.sha256,d.created_ms,d.uploaded_by,d.reserved,d.quarantined,cm.folio AS source_folio FROM document_item d JOIN communication cm ON cm.id=d.communication_id WHERE d.communication_id=(SELECT r.communication_id FROM requirement r JOIN case_record c ON c.requirement_id=r.id WHERE c.id=?) OR d.communication_id IN (SELECT l.communication_id FROM communication_link l WHERE l.target_case_id=? AND l.state='ADMITIDA' AND l.returned_ms IS NULL) ORDER BY d.id",caseId,caseId);
    }
    public List<Map<String,Object>> documents(Actor a,String kind,long id){
        String column=switch(kind){case "COM"->"communication_id";case "REQ"->"requirement_id";case "CAS"->"case_id";default->throw new Problem(400,"Contexto documental invalido.");};
        if(kind.equals("REQ")&&!access.reqMain(a,db.one("SELECT * FROM requirement WHERE id=?",id)))return List.of();
        return db.rows("SELECT id,name,size_bytes,sha256,created_ms,uploaded_by,reserved,quarantined,note_id,document_type,description,previous_document_id FROM document_item WHERE "+column+"=? ORDER BY id",id).stream().filter(d->!b(d,"reserved")||kind.equals("CAS")&&access.reserved(a,db.one("SELECT * FROM case_record WHERE id=?",id))||kind.equals("REQ")&&a.head(n(db.one("SELECT principal_scope FROM requirement WHERE id=?",id),"principal_scope"))).toList();
    }
    public Map<String,Object> administration(Actor a){
        boolean ti=a.role("TI"),authority=a.role("AUTORIDAD");if(!ti&&!authority&&!a.role("AUDITORIA"))Problem.denied();
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("users",ti||authority?db.rows("SELECT id,username,name,email,enabled,home_scope,must_change FROM staff ORDER BY id"):List.of());
        result.put("grants",authority?db.rows("SELECT id,user_id,role_code,scope_id,valid_from,valid_until,revoked_at,basis FROM role_assignment ORDER BY id").stream().filter(g->g.get("scope_id")==null?s(g,"role_code").equals("DISTRIBUIDOR"):a.authority(n(g,"scope_id"))).toList():List.of());
        result.put("quarantine",ti?db.rows("SELECT id,size_bytes,sha256,created_ms,quarantined FROM document_item WHERE quarantined=TRUE ORDER BY id"):List.of());
        result.put("audit",db.rows("SELECT e.*,u.name AS actor FROM audit_event e JOIN staff u ON u.id=e.actor_id WHERE e.scope_id IN ("+BoardQueries.ids(BoardQueries.grants(a,"AUDITORIA"))+")"+(ti?" OR e.resource_type IN ('USUARIO','SISTEMA','AMBITO')":"")+" ORDER BY e.id DESC LIMIT 300"));
        return result;
    }
    static Map<String,Object> pick(Map<String,Object> row,String...keys){Map<String,Object> out=new LinkedHashMap<>();for(var key:keys)out.put(key,row.get(key));return out;}
}
