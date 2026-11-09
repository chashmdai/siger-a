package cl.nexbyte.sigera;

import java.util.*;
import org.springframework.stereotype.Service;
import static cl.nexbyte.sigera.Db.*;

/** Una politica compartida por API, bandejas, documentos y comandos. */
@Service
public class Access {
    public record Grant(String role, Long scope) { }
    public record Actor(long id, String username, String name, long homeScope, boolean mustChange,
                        long securityVersion, List<Grant> grants, Set<Long> substitutions) {
        public boolean role(String role) { return grants.stream().anyMatch(g -> g.role().equals(role)); }
        public boolean at(String role,long scope) { return grants.stream().anyMatch(g -> g.role().equals(role) && Objects.equals(g.scope(),scope)); }
        public boolean head(long scope) { return at("JEFATURA",scope)||at("DIRECCION",scope); }
        public boolean professional(long scope) { return head(scope)||at("PROFESIONAL",scope); }
        public boolean registrar(long scope) { return professional(scope)||at("ADMINISTRATIVO",scope); }
        public boolean authority(long scope) { return at("AUTORIDAD",scope); }
    }
    private final Db db;
    public Access(Db db) { this.db=db; }

    // --- Funciones efectivas ---------------------------------------------------------------
    // Una sola regla resuelve una cuenta (cada solicitud) o todo el personal habilitado
    // (avisos y catalogo), para no repetir 2+N consultas por persona dentro del lock de comandos.
    private static final String STAFF_COLUMNS="SELECT id,username,name,home_scope,enabled,must_change,security_version FROM staff";
    private static final String GRANTS="SELECT g.* FROM role_assignment g LEFT JOIN org_scope s ON s.id=g.scope_id WHERE g.revoked_at IS NULL AND g.valid_from<=? AND (g.valid_until IS NULL OR g.valid_until>?) AND (g.scope_id IS NULL OR s.enabled=TRUE)";
    // La subrogancia conserva el tipo de funcion y solo los ambitos previamente otorgados.
    private static final String EFFECTIVE_SUBSTITUTIONS="SELECT d.titular_id,d.substitute_id,ss.scope_id,ss.role_code FROM substitution x JOIN substitution_designation d ON d.id=x.designation_id JOIN substitution_scope ss ON ss.substitution_id=x.id JOIN role_assignment g ON g.id=ss.grant_id JOIN staff u ON u.id=d.substitute_id JOIN staff t ON t.id=d.titular_id JOIN org_scope sc ON sc.id=ss.scope_id JOIN org_scope root ON root.id=d.scope_id WHERE d.enabled=TRUE AND u.enabled=TRUE AND t.enabled=TRUE AND sc.enabled=TRUE AND root.enabled=TRUE AND g.revoked_at IS NULL AND g.valid_from<=? AND (g.valid_until IS NULL OR g.valid_until>?) AND x.ended_ms IS NULL AND x.from_ms<=? AND x.until_ms>?";

