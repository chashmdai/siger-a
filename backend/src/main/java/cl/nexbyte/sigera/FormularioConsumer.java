package cl.nexbyte.sigera;

import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

/**
 * Un ciclo del consumidor del Formulario, manual o automatico (RecuperarFormulario de SMR como referencia):
 * 1) concilia los ACK pendientes con su reserva y el mismo comprobante; 2) reclama a lo mas una entrega;
 * 3) la valida (ExternalOriginal: contrato, huellas y sello Ed25519) antes de tocar SQL; 4) descarga y custodia sus
 * originales con tamano y SHA-256 firmados; 5) registra COM, originales, comprobante y ACK pendiente en un COMMIT;
 * 6) verifica de nuevo la custodia y solo entonces confirma. Despues del COMMIT nunca informa fallo: el ACK queda
 * pendiente y se concilia en un ciclo posterior. El registro no lleva datos ciudadanos ni la credencial.
 */
final class FormularioConsumer {
    private static final Logger LOG=LoggerFactory.getLogger(FormularioConsumer.class);
    private static final long LOCK_MS=15*60_000L,MARGIN_MS=10_000L;
    private final FormularioClient client;private final FormularioReception reception;private final Documents documents;
    private final FormularioKeys keys;private final FormularioAck ack;private final long scope;

    FormularioConsumer(FormularioClient client,FormularioReception reception,Documents documents,FormularioKeys keys,FormularioAck ack,long scope){
        this.client=client;this.reception=reception;this.documents=documents;this.keys=keys;this.ack=ack;this.scope=scope;
    }

    /** Resultado sin datos ciudadanos: contadores y codigos tecnicos. */
    record Cycle(boolean busy,int reconciled,int claimed,int registered,int confirmed,List<String> incidents) {
        boolean clean(){return !busy&&incidents.isEmpty();}
    }

    /** Sonda sin reclamar entregas (comprobacion previa a la activacion). */
    FormularioClient.Probe probe()throws FormularioClient.Failure{return client.probe();}

    /** Ciclo manual: lease propio de 15 minutos que se libera al terminar. */
    Cycle run(){return run(UUID.randomUUID().toString(),LOCK_MS,true);}

    /**
     * Un ciclo con el lease del titular dado. El consumidor automatico conserva su lease entre ciclos (lo renueva
     * su latido) para que ningun otro proceso de SIGER consuma la misma cola; si lo pierde, el ciclo se detiene sin
     * reclamar, registrar ni confirmar (falla cerrado).
     */
    Cycle run(String holder,long leaseMs,boolean release){
        if(!reception.acquire(holder,System.currentTimeMillis()+leaseMs))return new Cycle(true,0,0,0,0,List.of("CICLO_EN_CURSO"));
        var incidents=new ArrayList<String>();int reconciled=0,claimed=0,registered=0,confirmed=0;
        try{
            for(var p:reception.pendingAcks(10)){
                if(!owned(holder,incidents))return new Cycle(false,reconciled,0,0,0,List.copyOf(incidents));
                if(confirm(p.operation(),UUID.fromString(p.lease()),p.receipt(),p.communication(),incidents))reconciled++;
            }
            if(!owned(holder,incidents))return new Cycle(false,reconciled,0,0,0,List.copyOf(incidents));
            List<FormularioClient.Claim> batch;
            try{batch=client.claim();}catch(FormularioClient.Failure f){incidents.add(f.code);return new Cycle(false,reconciled,0,0,0,List.copyOf(incidents));}
            for(var c:batch){
                claimed++;
                var r=receive(c,holder,incidents);
                if(r==null)continue;
                if(r.created())registered++;
                if(!owned(holder,incidents))break;
                if(confirm(c.operation(),c.lease(),r.receipt(),r.communication(),incidents))confirmed++;
            }
            return new Cycle(false,reconciled,claimed,registered,confirmed,List.copyOf(incidents));
        }finally{if(release)reception.release(holder);}
    }
    private boolean owned(String holder,List<String> incidents){
        if(reception.holds(holder))return true;
        incidents.add("EXCLUSION_PERDIDA");return false;
    }

