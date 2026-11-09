package cl.nexbyte.sigera;

import java.util.Map;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiErrors {
    private final Db db;
    public ApiErrors(Db db){this.db=db;}
    @ExceptionHandler(Problem.class) ResponseEntity<?> problem(Problem p){
        if(p.status==403||p.status==404){
            var a=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if(a!=null&&a.getPrincipal() instanceof SecurityConfig.Principal u)try{db.audit(u.id(),"DENEGADO","SISTEMA",0,null,"Operacion fuera de permiso o recurso no disponible.");}catch(Exception ignored){}
        }
        return ResponseEntity.status(p.status).body(Map.of("message",p.getMessage()));}
    @ExceptionHandler({IllegalArgumentException.class,HttpMessageNotReadableException.class}) ResponseEntity<?> invalid(Exception e){return ResponseEntity.badRequest().body(Map.of("message","Solicitud invalida. Revisa los campos enviados."));}
    @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<?> duplicate(Exception e){return ResponseEntity.status(409).body(Map.of("message","La operacion entra en conflicto con un registro existente. Actualiza la ficha."));}
    @ExceptionHandler(MaxUploadSizeExceededException.class) ResponseEntity<?> upload(Exception e){return ResponseEntity.status(413).body(Map.of("message","El archivo supera 25 MiB."));}
    @ExceptionHandler(Exception.class) ResponseEntity<?> other(Exception e){
        String id=java.util.UUID.randomUUID().toString();
        if(Db.readOnly(e)){
            LoggerFactory.getLogger(ApiErrors.class).error("Incidente {}: la base de datos no admite escrituras",id);
            return ResponseEntity.status(503).body(Map.of("message",Db.READ_ONLY_MESSAGE,"incident",id));
        }
        // No registrar SQL, cuerpos HTTP ni datos ciudadanos en el log.
        LoggerFactory.getLogger(ApiErrors.class).error("Incidente {} tipo {}",id,e.getClass().getSimpleName());
        return ResponseEntity.internalServerError().body(Map.of("message","No se pudo confirmar la operacion. Conserva la accion y reintenta con la misma clave.","incident",id));
    }
}