    public Actor actor(String username) {
        var u=db.one(STAFF_COLUMNS+" WHERE username=?",username);
        if(!b(u,"enabled"))throw new Problem(401,"Cuenta inactiva.");
        long now=System.currentTimeMillis();
        var grants=db.rows(GRANTS+" AND g.user_id=?",now,now,n(u,"id"));
        return resolve(u,grants,db.rows(EFFECTIVE_SUBSTITUTIONS,now,now,now,now));
    }
    public Actor user(long id) { return actor(s(db.one("SELECT username FROM staff WHERE id=?",id),"username")); }
    /** Personal habilitado con sus funciones vigentes, en orden de usuario, con consultas constantes. */
    public List<Actor> enabledActors() {
        long now=System.currentTimeMillis();
        Map<Long,List<Map<String,Object>>> grants=new HashMap<>();
        for(var g:db.rows(GRANTS,now,now))grants.computeIfAbsent(n(g,"user_id"),k->new ArrayList<>()).add(g);
        var substitutions=db.rows(EFFECTIVE_SUBSTITUTIONS,now,now,now,now);
        return db.rows(STAFF_COLUMNS+" WHERE enabled=TRUE ORDER BY username").stream()
            .map(u->resolve(u,grants.getOrDefault(n(u,"id"),List.of()),substitutions)).toList();
    }
    private Actor resolve(Map<String,Object> u,List<Map<String,Object>> grantRows,List<Map<String,Object>> effective) {
        long id=n(u,"id");
        var list=new ArrayList<Grant>();
        for(var r:grantRows)list.add(new Grant(s(r,"role_code"),nullableId(r,"scope_id")));
        Set<Long> substituted=new HashSet<>();
        for(var r:effective) {
            long scope=n(r,"scope_id");String role=s(r,"role_code");
            if(n(r,"titular_id")==id){
                list.removeIf(g -> Objects.equals(g.scope(),scope)&&g.role().equals(role));
                // Retirar jefatura no retira la cartera personal que el titular gestionaba.
                boolean personal=db.count("SELECT COUNT(*) FROM case_record c WHERE c.scope_id=? AND (c.responsible_id=? OR EXISTS(SELECT 1 FROM case_member m WHERE m.case_id=c.id AND m.user_id=?))",scope,id,id)>0
                    ||db.count("SELECT COUNT(*) FROM requirement WHERE attention_scope=? AND attention_user=?",scope,id)>0;
                if(personal)list.add(new Grant("PROFESIONAL",scope));
            }
            if(n(r,"substitute_id")==id){list.add(new Grant(role,scope));substituted.add(scope);}
        }
        return new Actor(id,s(u,"username"),s(u,"name"),n(u,"home_scope"),b(u,"must_change"),n(u,"security_version"),List.copyOf(list),Set.copyOf(substituted));
    }

