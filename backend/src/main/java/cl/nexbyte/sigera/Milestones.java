package cl.nexbyte.sigera;

import java.util.*;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static cl.nexbyte.sigera.Db.*;

/**
 * Hitos de la cadena COM -> SOL -> REQ -> Caso (0.5.0, D6, V14, gate G10): dato derivado para los reportes de tiempos.
 * Los comandos escriben cada hito con Lifecycle.milestone en la misma transaccion del cambio de estado, con el mismo
 * instante que guarda su fuente (columna de la tabla o evento de auditoria). Por eso la carga historica reproduce
 * exactamente lo que escriben los comandos, salvo reconstructed=TRUE:
 *
 * <pre>
 * COM_REGISTRADA        communication.created_ms              SOL_DISTRIBUIDA/REDIRIGIDA/DEVUELTA/DESCARTADA  auditoria
 * SOL_ASUMIDA, REQ_CREADO requirement.created_ms              REQ_CERRADO  closure_record sin Caso (nunca antes de crear el REQ)
 * REQ_REACTIVADO, CASO_REABIERTO, CASO_ESPERA, CASO_REANUDADO, CIERRE_SOLICITADO/RETIRADO/DEVUELTO,
 * TAREA_COMPLETADA/CANCELADA  auditoria                        CASO_CREADO  case_record (+ primera asignacion)
 * CASO_ASIGNADO, ASIGNACION_ACUSADA  case_assignment            CASO_CERRADO closure_record del Caso
 * TAREA_CREADA          task_item                              ENTREGA_*  attention_handoff      FLUJO_*  flow_request
 * </pre>
 *
 * La carga historica (marcador MILESTONES_V14) recorre lotes de COM, cada uno en su transaccion con el lock global,
 * y solo reconstruye los eventos anteriores al primer hito escrito en vivo: un comando que llega durante el arranque
 * nunca queda duplicado, y un lote repetido tras un corte reemplaza sus propios hitos reconstruidos. Lo que no
 * reconoce (eventos de formato antiguo o desconocido) lo omite y lo cuenta; la cantidad queda en el marcador, en el
 * log y en los avisos de Administracion. Un error de base de datos detiene el arranque.
 */
@Component
public class Milestones {
    private static final Logger LOG=LoggerFactory.getLogger(Milestones.class);
    static final String MARKER="MILESTONES_V14";
    static final int BATCH=500;
    /** Un hito: evento, entidad, cadena, quien actua, persona a cargo o destinataria, ambito, instante y codigo. */
    record Hito(String event,String type,long resource,long com,Long req,Long cas,long actor,Long subject,Long scope,long at,String detail){ }

    static final Set<String> FLOW_KINDS=Set.of("COLABORACION","TRANSFERENCIA","DERIVACION");
    static final Set<String> FLOW_DECISIONS=Set.of("ACEPTADA","RECHAZADA","RETIRADA");
    static final Set<String> HANDOFF_DECISIONS=Set.of("RECIBIDA","DEVUELTA","RETIRADA");
    /** Acciones de auditoria que la carga lee, con el tipo de recurso que deben tener. */
    static final Map<String,String> SOL_ACTIONS=Map.of("SOL_DISTRIBUIDA","SOL_DISTRIBUIDA","SOL_REDIRIGIDA","SOL_REDIRIGIDA","SOL_DEVUELTA_DISTRIBUCION","SOL_DEVUELTA","SOL_INVALIDA","SOL_DESCARTADA","SOL_DUPLICADA","SOL_DESCARTADA");
    static final Map<String,String> CASE_ACTIONS=Map.of("CASE_WAIT","CASO_ESPERA","CASE_RESUME","CASO_REANUDADO","CASE_REQUEST_CLOSE","CIERRE_SOLICITADO","CASE_WITHDRAW_CLOSE","CIERRE_RETIRADO","CASE_RETURN","CIERRE_DEVUELTO","CASO_REABIERTO","CASO_REABIERTO");
    static final Map<String,String> TASK_ACTIONS=Map.of("TASK_COMPLETE","TAREA_COMPLETADA","TASK_CANCEL","TAREA_CANCELADA");
    static final Set<String> REQ_ACTIONS=Set.of("REQ_REACTIVADO");
    private static final Pattern TASK_NOTE=Pattern.compile("^Tarea (\\d+)\\.");

