package cl.nexbyte.sigera;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Guardas del motor estadistico (0.8.0, gate E08b), reutilizables por los cortes (E10) y las exportaciones (E11).
 *
 * <ol>
 * <li>Concurrencia ({@link #run}): a lo mas {@code siger.stats.max-concurrent} consultas en total y
 * {@code siger.stats.max-concurrent-per-account} por cuenta. Sin cola: sin cupo se responde 429 de inmediato. Los dos
 * cupos se toman antes de abrir la transaccion (una consulta rechazada no pide conexion) y se liberan al terminar,
 * tambien tras un error o un rollback.</li>
 * <li>Tiempo maximo ({@link #read}): una sola transaccion REPEATABLE READ con {@code siger.stats.timeout-seconds}. El
 * presupuesto es comun a todas sus sentencias (consulta, comparacion y lecturas auxiliares): Spring aplica a cada
 * sentencia lo que queda del plazo ({@code setQueryTimeout}) y rechaza la siguiente si ya vencio. En MySQL el driver
 * cancela la sentencia en el servidor (KILL QUERY); en H2 la cancela el motor. Al vencer: rollback, sin resultados
 * parciales, 503 en texto llano y, ya liberada la conexion, la auditoria STAT_CONSULTA_CANCELADA en otra transaccion
 * (solo nombres validados; si falla, se registra en el log y no oculta el rechazo original).</li>
 * <li>Confinamiento: al crearse, sobre MySQL, comprueba que @@datadir, @@tmpdir (todas sus rutas) y
 * @@innodb_temp_tablespaces_dir esten en la unidad de la instancia (la de siger.data; las rutas relativas se resuelven
 * contra el datadir, que es el directorio de trabajo de mysqld). Si no, o si no se puede comprobar, el motor queda
 * deshabilitado (503) con el aviso tecnico MOTOR_ESTADISTICO_DESHABILITADO en el log, sin afectar la operacion.
 * {@code siger.stats.instance-drive} solo rige en ensayos (perfil test sin el perfil mysql de la instancia).</li>
 * </ol>
 *
 * Las guardas no son una cuota de disco: MySQL 8.4 no limita por sesion el tamano de sus temporales en disco; los
 * contienen el tiempo maximo y la unidad de la instancia.
 */
@Component
public class StatGuard {
    static final String CANCELLED="STAT_CONSULTA_CANCELADA";
    static final String DISABLED_CODE="MOTOR_ESTADISTICO_DESHABILITADO";
    static final String BUSY="Ya hay consultas estadísticas en curso. Espera a que terminen y vuelve a intentarlo.";
    static final String DISABLED="El motor estadístico está deshabilitado por la configuración técnica de la base de datos. Avisa al equipo técnico.";
    static final List<String> VARIABLES=List.of("datadir","tmpdir","innodb_temp_tablespaces_dir");
    private static final Logger LOG=LoggerFactory.getLogger(StatGuard.class);

    private final Db db;
    private final PlatformTransactionManager transactions;
    private final int maxConcurrent,perAccount,timeoutSeconds;
    private final Semaphore total;
    private final ConcurrentHashMap<Long,Integer> running=new ConcurrentHashMap<>();
    /** Variables de MySQL fuera de la unidad de la instancia; vacia = motor habilitado. */
    private volatile List<String> disabled=List.of();

    @Autowired
    public StatGuard(Db db,PlatformTransactionManager transactions,Environment environment,
                     @Value("${siger.stats.max-concurrent:2}")int maxConcurrent,
                     @Value("${siger.stats.max-concurrent-per-account:1}")int perAccount,
                     @Value("${siger.stats.timeout-seconds:30}")int timeoutSeconds,
                     @Value("${siger.stats.instance-drive:}")String instanceDrive,
                     @Value("${siger.data:./var}")String data){
        this(db,transactions,maxConcurrent,perAccount,timeoutSeconds);
        this.disabled=confine(instanceDrive(environment.getActiveProfiles(),instanceDrive,data));
    }

    StatGuard(Db db,PlatformTransactionManager transactions,int maxConcurrent,int perAccount,int timeoutSeconds){
        if(maxConcurrent<1||perAccount<1||perAccount>maxConcurrent)throw new IllegalArgumentException("siger.stats.max-concurrent y max-concurrent-per-account deben ser positivos (por cuenta no mas que el total).");
        if(timeoutSeconds<1)throw new IllegalArgumentException("siger.stats.timeout-seconds debe ser positivo.");
        this.db=db;
        this.transactions=transactions;
        this.maxConcurrent=maxConcurrent;
        this.perAccount=perAccount;
        this.timeoutSeconds=timeoutSeconds;
        this.total=new Semaphore(maxConcurrent);
    }

    // --- 1. Concurrencia ----------------------------------------------------------------------------------------------
    /** Ejecuta el trabajo con un cupo total y uno de la cuenta; sin cupo, 429 de inmediato. */
    public <T> T run(long account,Supplier<T> work){
        if(!total.tryAcquire())throw new Problem(429,BUSY);
        boolean[] taken={false};
        try{
            running.compute(account,(k,v)->{
                int n=v==null?0:v;
                if(n>=perAccount)return v;
                taken[0]=true;
                return n+1;
            });
        }catch(RuntimeException|Error e){
            total.release();
            throw e;
        }
        if(!taken[0]){
            total.release();
            throw new Problem(429,BUSY);
        }
        try{
            return work.get();
        }finally{
            running.compute(account,(k,v)->v==null||v<=1?null:v-1);
            total.release();
        }
    }
    /** Cupos libres en total (pruebas). */
    int available(){return total.availablePermits();}
    /** Consultas en curso por cuenta (pruebas); una cuenta sin consultas no queda en el mapa. */
    Map<Long,Integer> active(){return Map.copyOf(running);}
    int maxConcurrent(){return maxConcurrent;}
    /** Plazo comun de las consultas y del congelado de un corte (siger.stats.timeout-seconds). */
    int timeoutSeconds(){return timeoutSeconds;}

    // --- 2. Tiempo maximo ---------------------------------------------------------------------------------------------
    /**
     * Ejecuta el trabajo en una transaccion REPEATABLE READ con el plazo comun de siger.stats.timeout-seconds. Si vence,
     * revierte, audita la cancelacion con {@code note} (solo nombres validados; nulo = la consulta no llego a validarse)
     * y responde 503.
     */
    public <T> T read(long actor,Supplier<String> note,Supplier<T> work){
        if(TransactionSynchronizationManager.isActualTransactionActive())return within(actor,note,work);
        var t=new TransactionTemplate(transactions);
        t.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        t.setTimeout(timeoutSeconds);
        try{
            return t.execute(status->work.get());
        }catch(RuntimeException e){
            if(!timedOut(e))throw e;
            // TransactionTemplate ya revirtio y devolvio la conexion al pool.
            cancelled(actor,note);
            throw new Problem(503,timeoutMessage());
        }
    }
    /**
     * Dentro de una transaccion ya abierta (STAT_CUT_CREATE, E10, con el lock global de Workflow.run): el mismo plazo, como
     * fecha limite de la conexion de esa transaccion (Spring aplica a cada sentencia lo que queda y rechaza la siguiente si
     * vencio). Al vencer: 503 sin resultados parciales (el comando revierte todo) y la auditoria de la cancelacion despues
     * del rollback, en otra transaccion. Al terminar, el resto del comando ya no queda acotado por este plazo.
     */
    private <T> T within(long actor,Supplier<String> note,Supplier<T> work){
        var holder=(ConnectionHolder)TransactionSynchronizationManager.getResource(db.dataSource());
        if(holder==null)throw new IllegalStateException("Transaccion sin conexion asociada.");
        holder.setTimeoutInSeconds(timeoutSeconds);
        try{
            return work.get();
        }catch(RuntimeException e){
            if(!timedOut(e))throw e;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
                @Override public void afterCompletion(int status){cancelled(actor,note);}
            });
            throw new Problem(503,timeoutMessage());
        }finally{
            holder.setTimeoutInMillis(LIFTED_MS);
        }
    }
    /** Fecha limite que deja el plazo propio sin efecto en lo que sigue de la transaccion (un dia). */
    private static final long LIFTED_MS=86_400_000L;
    String timeoutMessage(){
        return "La consulta superó el tiempo máximo de "+timeoutSeconds+" s y se canceló, sin resultados parciales. Agrega un filtro, quita una dimensión o acorta el periodo.";
    }
    private void cancelled(long actor,Supplier<String> note){
        try{
            String names=note==null?null:note.get();
            String text=(names==null||names.isBlank()?"Consulta cancelada antes de validarse":names)+"; cancelada por tiempo ("+timeoutSeconds+" s)";
            // Siempre en una transaccion nueva: tambien despues del rollback de un comando (afterCompletion).
            var t=new TransactionTemplate(transactions);
            t.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            t.executeWithoutResult(s->db.audit(actor,CANCELLED,"SISTEMA",0,null,text));
        }catch(RuntimeException e){
            LOG.error("Auditoria {} no registrada ({}).",CANCELLED,e.getClass().getSimpleName());
        }
    }
    /** Plazo vencido o sentencia cancelada por tiempo (Spring, JDBC, H2 57014, MySQL 3024 y 1317). */
    static boolean timedOut(Throwable error){
        for(Throwable t=error;t!=null;t=t.getCause()){
            if(t instanceof TransactionTimedOutException||t instanceof QueryTimeoutException||t instanceof java.sql.SQLTimeoutException)return true;
            if(t instanceof SQLException e&&("57014".equals(e.getSQLState())||e.getErrorCode()==3024||e.getErrorCode()==1317))return true;
            if(t.getCause()==t)break;
        }
        return false;
    }

    // --- 3. Confinamiento ---------------------------------------------------------------------------------------------
    public boolean enabled(){return disabled.isEmpty();}
    public void requireEnabled(){if(!disabled.isEmpty())throw new Problem(503,DISABLED);}
    /** Variables fuera de la unidad de la instancia (vacia = habilitado). */
    List<String> disabledBy(){return disabled;}
    /** Solo pruebas: fija el estado del confinamiento. */
    void confined(List<String> variables){disabled=List.copyOf(variables);}

    /**
     * Unidad de la instancia: la raiz de siger.data. El ajuste siger.stats.instance-drive rige solo en ensayos (perfil
     * test sin el perfil mysql, que usa toda instancia de la VM); en otro caso se ignora con un aviso.
     */
    static String instanceDrive(String[] profiles,String override,String data){
        var active=Arrays.asList(profiles);
        if(override!=null&&!override.isBlank()){
            if(active.contains("test")&&!active.contains("mysql"))return override.strip();
            LOG.warn("UNIDAD_DE_INSTANCIA_IGNORADA: siger.stats.instance-drive solo rige en ensayos; se usa la unidad de siger.data.");
        }
        var root=Path.of(data==null||data.isBlank()?".":data).toAbsolutePath().getRoot();
        return root==null?null:root.toString();
    }
    private List<String> confine(String drive){
        try(var c=db.dataSource().getConnection()){
            if(!c.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("mysql"))return List.of();
            String datadir,tmpdir,innodb;
            try(var st=c.createStatement();var rs=st.executeQuery("SELECT @@datadir,@@tmpdir,@@innodb_temp_tablespaces_dir")){
                rs.next();
                datadir=rs.getString(1);tmpdir=rs.getString(2);innodb=rs.getString(3);
            }
            var bad=misplaced(drive,datadir,tmpdir,innodb);
            if(!bad.isEmpty())LOG.warn("{}: {} fuera de la unidad de la instancia. El motor estadistico queda deshabilitado; la operacion sigue.",DISABLED_CODE,String.join(", ",bad));
            return bad;
        }catch(SQLException|RuntimeException e){
            LOG.warn("{}: no se pudo comprobar la ubicacion de los archivos de MySQL ({}). El motor estadistico queda deshabilitado; la operacion sigue.",DISABLED_CODE,e.getClass().getSimpleName());
            return VARIABLES;
        }
    }

    /**
     * Regla pura: nombres de las variables cuyas rutas no estan en la unidad indicada. Sin distinguir mayusculas ni
     * separadores; una ruta relativa se resuelve contra el datadir; tmpdir puede traer varias rutas (";" en Windows, ":"
     * en otros sistemas) y basta una fuera; una ruta vacia o una unidad desconocida no se aceptan.
     */
    static List<String> misplaced(String drive,String datadir,String tmpdir,String innodbTemp){
        String want=root(drive);
        if(want==null)return VARIABLES;
        String data=root(datadir);
        var out=new ArrayList<String>();
        if(!want.equals(data))out.add("datadir");
        boolean windows=datadir!=null&&WINDOWS.matcher(datadir.strip()).find();
        var tmps=tmpdir==null?List.<String>of():Arrays.stream(tmpdir.split(windows?";":":")).map(String::strip).filter(s->!s.isEmpty()).toList();
        if(tmps.isEmpty()||tmps.stream().anyMatch(t->!want.equals(resolved(t,data))))out.add("tmpdir");
        if(!want.equals(resolved(innodbTemp,data)))out.add("innodb_temp_tablespaces_dir");
        return List.copyOf(out);
    }
    private static final java.util.regex.Pattern WINDOWS=java.util.regex.Pattern.compile("^([A-Za-z]:|\\\\\\\\|//)");
    private static String resolved(String path,String dataRoot){
        if(path==null||path.isBlank())return null;
        String r=root(path);
        return r!=null?r:dataRoot;
    }
    /** Raiz de una ruta: "D:" (mayuscula), "\\servidor\recurso" (minusculas) o "/"; nulo si es relativa o vacia. */
    static String root(String path){
        if(path==null)return null;
        String s=path.strip().replace('/','\\');
        if(s.isEmpty())return null;
        if(s.length()>=2&&Character.isLetter(s.charAt(0))&&s.charAt(1)==':'&&s.charAt(0)<128)return Character.toUpperCase(s.charAt(0))+":";
        if(s.startsWith("\\\\")){
            var parts=s.substring(2).split("\\\\");
            if(parts.length<2||parts[0].isEmpty()||parts[1].isEmpty())return null;
            return ("\\\\"+parts[0]+"\\"+parts[1]).toLowerCase(Locale.ROOT);
        }
        if(path.strip().startsWith("/"))return "/";
        return null;
    }
}
