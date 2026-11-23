package cl.nexbyte.sigera;

import java.util.*;
import org.springframework.stereotype.Service;
import cl.nexbyte.sigera.Access.Actor;
import static cl.nexbyte.sigera.Db.*;

/** Proyecciones pequenas para la UI. Toda ficha revalida acceso en servidor. */
@Service
public class Queries {
    final Db db;final Access access;final Attention attention;
    private final Integrations integrations;
    private final Documents documents;private final OriginRelease origin;
    public Queries(Db db,Access access,Attention attention,Integrations integrations,Documents documents,OriginRelease origin){this.db=db;this.access=access;this.attention=attention;this.integrations=integrations;this.documents=documents;this.origin=origin;}
    public Map<String,Object> workspace(Actor a){
        // Las carteras se consultan por /worklist. Aqui solo contexto acotado de funciones temporales:
        // TI administra todas las subrogancias (D1); titular y subrogante ven las propias.
        boolean ti=a.role(Roles.TI);
        var notices=db.rows("SELECT * FROM inbox_notice WHERE user_id=? ORDER BY id DESC LIMIT 20",a.id());
        var designations=db.rows("SELECT * FROM substitution_designation ORDER BY id DESC").stream().filter(d->ti||a.id()==n(d,"titular_id")||a.id()==n(d,"substitute_id")).toList();
        var subs=db.rows("SELECT x.*,d.scope_id,d.titular_id,d.substitute_id FROM substitution x JOIN substitution_designation d ON d.id=x.designation_id ORDER BY x.id DESC").stream().filter(d->ti||a.id()==n(d,"titular_id")||a.id()==n(d,"substitute_id")).map(d->{var m=new LinkedHashMap<>(d);long now=System.currentTimeMillis();m.put("state",d.get("ended_ms")!=null?"TERMINADA":n(d,"until_ms")<=now?"EXPIRADA":n(d,"from_ms")>now?"PROGRAMADA":"ACTIVA");return m;}).toList();
        Map<String,Object> result=new LinkedHashMap<>();result.put("notices",notices);result.put("designations",designations);result.put("substitutions",subs);
        return result;
    }
    /** Operaciones con destino de unidad o sede: SOL (COM_ROUTE, SOL_ROUTE), flujos (FLOW_REQUEST) y entrega a jefatura (ATTENTION_SEND). */
    static final List<String> DESTINATION_OPERATIONS=List.of("SOL","COLABORACION","TRANSFERENCIA","DERIVACION","ENTREGA");
    public Map<String,Object> catalog(Actor a){
        // Todos los ambitos, tambien las sedes PROYECTADA y CERRADA (con su estado, para etiquetas), con su nivel y las
        // operaciones que reciben segun la misma regla que valida cada comando (Access.destination); solo un ambito
        // activo recibe. La entrega a un profesional se publica aparte, por persona (handoff_scopes).
        // Cobertura territorial vigente (0.7.0, D18): cobertura adicional abierta de cada sede (rige si la sede esta
        // ACTIVA; en una PROYECTADA queda preparada) y regiones que atiende el nivel central. Solo codigos de region.
        var territory=Territory.load(db);
        var catalogScopes=db.rows("SELECT * FROM org_scope ORDER BY id").stream().map(x->{
            var row=new LinkedHashMap<String,Object>(x);row.put("level",b(x,"regional")?"REGIONAL":"CENTRAL");
            row.put("receives",access.destination(x)?DESTINATION_OPERATIONS:List.of());
            row.put("coverage",territory.coverage(n(x,"id")));return (Map<String,Object>)row;}).toList();
        Map<String,Object> result=new LinkedHashMap<>();result.put("scopes",catalogScopes);result.put("central_coverage",territory.central());
        result.put("regions",AdultIdentity.REGIONS.entrySet().stream().map(e->Map.of("code",e.getKey(),"name",e.getValue())).toList());result.put("units",db.rows("SELECT * FROM org_unit ORDER BY code"));result.put("roles",Roles.json());result.put("reports",Reports.catalog());
        var enabledScopes=catalogScopes.stream().filter(x->b(x,"enabled")).map(x->n(x,"id")).toList();
        boolean registrar=a.grants().stream().anyMatch(g->g.scope()!=null&&a.registrar(g.scope()));
        Map<Long,Actor> actors=new HashMap<>();for(var candidate:access.enabledActors())actors.put(candidate.id(),candidate);
        // Directorio minimo de funcionarios, nunca hashes, correos privados o credenciales.
        result.put("people",db.rows("SELECT id,name,home_scope,enabled FROM staff WHERE technical=FALSE ORDER BY name").stream().map(u->{
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
        if(a.institutionalReader()||access.matterSol(a,s))return true;
        if(s(s,"state").equals("DEVUELTA"))return access.canDistribute(a,db.one("SELECT * FROM communication WHERE id=?",n(s,"communication_id")));
        if(s(s,"state").equals("PENDIENTE"))return access.solReceiver(a,n(s,"destination_scope"));
        return db.rows("SELECT * FROM requirement WHERE solicitation_id=?",n(s,"id")).stream().anyMatch(r->access.reqRead(a,r));
    }
    /** Flujo: jefaturas de origen y destino, consulta institucional o Direccion de la materia de su REQ (D9; no la de un Caso). */
    boolean flowVisible(Actor a,Map<String,Object> f){
        return a.institutionalReader()||a.head(n(f,"origin_scope"))||a.head(n(f,"target_scope"))||access.matterReq(a,db.one("SELECT * FROM requirement WHERE id=?",n(f,"requirement_id")));
    }
    Map<String,Object> reqSummary(Actor a,Map<String,Object> r){
        var out=new LinkedHashMap<>(r);long id=n(r,"id");
        if(!access.reqRecords(a,r))out.remove("close_reason");
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
                // Misma facultad que COM_LINK (distribucion o jefatura de origen); la consulta institucional no la tiene.
                out.put("can_link",(access.canDistribute(a,c)||a.head(scope))&&s(c,"state").equals("NUEVA")&&db.count("SELECT COUNT(*) FROM communication_link WHERE communication_id=?",id)==0);
                out.put("persons",db.rows("SELECT * FROM communication_person WHERE communication_id=? ORDER BY id",id));
                out.put("can_route",access.canDistribute(a,c)&&s(c,"state").equals("NUEVA")&&db.count("SELECT COUNT(*) FROM solicitation WHERE communication_id=?",id)==0);
                out.put("can_assume",a.head(scope)&&s(c,"state").equals("NUEVA"));out.put("attention_scopes",access.managementScopes(a,scope));
                out.put("solicitations",db.rows("SELECT * FROM solicitation WHERE communication_id=?",id).stream().filter(s->solVisible(a,s)).toList());
                out.put("requirements",db.rows("SELECT * FROM requirement WHERE communication_id=?",id).stream().filter(r->access.reqRead(a,r)).map(r->reqSummary(a,r)).toList());
                out.put("links",db.rows("SELECT l.id,l.requirement_id,l.state,l.returned_ms,r.folio FROM communication_link l JOIN requirement r ON r.id=l.requirement_id WHERE l.communication_id=?",id));
                out.put("documents",documents(a,"COM",id));
                // Solo en la ficha autorizada (comForRead): datos de identificacion y registrales del Formulario.
                if(c.get("external_source")!=null)out.put("external",external(id));
                out.put("suggested_region",access.suggestedRegion(id,null));
            }
            case "SOL" -> {
                var s=db.one("SELECT * FROM solicitation WHERE id=?",id);if(!solVisible(a,s))Problem.denied();scope=n(s,"destination_scope");out=new LinkedHashMap<>(s);
                var com=new LinkedHashMap<>(access.comForRead(a,n(s,"communication_id")));com.put("suggested_region",access.suggestedRegion(n(s,"communication_id"),null));out.put("communication",com);out.put("can_decide",access.solReceiver(a,scope)&&s(s,"state").equals("PENDIENTE"));
                out.put("receiving_authority",access.solReceiverLabel(scope));
                // La consulta institucional tambien ve SOL devueltas: redistribuir sigue siendo de Distribucion.
                out.put("can_redistribute",s(s,"state").equals("DEVUELTA")&&access.canDistribute(a,com));
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
                // Constancias del REQ: completas para quien lo gestiona; la consulta institucional no ve las reservadas (D4).
                out.put("notes",access.reqRecords(a,r)?db.rows("SELECT n.*,u.name AS author FROM management_note n JOIN staff u ON u.id=n.author_id WHERE n.requirement_id=? AND n.case_id IS NULL ORDER BY n.id",id).stream().filter(n->!b(n,"reserved")||access.reqMain(a,r)).toList():List.of());
                out.put("documents",documents(a,"REQ",id));out.put("citizen_documents",access.comRead(a,com)?db.rows("SELECT d.id,d.name,d.size_bytes,d.quarantined,d.document_type,d.created_ms,cm.folio AS source_folio,"+OriginRelease.BASIS_SQL+" FROM document_item d JOIN communication cm ON cm.id=d.communication_id WHERE cm.id=? ORDER BY d.id",n(r,"communication_id")):List.of());out.put("closures",access.reqRecords(a,r)?db.rows("SELECT * FROM closure_record WHERE requirement_id=? AND case_id IS NULL ORDER BY id",id):List.of());
                boolean wide=access.reqWideRead(a,r);
                out.put("links",db.rows("SELECT l.*,c.folio AS com_folio FROM communication_link l JOIN communication c ON c.id=l.communication_id WHERE l.requirement_id=? ORDER BY l.id",id).stream().filter(l->wide||a.head(scope)||s(l,"state").equals("ADMITIDA")&&(l.get("target_case_id")==null?access.reqMain(a,r):access.caseRead(a,db.one("SELECT * FROM case_record WHERE id=?",n(l,"target_case_id"))))).toList());
                out.put("flows",db.rows("SELECT * FROM flow_request WHERE requirement_id=? ORDER BY id",id).stream().filter(f->flowVisible(a,f)).toList());
                out.put("reopenings",db.rows("SELECT * FROM reopening WHERE requirement_id=? ORDER BY id",id).stream().filter(p->wide||a.head(scope)||a.id()==n(p,"requested_by")||p.get("case_id")!=null&&a.head(n(db.one("SELECT scope_id FROM case_record WHERE id=?",n(p,"case_id")),"scope_id"))).toList());
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
        out.put("history",kind.equals("REQ")&&!access.reqRecords(a,db.one("SELECT * FROM requirement WHERE id=?",id))?List.of():history(a,kind,id,scope));
        // Aviso de la ficha: se lee solo por la consulta institucional (sin ella, la misma cuenta no la veria).
        boolean institutional=a.institutionalReader()&&!readable(a.withoutInstitutionalReader(),kind,id);
        out.put("institutional_read",institutional);
        // Lectura nacional por materia (D9): la Direccion la lee solo por su materia en la sede.
        out.put("matter_read",!institutional&&!a.matters().isEmpty()&&!readable(a.withoutInstitutionalReader().withoutMatters(),kind,id));
        db.audit(a.id(),"CONSULTA",""+kind,id,scope,"");return out;
    }
    /** Mismos predicados de lectura que detail, sin auditar ni armar la ficha. */
    boolean readable(Actor a,String kind,long id){
        return switch(kind){
            case "COM" -> access.comRead(a,db.one("SELECT * FROM communication WHERE id=?",id));
            case "SOL" -> {var s=db.one("SELECT * FROM solicitation WHERE id=?",id);yield solVisible(a,s)&&access.comRead(a,db.one("SELECT * FROM communication WHERE id=?",n(s,"communication_id")));}
            case "REQ" -> access.reqRead(a,db.one("SELECT * FROM requirement WHERE id=?",id));
            case "CAS" -> access.caseRead(a,db.one("SELECT * FROM case_record WHERE id=?",id));
            case "FLUJO" -> flowVisible(a,db.one("SELECT * FROM flow_request WHERE id=?",id));
            default -> false;
        };
    }
    private List<Map<String,Object>> history(Actor a,String kind,long id,long scope){
        // Lectores por materia reciben la historia del registro autorizado, no auditoria transversal.
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
        return db.rows("SELECT d.id,d.name,d.size_bytes,d.sha256,d.created_ms,d.uploaded_by,d.reserved,d.quarantined,cm.folio AS source_folio,"+OriginRelease.BASIS_SQL+" FROM document_item d JOIN communication cm ON cm.id=d.communication_id WHERE d.communication_id=(SELECT r.communication_id FROM requirement r JOIN case_record c ON c.requirement_id=r.id WHERE c.id=?) OR d.communication_id IN (SELECT l.communication_id FROM communication_link l WHERE l.target_case_id=? AND l.state='ADMITIDA' AND l.returned_ms IS NULL) ORDER BY d.id",caseId,caseId);
    }
    public List<Map<String,Object>> documents(Actor a,String kind,long id){
        String column=switch(kind){case "COM"->"communication_id";case "REQ"->"requirement_id";case "CAS"->"case_id";default->throw new Problem(400,"Contexto documental invalido.");};
        if(kind.equals("REQ")&&!access.reqRecords(a,db.one("SELECT * FROM requirement WHERE id=?",id)))return List.of();
        // release_basis ORIGEN: liberado por analisis en origen del Formulario (D17), no por un analisis local.
        return db.rows("SELECT d.id,d.name,d.size_bytes,d.sha256,d.created_ms,d.uploaded_by,d.reserved,d.quarantined,d.note_id,d.document_type,d.description,d.previous_document_id,"+OriginRelease.BASIS_SQL
            +" FROM document_item d WHERE d."+column+"=? ORDER BY d.id",id).stream().filter(d->!b(d,"reserved")||kind.equals("CAS")&&access.reserved(a,db.one("SELECT * FROM case_record WHERE id=?",id))||kind.equals("REQ")&&a.head(n(db.one("SELECT principal_scope FROM requirement WHERE id=?",id),"principal_scope"))).toList();
    }
    public Map<String,Object> administration(Actor a){
        boolean ti=a.role(Roles.TI),auditor=a.role("AUDITORIA");if(!ti&&!auditor)Problem.denied();
        Map<String,Object> result=new LinkedHashMap<>();
        // TI administra cuentas, funciones (vigentes e historicas), designaciones y subrogancias de toda la instancia (D1).
        result.put("users",ti?db.rows("SELECT id,username,name,email,enabled,home_scope,must_change,security_version FROM staff WHERE technical=FALSE ORDER BY id"):List.of());
        result.put("grants",ti?db.rows("SELECT g.id,g.user_id,g.role_code,g.scope_id,g.valid_from,g.valid_until,g.revoked_at,g.granted_by,g.basis FROM role_assignment g JOIN staff u ON u.id=g.user_id WHERE u.technical=FALSE ORDER BY g.id"):List.of());
        result.put("designations",ti?db.rows("SELECT * FROM substitution_designation ORDER BY id DESC"):List.of());
        // Avisos de la instancia (sin datos de personas) e incompatibilidades por resolver (D2; heredadas de 0.4.0).
        var warnings=new ArrayList<>(Personnel.warnings(db));warnings.addAll(origin.warnings());
        result.put("warnings",warnings);
        result.put("incompatibilities",ti?Personnel.tiConflicts(db):List.of());
        result.put("structure",ti?structure():List.of());
        // Cobertura del nivel central (D18): regiones vigentes, historia de periodos y revision para la concurrencia optimista.
        if(ti){var central=new LinkedHashMap<String,Object>();central.put("regions",Territory.load(db).central());central.put("history",Territory.history(db,null));central.put("revision",Territory.centralRevision(db));result.put("central_coverage",central);}
        else result.put("central_coverage",Map.of());
        result.put("substitutions",ti?db.rows("SELECT x.*,d.scope_id,d.titular_id,d.substitute_id FROM substitution x JOIN substitution_designation d ON d.id=x.designation_id ORDER BY x.id DESC"):List.of());
        // Operacion de integraciones para TI: estado, pausa y contadores agregados; sin documentos ni contenidos.
        result.put("integrations",ti?integrations.overview():Map.of());
        // Cuarentena: identificador, tamano, huella y ultimo analisis; nunca nombre ni contenido del original.
        result.put("quarantine",ti?db.rows("SELECT d.id,d.size_bytes,d.sha256,d.created_ms,d.quarantined,s.verdict AS scan_verdict,s.detail AS scan_detail,s.engine AS scan_engine,s.finished_ms AS scan_ms,"
            +"(SELECT COUNT(*) FROM document_scan x WHERE x.document_id=d.id AND x.verdict='INFECTADO') AS detections FROM document_item d "
            +"LEFT JOIN document_scan s ON s.id=(SELECT MAX(y.id) FROM document_scan y WHERE y.document_id=d.id) WHERE d.quarantined=TRUE ORDER BY d.id"):List.of());
        if(ti){var policy=new LinkedHashMap<String,Object>(documents.releasePolicy());policy.put("origin_release",origin.policy());result.put("document_policy",policy);}
        else result.put("document_policy",Map.of());
        // Auditoria: eventos de su alcance mas toda la administracion de cuentas, ambitos, subrogancias y calendario habil. TI: administracion y sistema.
        String administrative=(auditor?"'USUARIO','AMBITO','SUBROGANCIA','CALENDARIO'":"")+(auditor&&ti?",":"")+(ti?"'USUARIO','SISTEMA','AMBITO','SUBROGANCIA','CALENDARIO'":"");
        result.put("audit",db.rows("SELECT e.*,u.name AS actor FROM audit_event e JOIN staff u ON u.id=e.actor_id WHERE e.scope_id IN ("+BoardQueries.ids(BoardQueries.grants(a,"AUDITORIA"))+") OR e.resource_type IN ("+administrative+") ORDER BY e.id DESC LIMIT 300"));
        return result;
    }
    /**
     * Panel Estructura (R03b, solo TI): cada ambito con su cabeza vigente (Jefatura, Direccion o Coordinacion regional),
     * la subrogancia activa o la proxima programada, la dotacion (cuentas con funciones vigentes o programadas), el
     * volumen abierto y, en las sedes activas, lo que impide cerrarlas (la misma regla de SCOPE_CLOSE). Solo cantidades
     * e identificadores de cuentas: ningun dato de expedientes ni de personas atendidas.
     */
    private List<Map<String,Object>> structure(){
        long now=System.currentTimeMillis();
        Map<Long,Long> heads=new HashMap<>(),staffed=counts("SELECT scope_id AS k,COUNT(DISTINCT user_id) AS c FROM role_assignment WHERE scope_id IS NOT NULL AND revoked_at IS NULL AND (valid_until IS NULL OR valid_until>?) GROUP BY scope_id",now);
        Map<Long,String> headRoles=new HashMap<>();
        for(var g:db.rows("SELECT g.scope_id,g.user_id,g.role_code FROM role_assignment g JOIN staff u ON u.id=g.user_id WHERE g.role_code IN ('JEFATURA','DIRECCION') AND g.revoked_at IS NULL AND g.valid_from<=? AND (g.valid_until IS NULL OR g.valid_until>?) ORDER BY g.id",now,now))
            if(heads.putIfAbsent(n(g,"scope_id"),n(g,"user_id"))==null)headRoles.put(n(g,"scope_id"),s(g,"role_code"));
        Map<Long,Map<String,Object>> subs=new HashMap<>();
        for(var x:db.rows("SELECT d.scope_id,d.titular_id,d.substitute_id,x.from_ms,x.until_ms FROM substitution x JOIN substitution_designation d ON d.id=x.designation_id WHERE x.ended_ms IS NULL AND x.until_ms>? ORDER BY x.from_ms",now)){
            var m=new LinkedHashMap<String,Object>(x);m.put("state",n(x,"from_ms")>now?"PROGRAMADA":"ACTIVA");subs.putIfAbsent(n(x,"scope_id"),m);
        }
        var req=counts("SELECT principal_scope AS k,COUNT(*) AS c FROM requirement WHERE state<>'CERRADO' GROUP BY principal_scope");
        var cases=counts("SELECT scope_id AS k,COUNT(*) AS c FROM case_record WHERE state<>'CERRADO' GROUP BY scope_id");
        var sol=counts("SELECT destination_scope AS k,COUNT(*) AS c FROM solicitation WHERE state='PENDIENTE' GROUP BY destination_scope");
        var territory=Territory.load(db);
        Map<Long,List<Map<String,Object>>> periods=new HashMap<>();
        for(var p:db.rows("SELECT scope_id,id,region_code,from_ms,from_by,from_basis,until_ms,until_by,until_basis FROM territorial_coverage WHERE scope_id IS NOT NULL ORDER BY id DESC")){
            var m=new LinkedHashMap<String,Object>(p);long scope=n(p,"scope_id");m.remove("scope_id");periods.computeIfAbsent(scope,k->new ArrayList<>()).add(m);
        }
        return db.rows("SELECT id,code,name,unit_code,area_code,site_code,regional,region_code,state,enabled,opens_ms,revision FROM org_scope ORDER BY id").stream().map(x->{
            long id=n(x,"id");var row=new LinkedHashMap<String,Object>(x);
            row.put("level",b(x,"regional")?"REGIONAL":"CENTRAL");
            row.put("head_id",heads.get(id));row.put("head_role",headRoles.get(id));row.put("substitution",subs.get(id));
            row.put("staff",staffed.getOrDefault(id,0L));
            row.put("open_req",req.getOrDefault(id,0L));row.put("open_cases",cases.getOrDefault(id,0L));row.put("pending_sol",sol.getOrDefault(id,0L));
            row.put("close_blockers",b(x,"regional")&&s(x,"state").equals(Access.ACTIVE)?Personnel.closeBlockers(db,id):List.of());
            // Cobertura adicional vigente, historia de periodos y efecto territorial de cerrar la sede (el mismo que SCOPE_CLOSE confirma).
            row.put("coverage",territory.coverage(id));row.put("coverage_history",periods.getOrDefault(id,List.of()));
            row.put("territorial_effect",b(x,"regional")&&s(x,"state").equals(Access.ACTIVE)?Territory.effect(territory,territory.closing(id),id):List.of());
            return (Map<String,Object>)row;
        }).toList();
    }
    private Map<Long,Long> counts(String sql,Object...args){
        Map<Long,Long> out=new HashMap<>();for(var r:db.rows(sql,args))if(r.get("k")!=null)out.put(n(r,"k"),n(r,"c"));return out;
    }
    static Map<String,Object> pick(Map<String,Object> row,String...keys){Map<String,Object> out=new LinkedHashMap<>();for(var key:keys)out.put(key,row.get(key));return out;}
}