    /** Valida, custodia y registra. Devuelve null si no hubo COMMIT (y en ese caso se informa el fallo contractual). */
    private FormularioReception.Registered receive(FormularioClient.Claim c,String holder,List<String> incidents){
        ExternalOriginal o;
        try{
            // Configuracion propia (clave no instalada) o reloj: no son defectos del documento; no van a cuarentena.
            var root=JsonMapper.builder().build().readTree(c.document());
            if(!keys.keys().containsKey(root.path("custodia").path("keyId").asString())){fail(c,"RECEPTOR_NO_DISPONIBLE","CLAVE_CUSTODIA_NO_CONFIABLE",incidents);return null;}
            if(Instant.parse(root.path("comunicacion").path("recibidaEn").asString()).toEpochMilli()>System.currentTimeMillis()){fail(c,"TRANSITORIO","FECHA_FUTURA",incidents);return null;}
            o=ExternalOriginal.parse(c.document(),keys.keys());
            if(!o.externalId().equals(c.operation()))throw new Problem(400,"Operacion distinta");
        }catch(RuntimeException invalid){
            fail(c,"CONTRATO_INVALIDO","CONTRATO_INVALIDO",incidents);
            try{reception.rejected(c.operation(),null,"CONTRATO_INVALIDO");}catch(RuntimeException ignored){incidents.add("ESTADO_NO_PERSISTIDO");}
            return null;
        }
        long deadline=c.until()-MARGIN_MS;
        for(var a:o.attachments()){
            String key=Documents.externalKey(o.externalId(),a.id());
            try{
                if(documents.externalIntact(key,a.size(),a.sha256()))continue;
                client.download(o.externalId(),a.id(),c.lease(),a.size(),deadline,body->documents.custodyExternal(key,a.size(),a.sha256(),body));
            }catch(FormularioClient.Failure f){fail(c,"TRANSITORIO",f.code,incidents);return null;}
            catch(java.io.IOException|RuntimeException e){fail(c,"RECEPTOR_NO_DISPONIBLE","CUSTODIA_LOCAL_FALLIDA",incidents);return null;}
        }
        // Sin lease vigente no se registra: la reserva vence en el Formulario y la entrega se reintenta.
        if(!owned(holder,incidents))return null;
        try{
            return reception.register(o,scope,c.lease(),c.until(),c.attempt(),ack);
        }catch(RuntimeException e){
            // Sin COMMIT: la entrega vuelve a PENDIENTE en el Formulario; nada de esta operacion quedo registrado.
            // Solo el tipo de error: el mensaje de SQL podria contener valores del documento.
            LOG.warn("Registro de la entrega sin COMMIT ({}).",e.getClass().getName());
            fail(c,"RECEPTOR_NO_DISPONIBLE",e instanceof Problem p?"REGISTRO_RECHAZADO_"+p.status:"REGISTRO_NO_DISPONIBLE",incidents);
            return null;
        }
    }

    /** Verifica la custodia completa y envia el mismo comprobante. true solo con 204. */
    private boolean confirm(String operation,UUID lease,String receipt,long communication,List<String> incidents){
        try{
            var stored=reception.custody(communication);
            if(stored.size()!=reception.signedOriginals(communication))throw new java.io.IOException();
            for(var f:stored)if(!documents.externalIntact(f.key(),f.size(),f.sha256()))throw new java.io.IOException();
            ack.verify(receipt);
        }catch(java.io.IOException|RuntimeException e){
            reception.custodyFailed(operation);incidents.add("CUSTODIA_NO_VERIFICADA");return false;
        }
        int status;
        try{status=client.confirm(operation,lease,receipt);}
        catch(FormularioClient.Failure f){reception.ackDeferred(operation,lease.toString(),0);incidents.add("ACK_"+f.code);return false;}
        if(status==204){reception.acknowledged(operation,lease.toString());return true;}
        if(status==409)reception.leaseLost(operation,lease.toString());
        else reception.ackDeferred(operation,lease.toString(),status);
        incidents.add("ACK_HTTP_"+status);
        return false;
    }

    private void fail(FormularioClient.Claim c,String code,String incident,List<String> incidents){
        incidents.add(incident);
        try{int status=client.fail(c.operation(),c.lease(),code);if(status!=204)incidents.add("FALLO_HTTP_"+status);}
        catch(FormularioClient.Failure f){incidents.add("FALLO_"+f.code);}
        LOG.warn("Entrega del Formulario no registrada: {} (intento {}).",incident,c.attempt());
    }
}