    private final Db db;
    private final TransactionTemplate tx;
    /** COM por lote de la carga historica (las pruebas lo reducen para recorrer varios lotes). */
    int batch=BATCH;
    public Milestones(Db db,PlatformTransactionManager manager){this.db=db;this.tx=new TransactionTemplate(manager);}

    /** Escritura en vivo, dentro de la transaccion del comando (via Lifecycle.milestone). */
    void write(Hito h){insert(h,false);}
    private void insert(Hito h,boolean rebuilt){
        db.update("INSERT INTO milestone(event,resource_type,resource_id,communication_id,requirement_id,case_id,actor_id,subject_user,scope_id,at_ms,detail,reconstructed) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
            h.event(),h.type(),h.resource(),h.com(),h.req(),h.cas(),h.actor(),h.subject(),h.scope(),h.at(),h.detail(),rebuilt);
    }

    /** Primer hito escrito en vivo: todo evento anterior es historico; los posteriores ya tienen su hito. */
    private long cutoff(){
        var first=db.one("SELECT MIN(at_ms) AS first FROM milestone WHERE reconstructed=FALSE");
        return first.get("first")==null?Long.MAX_VALUE:n(first,"first");
    }

    /** Carga historica, una sola vez por instancia. */
    void rebuild(){
        if(Boolean.TRUE.equals(tx.execute(st->db.count("SELECT COUNT(*) FROM application_marker WHERE code=?",MARKER)>0)))return;
        long last=0,coms=0,written=0,omitted=0;
        while(true){
            final long from=last;
            long[] step=tx.execute(st->{
                db.lock();
                var ids=db.rows("SELECT id FROM communication WHERE id>? ORDER BY id LIMIT "+batch,from);
                if(ids.isEmpty())return null;
                long to=n(ids.getLast(),"id");
                db.update("DELETE FROM milestone WHERE reconstructed=TRUE AND communication_id>? AND communication_id<=?",from,to);
                var batch=new Batch(from,to,cutoff());
                batch.read();
                batch.hitos.forEach(h->insert(h,true));
                return new long[]{to,ids.size(),batch.hitos.size(),batch.omitted};
            });
            if(step==null)break;
            last=step[0];coms+=step[1];written+=step[2];omitted+=step[3];
        }
        final long inBatches=omitted;
        long total=Objects.requireNonNull(tx.execute(st->{
            db.lock();
            long all=inBatches+orphans(cutoff());
            if(db.count("SELECT COUNT(*) FROM application_marker WHERE code=?",MARKER)==0)
                db.update("INSERT INTO application_marker(code,applied_ms,amount) VALUES(?,?,?)",MARKER,System.currentTimeMillis(),all);
            return all;
        }));
        if(coms>0)LOG.info("Hitos: {} reconstruidos en {} comunicaciones.",written,coms);
        if(total>0)LOG.warn("Hitos: {} eventos historicos omitidos por formato no reconocido. Revisar en Administracion.",total);
    }

    /** Omisiones registradas por la carga historica (0 si no hubo o aun no corre). */
    static long omitted(Db db){
        var rows=db.rows("SELECT amount FROM application_marker WHERE code=? AND amount IS NOT NULL",MARKER);
        return rows.isEmpty()?0:n(rows.getFirst(),"amount");
    }

    /**
     * Eventos de auditoria que la carga lee pero que no pertenecen a ningun registro de la cadena: tipo de recurso de
     * otra version o recurso inexistente. Ningun lote los alcanza, asi que se cuentan aparte.
     */
    private long orphans(long cutoff){
        return orphan(SOL_ACTIONS.keySet(),"SOL","solicitation",cutoff)
            +orphan(REQ_ACTIONS,"REQ","requirement",cutoff)
            +orphan(union(CASE_ACTIONS.keySet(),TASK_ACTIONS.keySet()),"CAS","case_record",cutoff);
    }
    private long orphan(Set<String> actions,String type,String table,long cutoff){
        return db.count("SELECT COUNT(*) FROM audit_event a WHERE a.action IN ("+marks(actions.size())+") AND a.at_ms<? AND NOT (a.resource_type=? AND EXISTS(SELECT 1 FROM "+table+" x WHERE x.id=a.resource_id))",
            args(actions,cutoff,type));
    }
    private static Set<String> union(Set<String> a,Set<String> b){var out=new TreeSet<>(a);out.addAll(b);return out;}
    private static String marks(int n){return String.join(",",Collections.nCopies(n,"?"));}
    private static Object[] args(Collection<String> head,Object... tail){var out=new ArrayList<Object>(new TreeSet<>(head));out.addAll(Arrays.asList(tail));return out.toArray();}
    private static String detail(String value){return value==null||value.isBlank()||value.length()>32?null:value;}