    // --- Ambitos y receptores --------------------------------------------------------------
    public Map<String,Object> scope(long id) {
        var r=db.one("SELECT * FROM org_scope WHERE id=?",id);Problem.require(b(r,"enabled"),"Ambito no habilitado.");return r;
    }
    public boolean regional(long scope){return b(db.one("SELECT regional FROM org_scope WHERE id=?",scope),"regional");}
    /** La unidad recibe SOL en Direccion; solo UPR-REQ tiene coordinacion de area como destinataria. */
    public boolean solReceiver(Actor a,long scope){return receiver(a,scope(scope));}
    private static boolean receiver(Actor a,Map<String,Object> s){
        long scope=n(s,"id");
        if(b(s,"regional"))return a.head(scope);
        if("UPR-REQ".equals(s(s,"code")))return a.at("JEFATURA",scope);
        return a.at("DIRECCION",scope);
    }
    public String solReceiverLabel(long scope){
        var s=scope(scope);
        if(b(s,"regional"))return "Coordinacion regional: "+s(s,"name");
        return "UPR-REQ".equals(s(s,"code"))?"Coordinadora de Requerimientos":"Direccion "+s(s,"unit_code");
    }
    public record SolDestination(long scope, Long area) { }
    public SolDestination solDestination(Input in){
        var selected=scope(in.id("scope_id"));
        Long proposed=in.optionalId("area_scope_id");
        // Compatibilidad con solicitudes que indiquen directamente el ambito de un area.
        if(proposed==null&&!s(selected,"area_code").isBlank())proposed=n(selected,"id");
        if(proposed!=null){
            var area=scope(proposed);
            Problem.require(!b(selected,"regional")&&!b(area,"regional")&&!s(area,"area_code").isBlank()
                &&s(selected,"unit_code").equals(s(area,"unit_code"))&&s(selected,"site_code").equals(s(area,"site_code")),
                "El area debe pertenecer a la unidad seleccionada.");
        }
        if(b(selected,"regional"))return new SolDestination(n(selected,"id"),null);
        String unit=s(selected,"unit_code");
        Problem.require(Set.of("UPR","UPD","UEP","UGI").contains(unit),"La dependencia no tiene recepcion de SOL habilitada.");
        if(proposed!=null&&"UPR-REQ".equals(s(scope(proposed),"code")))return new SolDestination(proposed,proposed);
        var roots=db.rows("SELECT * FROM org_scope WHERE unit_code=? AND site_code='CENTRAL' AND COALESCE(area_code,'')='' AND enabled=TRUE",unit);
        Problem.require(roots.size()==1,"Falta habilitar la Direccion destinataria de "+unit+".");
        return new SolDestination(n(roots.getFirst(),"id"),proposed);
    }
    /** Profesional elegible de cualquier area de la unidad, sin otorgarle permisos sobre toda ella. */
    public long solAttentionScope(Actor receiver,long staffId,long scope,Long area){
        var target=scope(scope);var user=user(staffId);
        if(b(target,"regional")||"UPR-REQ".equals(s(target,"code"))){assignable(staffId,scope);return scope;}
        var options=db.rows("SELECT * FROM org_scope WHERE unit_code=? AND site_code=? AND enabled=TRUE ORDER BY id",s(target,"unit_code"),s(target,"site_code"))
            .stream().filter(r->receiver.head(n(r,"id"))&&user.professional(n(r,"id"))).toList();
        Problem.require(!options.isEmpty(),"El funcionario no tiene funcion profesional en esta unidad.");
        if(area!=null&&options.stream().anyMatch(r->n(r,"id")==area))return area;
        if(options.stream().anyMatch(r->n(r,"id")==user.homeScope()))return user.homeScope();
        return n(options.getFirst(),"id");
    }
    /** Ambitos precisos que una autoridad puede ejercer; ninguna herencia de organigrama. */
    public List<Long> managementScopes(Actor a,long reception){
        var root=scope(reception);
        if(b(root,"regional")||!s(root,"area_code").isBlank())return a.head(reception)?List.of(reception):List.of();
        return db.rows("SELECT id FROM org_scope WHERE unit_code=? AND site_code=? AND enabled=TRUE ORDER BY id",s(root,"unit_code"),s(root,"site_code"))
            .stream().map(r->n(r,"id")).filter(a::head).toList();
    }
    public List<Map<String,Object>> functionGrants(long titular,long reception){
        var root=scope(reception);long now=System.currentTimeMillis();
        return db.rows("SELECT g.* FROM role_assignment g JOIN org_scope sc ON sc.id=g.scope_id WHERE g.user_id=? AND g.role_code IN ('JEFATURA','DIRECCION') AND g.revoked_at IS NULL AND g.valid_from<=? AND (g.valid_until IS NULL OR g.valid_until>?) AND sc.enabled=TRUE AND sc.unit_code=? AND sc.site_code=?",titular,now,now,s(root,"unit_code"),s(root,"site_code"))
            .stream().filter(g->n(g,"scope_id")==reception || !b(root,"regional")&&s(root,"area_code").isBlank()&&s(g,"role_code").equals("DIRECCION")&&!s(scope(n(g,"scope_id")),"code").equals("UPR-REQ")).toList();
    }
    public void substituteEligible(long userId,long reception){
        var root=scope(reception);var candidate=user(userId);
        boolean eligible=db.rows("SELECT id FROM org_scope WHERE unit_code=? AND site_code=? AND enabled=TRUE",s(root,"unit_code"),s(root,"site_code"))
            .stream().anyMatch(r->(s(root,"area_code").isBlank()&&!b(root,"regional")||n(r,"id")==reception)&&candidate.professional(n(r,"id")));
        Problem.require(eligible,"El subrogante necesita una funcion profesional vigente en el ambito o en un area de esa unidad.");
    }
    public boolean canOpenCaseUnder(long reqScope,long caseScope){
        var principal=scope(reqScope);var target=scope(caseScope);
        if(b(principal,"regional"))return reqScope==caseScope;
        return !b(target,"regional")&&s(principal,"unit_code").equals(s(target,"unit_code"))
            &&s(principal,"site_code").equals(s(target,"site_code"));
    }
    public boolean canDistribute(Actor a,Map<String,Object> com) {
        long origin=n(com,"origin_scope");
        return distributes(a,origin,regional(origin));
    }
    private static boolean distributes(Actor a,long origin,boolean regional){return regional?a.head(origin):a.role("DISTRIBUIDOR");}
    public boolean member(Actor a,Map<String,Object> c) {
        long scope=n(c,"scope_id");
        return a.professional(scope)&&(n(c,"responsible_id")==a.id()||db.count("SELECT COUNT(*) FROM case_member WHERE case_id=? AND user_id=?",n(c,"id"),a.id())>0);
    }
    // Los originales de una COM pueden contener materias ajenas: lectura transversal de Caso no abre todos sus archivos.
    public boolean caseSourceDocuments(Actor a,Map<String,Object> c) {
        return a.head(n(c,"scope_id"))||member(a,c);
    }
    public boolean caseRead(Actor a,Map<String,Object> c) {
        long scope=n(c,"scope_id");
        if(a.head(scope)||member(a,c)||a.at("LECTURA",scope))return true;
        if(!regional(scope))return false;
        for(var g:a.grants())if(g.role().equals("DIRECCION")&&g.scope()!=null){
            String unit=s(scope(g.scope()),"unit_code");
            if(Set.of("UPR","UPD").contains(unit)&&unit.equals(s(c,"matter")))return true;
        }
        return false;
    }
    public boolean caseWrite(Actor a,Map<String,Object> c) { return a.head(n(c,"scope_id"))||member(a,c); }
    public boolean reserved(Actor a,Map<String,Object> c) { return a.head(n(c,"scope_id"))||n(c,"responsible_id")==a.id()&&a.professional(n(c,"scope_id")); }
    public boolean reqMain(Actor a,Map<String,Object> r) {
        long scope=n(r,"principal_scope");
        return a.head(scope)||Objects.equals(nullableId(r,"attention_user"),a.id())&&a.registrar(n(r,"attention_scope"));
    }
    // --- Primera atencion ------------------------------------------------------------------
    public long pendingHandoffs(long req){return db.count("SELECT COUNT(*) FROM attention_handoff WHERE requirement_id=? AND state='PENDIENTE'",req);}
    /** Destinatario vigente de una entrega pendiente: funcion receptora o profesional designado. */
    public boolean handoffRecipient(Actor a,Map<String,Object> h){
        if(!s(h,"state").equals("PENDIENTE"))return false;
        return s(h,"recipient_kind").equals("JEFATURA")?solReceiver(a,n(h,"target_scope")):
            Objects.equals(nullableId(h,"target_user"),a.id())&&a.professional(n(h,"target_scope"));
    }
    public boolean pendingAttentionRecipient(Actor a,Map<String,Object> r){
        return db.rows("SELECT * FROM attention_handoff WHERE requirement_id=? AND state='PENDIENTE'",n(r,"id")).stream().anyMatch(h->handoffRecipient(a,h));
    }
    /** Ficha editable: encargado o jefatura principal, REQ abierto, sin Caso ni entrega pendiente. */
    public boolean attentionWritable(Actor a,Map<String,Object> r){
        return s(r,"state").equals("ABIERTO")&&reqMain(a,r)
            &&db.count("SELECT COUNT(*) FROM case_record WHERE requirement_id=?",n(r,"id"))==0
            &&pendingHandoffs(n(r,"id"))==0;
    }
    public boolean attentionEditable(Actor a,Map<String,Object> r){
        return attentionWritable(a,r)&&db.count("SELECT COUNT(*) FROM attention_record WHERE requirement_id=?",n(r,"id"))==1;
    }
    public boolean reqRead(Actor a,Map<String,Object> r) {
        if(pendingAttentionRecipient(a,r))return true;
        if(n(r,"created_by")==a.id()&&db.count("SELECT COUNT(*) FROM attention_record WHERE requirement_id=?",n(r,"id"))==1)return true; // Resumen de continuidad, no ficha completa.
        if(reqMain(a,r)||a.at("LECTURA",n(r,"principal_scope")))return true;
        return db.rows("SELECT * FROM case_record WHERE requirement_id=?",n(r,"id")).stream().anyMatch(c->caseRead(a,c));
    }
    public boolean comRead(Actor a,Map<String,Object> c) {
        if(n(c,"created_by")==a.id())return true;
        if(canDistribute(a,c))return true;
        if(db.rows("SELECT destination_scope FROM solicitation WHERE communication_id=? AND state='PENDIENTE'",n(c,"id")).stream().anyMatch(r->solReceiver(a,n(r,"destination_scope"))))return true;
        if(db.rows("SELECT * FROM requirement WHERE communication_id=?",n(c,"id")).stream().anyMatch(r->reqMain(a,r)||pendingAttentionRecipient(a,r)))return true;
        for(var l:db.rows("SELECT * FROM communication_link WHERE communication_id=? AND returned_ms IS NULL",n(c,"id"))){
            var r=db.one("SELECT * FROM requirement WHERE id=?",n(l,"requirement_id"));
            if(a.head(n(r,"principal_scope")))return true;
            if(s(l,"state").equals("ADMITIDA")){
                Long caseId=nullableId(l,"target_case_id");
                if(caseId!=null&&caseRead(a,db.one("SELECT * FROM case_record WHERE id=?",caseId)))return true;
                if(caseId==null&&reqMain(a,r))return true;
            }
        }
        return false;
    }
    public void head(Actor a,long scope) { scope(scope);if(!a.head(scope))Problem.denied(); }
    public void assignable(long user,long scope) { if(!this.user(user).professional(scope))throw new Problem(400,"Responsable sin funcion profesional o jefatura en el destino."); }
    public Map<String,Object> caseForRead(Actor a,long id) { var r=db.one("SELECT * FROM case_record WHERE id=?",id);if(!caseRead(a,r))Problem.denied();return r; }
    public Map<String,Object> caseForWrite(Actor a,long id) { var r=caseForRead(a,id);if(!caseWrite(a,r))Problem.denied();return r; }
    public Map<String,Object> reqForRead(Actor a,long id) { var r=db.one("SELECT * FROM requirement WHERE id=?",id);if(!reqRead(a,r))Problem.denied();return r; }
    public Map<String,Object> comForRead(Actor a,long id) { var r=db.one("SELECT * FROM communication WHERE id=?",id);if(!comRead(a,r))Problem.denied();return r; }
    public void notice(long user,String text,String kind,long id) {
        db.update("INSERT INTO inbox_notice(user_id,at_ms,text_value,resource_type,resource_id) VALUES(?,?,?,?,?)",user,System.currentTimeMillis(),text,kind,id);
    }
    // --- Avisos a funciones efectivas (incluye subrogancias vigentes) ------------------------
    private void notifyWhere(java.util.function.Predicate<Actor> recipient,String text,String kind,long id) {
        for(var a:enabledActors())if(recipient.test(a))notice(a.id(),text,kind,id);
    }
    /** Avisa a la funcion receptora del ambito (Direccion, Coordinadora UPR-REQ o coordinacion regional). */
    public void notifyReceivers(long scope,String text,String kind,long id) {
        var target=scope(scope);notifyWhere(a->receiver(a,target),text,kind,id);
    }
    public void notifySolReceivers(long scope,String text,long id) { notifyReceivers(scope,text,"SOL",id); }
    public void notifyHeads(long scope,String text,String kind,long id) { notifyWhere(a->a.head(scope),text,kind,id); }
    public void notifyDistributors(Map<String,Object> com,String text) {
        long origin=n(com,"origin_scope");boolean regional=regional(origin);
        notifyWhere(a->distributes(a,origin,regional),text,"COM",n(com,"id"));
    }
}
