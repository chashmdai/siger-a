package cl.nexbyte.sigera;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static cl.nexbyte.sigera.Db.*;

/**
 * Liberacion por analisis en origen (D17 de docs/PLAN_0_6_0.md, gate H04c): modo de emergencia mientras la instancia no
 * tenga un motor antivirus admitido. Los originales del Formulario se liberan al registrarse (FormularioReception), sobre
 * la base del analisis que hace el Formulario antes de aceptar la entrega y de la verificacion de SIGER de la firma
 * Ed25519 de la entrega y del SHA-256 de cada original. Nunca libera adjuntos subidos en SIGER.
 *
 * Activacion expresa por instancia: config\liberacion-origen.json, escrito por scripts\Set-OriginRelease.ps1 y leido una
 * sola vez al arrancar (siger.documents.origin-release.file, que solo reciben el servicio y el ciclo manual que registra
 * entregas). Lista cerrada de campos, version 2, sin fecha de termino (D17 revisada en H05): el modo rige hasta que se
 * configure un motor antivirus admitido (CON_MOTOR lo deja sin efecto) o se desactive con -Desactivar; la revision
 * periodica queda a cargo del responsable. Falla cerrado: archivo ilegible, con otros campos (tambien un termino "until"
 * de la version 1), activacion futura o un motor configurado dejan el modo sin efecto (los originales siguen en
 * cuarentena) con un aviso para TI. Con el modo vigente, TI ve un aviso permanente.
 *
 * La evidencia es la auditoria (DOCUMENTO_LIBERADO_EN_ORIGEN por original, con el motivo); no se escribe document_scan,
 * asi que nunca se presenta como un analisis LIMPIO de un motor de la VM. El estado del modo se audita al arrancar
 * (recurso SISTEMA/17), una vez por cambio. Con el modo vigente, al arrancar se liberan tambien los originales del
 * Formulario que quedaron en cuarentena mientras no regia, con la misma verificacion, cada uno en su transaccion.
 * La auditoria de arranque y la liberacion retroactiva no corren en la prueba de Graph, la sonda del Formulario,
 * migrate-only ni la recuperacion de TI, aunque la propiedad llegue.
 */
@Service
class OriginRelease {
    private static final Logger LOG=LoggerFactory.getLogger(OriginRelease.class);
    static final ZoneId ZONE=ZoneId.of("America/Santiago");
    static final int VERSION=2;
    static final long FUTURE_TOLERANCE_MS=5*60_000L,RESOURCE=17;
    static final String KIND="LIBERACION_ORIGEN",REASON="analisis en origen del Formulario",RELEASED="DOCUMENTO_LIBERADO_EN_ORIGEN";
    static final Set<String> FIELDS=Set.of("kind","version","id","activatedUtc","account","reason");
    static final List<String> EVENTS=List.of("LIBERACION_ORIGEN_ACTIVADA","LIBERACION_ORIGEN_INVALIDA","LIBERACION_ORIGEN_DESACTIVADA");
    /** Columna release_basis de las listas de documentos (alias d de document_item): ORIGEN si se libero por D17. */
    static final String BASIS_SQL="CASE WHEN d.quarantined=FALSE AND EXISTS(SELECT 1 FROM audit_event e WHERE e.resource_type='DOCUMENTO' AND e.resource_id=d.id AND e.action='"+RELEASED+"') THEN 'ORIGEN' ELSE NULL END AS release_basis";
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private static final DateTimeFormatter DAY=DateTimeFormatter.ofPattern("dd-MM-yyyy");

    /** Configuracion leida al arrancar: INACTIVO (sin archivo), ACTIVO, INVALIDO (con detalle) o CON_MOTOR. */
    record Config(String state,String detail,String id,long activated,String account,String reason){
        static Config of(String state,String detail){return new Config(state,detail,null,0,null,null);}
    }

    private final Db db;private final Documents documents;private final TransactionTemplate tx;
    final Config config;final boolean applies;

    @Autowired
    OriginRelease(Db db,Documents documents,DocumentScanner scanner,PlatformTransactionManager manager,Environment env){
        this(db,documents,scanner.configured(),manager,env,Clock.systemUTC());
    }
    OriginRelease(Db db,Documents documents,boolean engine,PlatformTransactionManager manager,Environment env,Clock clock){
        this.db=db;this.documents=documents;this.tx=new TransactionTemplate(manager);
        String file=env.getProperty("siger.documents.origin-release.file","").strip();
        this.config=file.isEmpty()?Config.of("INACTIVO",null):read(Path.of(file),engine,clock.millis());
        this.applies=!env.getProperty("siger.migrate-only",Boolean.class,false)&&env.getProperty("siger.mail.check.mode","").isBlank()
            &&!env.getProperty("siger.formulario.pull.probe",Boolean.class,false)&&env.getProperty("siger.recover-ti.user","").isBlank();
    }

