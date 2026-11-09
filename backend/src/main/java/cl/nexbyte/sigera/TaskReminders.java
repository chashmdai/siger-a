package cl.nexbyte.sigera;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import static cl.nexbyte.sigera.Db.*;

/** Avisos de agenda internos. Un solo recordatorio por tarea y vencimiento, sin email/Graph. */
@Component
public class TaskReminders {
    private final Db db;
    private final Access access;
    public TaskReminders(Db db,Access access){this.db=db;this.access=access;}

    @Scheduled(initialDelay=60000,fixedDelay=60000)
    @Transactional
    public void deliverDue(){
        db.lock();long now=System.currentTimeMillis();
        var due=db.rows("SELECT t.id,t.case_id,t.executor_id,t.reminder_interval_hours FROM task_item t JOIN staff u ON u.id=t.executor_id JOIN case_record c ON c.id=t.case_id WHERE u.enabled=TRUE AND c.state<>'CERRADO' AND t.state IN ('PENDIENTE','EN_CURSO') AND t.next_reminder_ms IS NOT NULL AND t.next_reminder_ms<=? ORDER BY t.next_reminder_ms,t.id LIMIT 100",now);
        for(var t:due){
            long id=n(t,"id"),cas=n(t,"case_id"),user=n(t,"executor_id");
            var actor=access.user(user);
            var c=db.one("SELECT * FROM case_record WHERE id=?",cas);
            if(!access.caseRead(actor,c)){db.update("UPDATE task_item SET next_reminder_ms=? WHERE id=?",now+86400000L,id);continue;}
            access.notice(user,"Recordatorio: tienes una tarea pendiente de este Caso. Revisa tu agenda.","CAS",cas);
            long hours=n(t,"reminder_interval_hours");
            Long next=hours==0?null:now+hours*3600000L;
            db.update("UPDATE task_item SET next_reminder_ms=? WHERE id=?",next,id);
        }
    }
}