    /** Hitos historicos de las COM con id en (from, to], anteriores al corte. */
    private final class Batch {
        final long from,to,cutoff;
        final List<Hito> hitos=new ArrayList<>();
        long omitted;
        Batch(long from,long to,long cutoff){this.from=from;this.to=to;this.cutoff=cutoff;}

        private void add(Hito h){if(h.at()<cutoff)hitos.add(h);}
        private void omit(long at){if(at<cutoff)omitted++;}
        private List<Map<String,Object>> rows(String sql,Object... head){
            var values=new ArrayList<Object>(Arrays.asList(head));values.add(from);values.add(to);
            return db.rows(sql,values.toArray());
        }

        void read(){
            for(var c:rows("SELECT id,created_ms,created_by,origin_scope FROM communication WHERE id>? AND id<=?"))
                add(new Hito("COM_REGISTRADA","COM",n(c,"id"),n(c,"id"),null,null,n(c,"created_by"),null,nullableId(c,"origin_scope"),n(c,"created_ms"),null));

            // REQ: la persona a cargo al crear es quien entrega en la primera entrega (si la hubo); el ambito, el de su evento de creacion.
            var handoffs=rows("SELECT h.*,r.communication_id FROM attention_handoff h JOIN requirement r ON r.id=h.requirement_id WHERE r.communication_id>? AND r.communication_id<=? ORDER BY h.id");
            var firstHolder=new HashMap<Long,Long>();
            for(var h:handoffs)firstHolder.putIfAbsent(n(h,"requirement_id"),n(h,"from_user"));
            var createdScope=new HashMap<Long,Long>();
            for(var e:rows("SELECT a.resource_id,a.scope_id FROM audit_event a JOIN requirement r ON r.id=a.resource_id WHERE a.resource_type='REQ' AND a.action IN ('REQ_CREADO','ATENCION_INICIADA') AND r.communication_id>? AND r.communication_id<=? ORDER BY a.id"))
                if(e.get("scope_id")!=null)createdScope.putIfAbsent(n(e,"resource_id"),n(e,"scope_id"));
            var direct=new HashSet<Long>();
            for(var e:rows("SELECT a.resource_id FROM audit_event a JOIN solicitation s ON s.id=a.resource_id WHERE a.resource_type='SOL' AND a.action='ASUNCION_INGRESO_PROPIO' AND s.communication_id>? AND s.communication_id<=?"))
                direct.add(n(e,"resource_id"));
            for(var r:rows("SELECT id,communication_id,solicitation_id,created_ms,created_by,attention_user,principal_scope FROM requirement WHERE communication_id>? AND communication_id<=?")){
                long req=n(r,"id"),com=n(r,"communication_id"),at=n(r,"created_ms"),actor=n(r,"created_by");
                Long subject=firstHolder.containsKey(req)?firstHolder.get(req):nullableId(r,"attention_user");
                Long scope=createdScope.getOrDefault(req,nullableId(r,"principal_scope"));
                add(new Hito("REQ_CREADO","REQ",req,com,req,null,actor,subject,scope,at,null));
                Long sol=nullableId(r,"solicitation_id");
                if(sol!=null)add(new Hito("SOL_ASUMIDA","SOL",sol,com,req,null,actor,subject,scope,at,direct.contains(sol)?"DIRECTA":null));
            }
            for(var k:rows("SELECT k.requirement_id,k.actor_id,k.closed_ms,k.mode,r.communication_id,r.created_ms,r.principal_scope FROM closure_record k JOIN requirement r ON r.id=k.requirement_id WHERE k.case_id IS NULL AND r.communication_id>? AND r.communication_id<=? ORDER BY k.id")){
                long at=Math.max(n(k,"closed_ms"),n(k,"created_ms")),req=n(k,"requirement_id");
                String mode=detail(s(k,"mode"));
                if(mode==null){omit(at);continue;}
                add(new Hito("REQ_CERRADO","REQ",req,n(k,"communication_id"),req,null,n(k,"actor_id"),null,nullableId(k,"principal_scope"),at,mode));
            }
            for(var e:rows("SELECT a.action,a.resource_id,a.actor_id,a.at_ms,a.scope_id,s.communication_id FROM audit_event a JOIN solicitation s ON s.id=a.resource_id WHERE a.resource_type='SOL' AND a.action IN ("+marks(SOL_ACTIONS.size())+") AND s.communication_id>? AND s.communication_id<=? ORDER BY a.id",new TreeSet<>(SOL_ACTIONS.keySet()).toArray())){
                String action=s(e,"action");
                String code=action.equals("SOL_INVALIDA")?"INVALIDA":action.equals("SOL_DUPLICADA")?"DUPLICADA":null;
                add(new Hito(SOL_ACTIONS.get(action),"SOL",n(e,"resource_id"),n(e,"communication_id"),null,null,n(e,"actor_id"),null,nullableId(e,"scope_id"),n(e,"at_ms"),code));
            }
            for(var e:rows("SELECT a.resource_id,a.actor_id,a.at_ms,a.scope_id,r.communication_id FROM audit_event a JOIN requirement r ON r.id=a.resource_id WHERE a.resource_type='REQ' AND a.action='REQ_REACTIVADO' AND r.communication_id>? AND r.communication_id<=? ORDER BY a.id"))
                add(new Hito("REQ_REACTIVADO","REQ",n(e,"resource_id"),n(e,"communication_id"),n(e,"resource_id"),null,n(e,"actor_id"),null,nullableId(e,"scope_id"),n(e,"at_ms"),null));

            // Casos, asignaciones, cierres y tareas.
            var assignments=rows("SELECT g.*,c.requirement_id,c.scope_id,r.communication_id FROM case_assignment g JOIN case_record c ON c.id=g.case_id JOIN requirement r ON r.id=c.requirement_id WHERE r.communication_id>? AND r.communication_id<=? ORDER BY g.id");
            var firstResponsible=new HashMap<Long,Long>();
            for(var g:assignments){
                long id=n(g,"id"),cas=n(g,"case_id"),com=n(g,"communication_id"),req=n(g,"requirement_id"),user=n(g,"user_id");Long scope=nullableId(g,"scope_id");
                firstResponsible.putIfAbsent(cas,user);
                add(new Hito("CASO_ASIGNADO","ASIGNACION",id,com,req,cas,n(g,"assigned_by"),user,scope,n(g,"assigned_ms"),null));
                if(g.get("acknowledged_ms")!=null)add(new Hito("ASIGNACION_ACUSADA","ASIGNACION",id,com,req,cas,user,user,scope,n(g,"acknowledged_ms"),null));
            }
            for(var c:rows("SELECT c.id,c.requirement_id,c.scope_id,c.created_ms,c.created_by,r.communication_id FROM case_record c JOIN requirement r ON r.id=c.requirement_id WHERE r.communication_id>? AND r.communication_id<=?")){
                long cas=n(c,"id");
                add(new Hito("CASO_CREADO","CAS",cas,n(c,"communication_id"),n(c,"requirement_id"),cas,n(c,"created_by"),firstResponsible.get(cas),nullableId(c,"scope_id"),n(c,"created_ms"),null));
            }
            for(var k:rows("SELECT k.case_id,k.requirement_id,k.actor_id,k.closed_ms,k.mode,c.scope_id,r.communication_id FROM closure_record k JOIN case_record c ON c.id=k.case_id JOIN requirement r ON r.id=k.requirement_id WHERE r.communication_id>? AND r.communication_id<=? ORDER BY k.id")){
                long at=n(k,"closed_ms"),cas=n(k,"case_id");
                String mode=detail(s(k,"mode"));
                if(mode==null){omit(at);continue;}
                add(new Hito("CASO_CERRADO","CAS",cas,n(k,"communication_id"),n(k,"requirement_id"),cas,n(k,"actor_id"),null,nullableId(k,"scope_id"),at,mode));
            }
            var tasks=new HashMap<Long,Map<String,Object>>();
            for(var t:rows("SELECT t.id,t.case_id,t.executor_id,t.created_by,t.created_ms,c.requirement_id,c.scope_id,r.communication_id FROM task_item t JOIN case_record c ON c.id=t.case_id JOIN requirement r ON r.id=c.requirement_id WHERE r.communication_id>? AND r.communication_id<=?")){
                tasks.put(n(t,"id"),t);
                add(new Hito("TAREA_CREADA","TAREA",n(t,"id"),n(t,"communication_id"),n(t,"requirement_id"),n(t,"case_id"),n(t,"created_by"),n(t,"executor_id"),nullableId(t,"scope_id"),n(t,"created_ms"),null));
            }
            var caseActions=union(CASE_ACTIONS.keySet(),TASK_ACTIONS.keySet());
            for(var e:rows("SELECT a.action,a.resource_id,a.actor_id,a.at_ms,a.scope_id,a.note,c.requirement_id,r.communication_id FROM audit_event a JOIN case_record c ON c.id=a.resource_id JOIN requirement r ON r.id=c.requirement_id WHERE a.resource_type='CAS' AND a.action IN ("+marks(caseActions.size())+") AND r.communication_id>? AND r.communication_id<=? ORDER BY a.id",caseActions.toArray())){
                String action=s(e,"action");long cas=n(e,"resource_id"),at=n(e,"at_ms");
                if(CASE_ACTIONS.containsKey(action)){
                    add(new Hito(CASE_ACTIONS.get(action),"CAS",cas,n(e,"communication_id"),n(e,"requirement_id"),cas,n(e,"actor_id"),null,nullableId(e,"scope_id"),at,null));
                    continue;
                }
                // TASK_COMPLETE / TASK_CANCEL: la tarea esta en la nota ("Tarea N. ..."); sin ella, o de otro Caso, se omite.
                var m=TASK_NOTE.matcher(s(e,"note"));
                var task=m.find()?tasks.get(Long.parseLong(m.group(1))):null;
                if(task==null||n(task,"case_id")!=cas){omit(at);continue;}
                add(new Hito(TASK_ACTIONS.get(action),"TAREA",n(task,"id"),n(e,"communication_id"),n(e,"requirement_id"),cas,n(e,"actor_id"),n(task,"executor_id"),nullableId(e,"scope_id"),at,null));
            }

            // Entregas de primera atencion y flujos entre unidades.
            for(var h:handoffs){
                long id=n(h,"id"),req=n(h,"requirement_id"),com=n(h,"communication_id");
                add(new Hito("ENTREGA_ENVIADA","ENTREGA",id,com,req,null,n(h,"sent_by"),nullableId(h,"target_user"),nullableId(h,"target_scope"),n(h,"sent_ms"),null));
                if(h.get("decided_ms")==null)continue;
                long at=n(h,"decided_ms");
                if(!HANDOFF_DECISIONS.contains(s(h,"state"))||h.get("decided_by")==null){omit(at);continue;}
                add(new Hito("ENTREGA_DECIDIDA","ENTREGA",id,com,req,null,n(h,"decided_by"),nullableId(h,"target_user"),nullableId(h,"target_scope"),at,s(h,"state")));
            }
            for(var f:rows("SELECT f.*,r.communication_id FROM flow_request f JOIN requirement r ON r.id=f.requirement_id WHERE r.communication_id>? AND r.communication_id<=? ORDER BY f.id")){
                long id=n(f,"id"),req=n(f,"requirement_id"),com=n(f,"communication_id");Long cas=nullableId(f,"origin_case_id"),scope=nullableId(f,"target_scope");
                boolean known=FLOW_KINDS.contains(s(f,"kind"));
                if(known)add(new Hito("FLUJO_SOLICITADO","FLUJO",id,com,req,cas,n(f,"requested_by"),null,scope,n(f,"created_ms"),s(f,"kind")));
                else omit(n(f,"created_ms"));
                if(f.get("decided_ms")==null)continue;
                long at=n(f,"decided_ms");
                if(!known||!FLOW_DECISIONS.contains(s(f,"state"))||f.get("decided_by")==null){omit(at);continue;}
                add(new Hito("FLUJO_DECIDIDO","FLUJO",id,com,req,cas,n(f,"decided_by"),null,scope,at,s(f,"state")));
            }
        }
    }
}
