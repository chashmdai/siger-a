package cl.nexbyte.sigera;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import static cl.nexbyte.sigera.Db.*;

/**
 * Aviso a distribucion de las COM del Formulario (0.7.0, D24, gate T03c). Tarea periodica aparte de la recepcion del
 * Formulario (que no cambia): cada 60 segundos, con el lock global, busca las COM con origen en el Formulario
 * (external_source, la regla de Reports.INTAKE) que siguen por distribuir (NUEVA), que nunca pasaron por una SOL ni por
 * un vinculo como antecedente (una COM ya distribuida y devuelta solo recibe el aviso de rechazo existente) y que aun no
 * tienen este aviso, y avisa una sola vez a quienes pueden distribuirla (Access.notifyDistributors: "Mis avisos" y, con
 * el correo Graph activo, MailOutbox en el mismo COMMIT).
 * <p>
 * La marca de "ya avisado" es el propio aviso: una fila inbox_notice de la COM con uno de los dos textos de D24 (sin
 * migracion). Cambiar TEXT o RISK_TEXT exige revisar esa marca: si no, las COM pendientes se avisarian de nuevo. Si nadie
 * puede distribuir, no se escribe ningun aviso y la COM se vuelve a considerar en el ciclo siguiente. El riesgo usa la
 * regla unica de D23 (BoardQueries.RISK_SQL y riskAffirmative). Los textos no llevan datos de personas ni del relato.
 */
@Component
public class DistributionNotices {
    static final String TEXT="Hay un ingreso del Formulario pendiente de distribucion.";
    static final String RISK_TEXT="Hay un ingreso del Formulario con NNA en riesgo inminente, pendiente de distribucion.";
    static final int BATCH=100;
    private final Db db;
    private final Access access;
    public DistributionNotices(Db db,Access access){this.db=db;this.access=access;}

    /** Un ciclo; devuelve cuantas COM recibieron su aviso (al menos un destinatario). */
    @Scheduled(initialDelay=60000,fixedDelay=60000)
    @Transactional
    public int deliver(){
        db.lock();int delivered=0;
        var pending=db.rows("SELECT c.*,"+BoardQueries.RISK_SQL+" AS risk_value FROM communication c WHERE c.external_source IS NOT NULL AND c.state='NUEVA'"
            +" AND NOT EXISTS(SELECT 1 FROM solicitation s WHERE s.communication_id=c.id)"
            +" AND NOT EXISTS(SELECT 1 FROM communication_link l WHERE l.communication_id=c.id)"
            +" AND NOT EXISTS(SELECT 1 FROM inbox_notice i WHERE i.resource_type='COM' AND i.resource_id=c.id AND i.text_value IN (?,?))"
            +" ORDER BY c.id LIMIT "+BATCH,TEXT,RISK_TEXT);
        for(var com:pending){
            access.notifyDistributors(com,BoardQueries.riskAffirmative(com.get("risk_value"))?RISK_TEXT:TEXT);
            if(db.count("SELECT COUNT(*) FROM inbox_notice WHERE resource_type='COM' AND resource_id=? AND text_value IN (?,?)",n(com,"id"),TEXT,RISK_TEXT)>0)delivered++;
        }
        return delivered;
    }
}
