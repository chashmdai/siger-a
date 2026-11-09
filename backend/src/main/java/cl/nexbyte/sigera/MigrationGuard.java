package cl.nexbyte.sigera;

import org.flywaydb.core.api.callback.*;
import org.springframework.stereotype.Component;

@Component
public class MigrationGuard implements Callback {
    @Override public boolean supports(Event event,Context context){return event==Event.BEFORE_MIGRATE;}
    @Override public boolean canHandleInTransaction(Event event,Context context){return true;}
    @Override public String getCallbackName(){return "siger-a-isolated-database";}
    @Override public void handle(Event event,Context context){
        try {
            var c=context.getConnection();String url=c.getMetaData().getURL();
            if(url.startsWith("jdbc:mysql:")){
                String catalog=c.getCatalog();
                if(catalog==null||!catalog.matches("siger_a_[a-z0-9_]{1,40}"))throw new IllegalStateException("Migracion denegada: esquema no exclusivo SIGER A.");
                try(var statement=c.createStatement();var result=statement.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('communication_registry','institutional_inbox','core_case')")){
                    result.next();if(result.getInt(1)>0)throw new IllegalStateException("Migracion denegada: se detectaron tablas SMR/SIGER anterior.");
                }
            }else if(!url.startsWith("jdbc:h2:"))throw new IllegalStateException("Motor no permitido.");
        }catch(java.sql.SQLException e){throw new IllegalStateException("No se pudo verificar el aislamiento de la migracion.",e);}
    }
}