    // --- Lectura y validacion (pura) ------------------------------------------------------------------
    static Config read(Path file,boolean engine,long now){
        if(engine)return Config.of("CON_MOTOR",null);
        String text;
        try{
            if(Files.isSymbolicLink(file)||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>4096)return Config.of("INVALIDO","ILEGIBLE");
            text=Files.readString(file,StandardCharsets.UTF_8);
        }catch(IOException|RuntimeException e){return Config.of("INVALIDO","ILEGIBLE");}
        return parse(text,now);
    }
    static Config parse(String text,long now){
        if(text.startsWith("﻿"))text=text.substring(1);
        JsonNode root;
        try{root=JSON.readTree(text);}catch(RuntimeException e){return Config.of("INVALIDO","ILEGIBLE");}
        if(root==null||!root.isObject())return Config.of("INVALIDO","ILEGIBLE");
        var names=new HashSet<String>();root.properties().forEach(e->names.add(e.getKey()));
        if(!names.equals(FIELDS))return Config.of("INVALIDO","CAMPOS");
        for(var f:FIELDS)if(!f.equals("version")&&!root.path(f).isString())return Config.of("INVALIDO","CAMPOS");
        if(!KIND.equals(root.path("kind").asString())||!root.path("version").isIntegralNumber()||root.path("version").asLong()!=VERSION)return Config.of("INVALIDO","TIPO");
        String id=root.path("id").asString();
        try{if(!UUID.fromString(id).toString().equals(id.toLowerCase(Locale.ROOT)))return Config.of("INVALIDO","IDENTIFICADOR");}
        catch(IllegalArgumentException e){return Config.of("INVALIDO","IDENTIFICADOR");}
        long activated;
        try{activated=Instant.parse(root.path("activatedUtc").asString()).toEpochMilli();}
        catch(RuntimeException e){return Config.of("INVALIDO","FECHAS");}
        if(activated>now+FUTURE_TOLERANCE_MS)return Config.of("INVALIDO","ACTIVACION_FUTURA");
        String account=root.path("account").asString().strip(),reason=root.path("reason").asString().strip();
        if(account.isEmpty()||account.length()>200||account.chars().anyMatch(Character::isISOControl))return Config.of("INVALIDO","CUENTA");
        if(reason.isEmpty()||reason.length()>500||reason.chars().anyMatch(Character::isISOControl))return Config.of("INVALIDO","MOTIVO");
        return new Config("ACTIVO",null,id.toLowerCase(Locale.ROOT),activated,account,reason);
    }

    // --- Estado ---------------------------------------------------------------------------------------
    /** Estado leido al arrancar; no cambia hasta el proximo inicio (sin termino). */
    String state(){return config.state();}
    /** El modo rige: configuracion valida, sin motor y en un proceso que registra entregas. */
    boolean active(){return applies&&state().equals("ACTIVO");}
    /** Dia de Chile (dd-mm-aaaa) de un instante: el de la activacion. */
    static String day(long at){return Instant.ofEpochMilli(at).atZone(ZONE).format(DAY);}

    /** Texto de la auditoria de cada liberacion (al registrar o al reactivar el modo). */
    String note(boolean retroactive){
        return "Motivo: "+REASON+(retroactive?" (liberado al regir de nuevo el modo; estaba en cuarentena)":"")
            +". Firma Ed25519 de la entrega y SHA-256 del original verificados por SIGER. Modo D17 "+config.id()+" activado el "+day(config.activated())+".";
    }

    /** Politica visible para TI (Auditoria y operacion). */
    Map<String,Object> policy(){
        var out=new LinkedHashMap<String,Object>();
        out.put("state",state());
        if(config.detail()!=null)out.put("detail",config.detail());
        if(config.id()!=null){out.put("since_ms",config.activated());out.put("since_day",day(config.activated()));}
        return out;
    }

    /** Avisos de administracion (TI y AUDITORIA, y log al arrancar). Sin datos de personas. Con el modo vigente, permanente. */
    List<Map<String,Object>> warnings(){
        String state=state();
        long pending=state.equals("INACTIVO")?0:db.count("SELECT COUNT(*) FROM document_item d JOIN external_document x ON x.document_id=d.id WHERE d.quarantined=TRUE");
        String tail=pending==0?"":" Hay "+pending+(pending==1?" original del Formulario en cuarentena.":" originales del Formulario en cuarentena.");
        return switch(state){
            case "ACTIVO" -> List.of(Map.of("code","LIBERACION_ORIGEN_ACTIVA","count",0L,
                "message","Rige el modo de emergencia D17 (activado el "+day(config.activated())+"): los originales del Formulario se liberan al registrarse por el análisis en origen, sin análisis local; los adjuntos subidos en SIGER siguen en cuarentena. Rige hasta que se configure un motor antivirus admitido o se desactive con scripts\\Set-OriginRelease.ps1 -Desactivar."));
            case "INVALIDO" -> List.of(Map.of("code","LIBERACION_ORIGEN_INVALIDA","count",pending,
                "message","La configuración del modo de emergencia D17 no es válida ("+config.detail()+"): no se libera ningún original y rige la cuarentena."+tail+" Vuelve a activarlo con scripts\\Set-OriginRelease.ps1 y reinicia."));
            case "CON_MOTOR" -> List.of(Map.of("code","LIBERACION_ORIGEN_CON_MOTOR","count",pending,
                "message","Hay un motor antivirus configurado y el modo de emergencia D17 sigue activado: el modo no rige y vuelve el análisis local. Desactívalo con scripts\\Set-OriginRelease.ps1 -Desactivar."));
            default -> List.of();
        };
    }

    // --- Arranque ---------------------------------------------------------------------------------------
    /** Auditoria del estado (una vez por cambio) y, con el modo vigente, liberacion de los originales pendientes. */
    void startup(){
        if(!applies)return;
        audit();
        if(active())releasePending();
    }
    /** Evento y texto deterministas del estado actual; se escribe solo si difiere del ultimo evento del modo. */
    private void audit(){
        String state=state();String action,note;
        switch(state){
            case "ACTIVO" -> {action="LIBERACION_ORIGEN_ACTIVADA";note="Modo de emergencia D17 "+config.id()+" activado el "+Instant.ofEpochMilli(config.activated())+" por "+config.account()
                +", sin fecha de termino: rige hasta que se configure un motor antivirus admitido o se desactive. Motivo: "+config.reason();}
            case "INVALIDO" -> {action="LIBERACION_ORIGEN_INVALIDA";note="Configuracion del modo de emergencia D17 no valida ("+config.detail()+"). Rige la cuarentena.";}
            case "CON_MOTOR" -> {action="LIBERACION_ORIGEN_INVALIDA";note="Modo de emergencia D17 activado con un motor antivirus configurado (CON_MOTOR). Rige el analisis local.";}
            default -> {action="LIBERACION_ORIGEN_DESACTIVADA";note="Modo de emergencia D17 desactivado. Rige la cuarentena o el analisis local.";}
        }
        String a=action,t=note;
        tx.executeWithoutResult(st->{
            db.lock();
            var last=db.rows("SELECT action,note FROM audit_event WHERE resource_type='SISTEMA' AND resource_id=? ORDER BY id DESC LIMIT 1",RESOURCE);
            if(last.isEmpty()?a.equals("LIBERACION_ORIGEN_DESACTIVADA"):s(last.getFirst(),"action").equals(a)&&s(last.getFirst(),"note").equals(t))return;
            db.audit(Bootstrap.maintenance(db),a,"SISTEMA",RESOURCE,null,t);
        });
        if(!state.equals("INACTIVO"))LOG.info("Liberacion por analisis en origen (D17): {}.",state);
    }
    /**
     * Originales del Formulario que siguen en cuarentena (sin deteccion previa): cada uno en su transaccion con el lock
     * global, con la misma verificacion que al registrar (SHA-256 completo ahora). Solo se llama con el modo vigente, que no
     * cambia durante el proceso. Solo filas de external_document de
     * una recepcion registrada, cargadas por la identidad tecnica del Formulario; nunca adjuntos subidos en SIGER.
     */
    int[] releasePending(){
        var ids=db.rows("SELECT d.id FROM document_item d JOIN external_document x ON x.document_id=d.id WHERE d.quarantined=TRUE ORDER BY d.id").stream().map(r->n(r,"id")).toList();
        int released=0,kept=0;
        for(long id:ids){
            boolean done=Boolean.TRUE.equals(tx.execute(st->{
                db.lock();
                var rows=db.rows("SELECT d.id,d.storage_key,d.size_bytes,d.sha256,d.quarantined,d.uploaded_by,c.origin_scope FROM document_item d JOIN external_document x ON x.document_id=d.id"
                    +" JOIN communication c ON c.id=x.communication_id JOIN staff u ON u.id=d.uploaded_by"
                    +" WHERE d.id=? AND d.quarantined=TRUE AND d.document_type='INGRESO' AND d.communication_id=x.communication_id AND u.technical=TRUE AND u.username=?"
                    +" AND EXISTS(SELECT 1 FROM formulario_reception f WHERE f.communication_id=x.communication_id AND f.state IN ('REGISTRADA','CONFIRMADA'))",id,FormularioReception.TECHNICAL);
                if(rows.isEmpty())return false;
                var d=rows.getFirst();
                if(db.count("SELECT COUNT(*) FROM document_scan WHERE document_id=? AND verdict='INFECTADO'",id)>0)return false;
                try{if(!documents.externalIntact(s(d,"storage_key"),n(d,"size_bytes"),s(d,"sha256")))return false;}catch(IOException|RuntimeException e){return false;}
                release(Bootstrap.maintenance(db),id,n(d,"origin_scope"),true);
                return true;
            }));
            if(done)released++;else kept++;
        }
        if(released+kept>0)LOG.info("Liberacion por analisis en origen (D17): {} originales pendientes liberados, {} siguen en cuarentena.",released,kept);
        return new int[]{released,kept};
    }
    /** Liberacion de un original del Formulario ya verificado, con su auditoria (dentro de la transaccion del llamador). */
    void release(long actor,long document,long scope,boolean retroactive){
        if(db.update("UPDATE document_item SET quarantined=FALSE WHERE id=? AND quarantined=TRUE",document)!=1)throw new IllegalStateException("Original no liberable.");
        db.audit(actor,RELEASED,"DOCUMENTO",document,scope,note(retroactive));
    }
}
