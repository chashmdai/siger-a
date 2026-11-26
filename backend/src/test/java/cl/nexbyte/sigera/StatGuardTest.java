package cl.nexbyte.sigera;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

import static cl.nexbyte.sigera.Db.*;
import static cl.nexbyte.sigera.ImmutabilityTest.map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Guardas del motor estadistico (0.8.0, gate E08b): concurrencia sin cola (total y por cuenta, contador sin carreras,
 * liberacion ante error y rollback, sin pedir conexion al rechazar), operacion de expedientes con los cupos ocupados,
 * tiempo maximo comun a las sentencias de una consulta con cancelacion efectiva (tambien en MySQL) y conexion
 * reutilizable, auditoria de la cancelacion y confinamiento de los archivos de MySQL en la unidad de la instancia.
 * Corre en H2 y, con siger.mysql.suite=true, en MySQL 8.4.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_guarda;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.seed-demo=true","siger.data=./target/test-guarda-${random.uuid}","siger.scheduling=false"})
@ActiveProfiles("test")
public class StatGuardTest extends StatFixture {
    static final String REP="rep.guarda";
    static final boolean MYSQL=MySqlSuite.enabled();
    static final String MARK="guarda-lenta";
    static final String QUERY_NOTE="Medida INGRESOS; modo VIGENTE";
    @Autowired PlatformTransactionManager transactions;

    /** Funcion lenta de H2 (espera activa por fila, en microsegundos): H2 revisa la cancelacion cada 128 filas. */
    public static int slow(int micros){long end=System.nanoTime()+micros*1000L;while(System.nanoTime()<end)Thread.onSpinWait();return 0;}

    @BeforeEach void base(){
        staff(REP,"REPORTES");
        if(!MYSQL)db.update("CREATE ALIAS IF NOT EXISTS STAT_LENTO FOR 'cl.nexbyte.sigera.StatGuardTest.slow'");
    }
    /** Sentencia que tarda unos {@code seconds} segundos y se puede cancelar mientras corre (MySQL: 100 filas; H2: 2.500). */
    static String slowSql(double seconds){
        return MYSQL?"WITH RECURSIVE r(n) AS (SELECT 1 UNION ALL SELECT n+1 FROM r WHERE n<100) SELECT /* "+MARK+" */ SUM(SLEEP("+(seconds/100)+")) AS s FROM r"
            :"SELECT /* "+MARK+" */ SUM(STAT_LENTO("+Math.round(seconds*1_000_000/2500)+")) AS s FROM SYSTEM_RANGE(1,2500)";
    }
    StatGuard guardOf(int seconds){return new StatGuard(db,transactions,2,1,seconds);}
    long cancelled(){return db.count("SELECT COUNT(*) FROM audit_event WHERE action=?",StatGuard.CANCELLED);}
    long consulted(){return db.count("SELECT COUNT(*) FROM audit_event WHERE action='STAT_CONSULTA'");}
    static Map<String,Object> ingresos(){return map("measure","INGRESOS","rows",List.of("CANAL"),"period",map("type","CUENTA_PUBLICA","from","2025-07-01"));}

    /** Una consulta que ocupa su cupo hasta release(); con read=true, ademas una transaccion con su conexion. */
    final class Hold implements AutoCloseable {
        final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        final Thread thread;final AtomicReference<Throwable> failure=new AtomicReference<>();
        Hold(StatGuard g,long account,boolean read)throws InterruptedException{
            Supplier<Object> wait=()->{
                if(read)db.count("SELECT COUNT(*) FROM communication");
                entered.countDown();
                try{release.await(60,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
                return null;
            };
            thread=new Thread(()->{
                try{g.run(account,()->read?g.read(account,()->QUERY_NOTE,wait):wait.get());}catch(Throwable t){failure.set(t);entered.countDown();}
            });
            thread.start();
            assertTrue(entered.await(30,TimeUnit.SECONDS));
            assertNull(failure.get());
        }
        @Override public void close()throws InterruptedException{release.countDown();thread.join(30_000);assertFalse(thread.isAlive());}
    }
    static void busy(Executable call){
        long t0=System.nanoTime();
        var p=assertThrows(Problem.class,call::run);
        assertEquals(429,p.status);assertEquals(StatGuard.BUSY,p.getMessage());
        assertTrue(System.nanoTime()-t0<1_000_000_000L,"rechazo inmediato, sin cola");
    }
    interface Executable{void run()throws Exception;}

    // --- 1. Concurrencia ----------------------------------------------------------------------------------------------
    @Test void concurrenciaTotalYPorCuenta()throws Exception{
        var g=guardOf(30);
        try(var one=new Hold(g,1,false)){
            busy(()->g.run(1,()->1));
            try(var two=new Hold(g,2,false)){
                assertEquals(0,g.available());
                busy(()->g.run(3,()->1));
                busy(()->g.run(2,()->1));
                assertEquals(Map.of(1L,1,2L,1),g.active());
            }
            assertEquals(3,g.run(3,()->3));
        }
        assertEquals(2,g.available());assertTrue(g.active().isEmpty());
    }
    @Test void contadorAtomicoSinCarreras()throws Exception{
        var g=guardOf(30);
        var total=new AtomicInteger();var maxTotal=new AtomicInteger();
        var per=new ConcurrentHashMap<Long,AtomicInteger>();var maxPer=new AtomicInteger();
        var accepted=new AtomicInteger();var rejected=new AtomicInteger();
        var pool=Executors.newFixedThreadPool(16);
        try{
            var futures=new ArrayList<Future<?>>();
            for(int t=0;t<16;t++){
                final int th=t;
                futures.add(pool.submit(()->{
                    for(int i=0;i<300;i++){
                        long account=(th+i)%3+1;final boolean fail=i%7==0;
                        try{
                            g.run(account,()->{
                                maxTotal.accumulateAndGet(total.incrementAndGet(),Math::max);
                                maxPer.accumulateAndGet(per.computeIfAbsent(account,k->new AtomicInteger()).incrementAndGet(),Math::max);
                                java.util.concurrent.locks.LockSupport.parkNanos(50_000);
                                per.get(account).decrementAndGet();total.decrementAndGet();
                                if(fail)throw new IllegalStateException("falla sintetica");
                                return null;
                            });
                            accepted.incrementAndGet();
                        }catch(Problem p){assertEquals(429,p.status);rejected.incrementAndGet();}
                        catch(IllegalStateException e){accepted.incrementAndGet();}
                    }
                }));
            }
            for(var f:futures)f.get(120,TimeUnit.SECONDS);
        }finally{pool.shutdownNow();}
        assertTrue(maxTotal.get()<=2,"total "+maxTotal);assertTrue(maxPer.get()<=1,"por cuenta "+maxPer);
        assertTrue(accepted.get()>0&&rejected.get()>0,accepted+" / "+rejected);
        assertEquals(2,g.available());assertTrue(g.active().isEmpty(),()->"contador limpio: "+g.active());
    }
    @Test void liberacionAnteErrorYRollback(){
        var g=guardOf(30);
        long cancelledBefore=cancelled();
        assertThrows(IllegalStateException.class,()->g.run(1,()->{throw new IllegalStateException("x");}));
        assertEquals(2,g.available());assertTrue(g.active().isEmpty());
        long before=db.count("SELECT COUNT(*) FROM audit_event WHERE action='PRUEBA_GUARDA'");
        var p=assertThrows(Problem.class,()->g.run(1,()->g.read(1,()->QUERY_NOTE,()->{
            db.audit(user(REP),"PRUEBA_GUARDA","SISTEMA",0,null,"");
            throw new Problem(409,"rechazo sintetico");
        })));
        assertEquals(409,p.status);
        assertEquals(before,db.count("SELECT COUNT(*) FROM audit_event WHERE action='PRUEBA_GUARDA'"),"rollback");
        assertEquals(2,g.available());assertTrue(g.active().isEmpty());
        assertEquals(cancelledBefore,cancelled(),"un rechazo que no es de tiempo no se audita como cancelacion");
    }
    @Test void rechazadasNoPidenConexion()throws Exception{
        var hikari=(HikariDataSource)db.dataSource();
        var actor=access.actor(REP);
        var held=new ArrayList<Connection>();
        try(var a=new Hold(guard,user(REP),true);var b=new Hold(guard,user(INST_GUARD()),true)){
            for(int i=2;i<hikari.getMaximumPoolSize();i++)held.add(hikari.getConnection());
            assertEquals(hikari.getMaximumPoolSize(),hikari.getHikariPoolMXBean().getActiveConnections(),"pool agotado");
            long t0=System.nanoTime();
            for(int i=0;i<3;i++){
                var p=assertThrows(Problem.class,()->engine.query(actor,ingresos()));
                assertEquals(429,p.status);
            }
            assertTrue(System.nanoTime()-t0<2_000_000_000L,"sin esperar una conexion (connection-timeout 10 s)");
            assertEquals(hikari.getMaximumPoolSize(),hikari.getHikariPoolMXBean().getActiveConnections());
        }finally{
            for(var c:held)c.close();
        }
        long deadline=System.currentTimeMillis()+10_000;
        while(hikari.getHikariPoolMXBean().getActiveConnections()>0&&System.currentTimeMillis()<deadline)Thread.sleep(20);
        assertEquals(0,hikari.getHikariPoolMXBean().getActiveConnections(),"ninguna conexion retenida");
        assertNotNull(engine.query(actor,ingresos()).get("cells"));
    }
    String INST_GUARD(){return staff("rep.guarda.dos","REPORTES");}
    @Test void comandoDeExpedienteConLosCuposOcupados()throws Exception{
        long before=db.count("SELECT COUNT(*) FROM communication");
        try(var a=new Hold(guard,user(REP),true);var b=new Hold(guard,user(INST_GUARD()),true)){
            assertEquals(0,guard.available());
            long com=assertTimeoutPreemptively(java.time.Duration.ofSeconds(20),()->manual(Map.of()));
            assertEquals(0,guard.available(),"el comando termino antes de liberar los cupos");
            assertEquals("NUEVA",s(db.one("SELECT state FROM communication WHERE id=?",com),"state"));
            assertEquals(before+1,db.count("SELECT COUNT(*) FROM communication"));
        }
        assertEquals(guard.maxConcurrent(),guard.available());
    }

    // --- 2. Tiempo maximo ---------------------------------------------------------------------------------------------
    @Test void tiempoAgotadoCancelaYAuditaSoloNombres(){
        var g=guardOf(1);
        long before=cancelled(),queries=consulted();
        long t0=System.nanoTime();
        var p=assertThrows(Problem.class,()->g.run(1,()->g.read(user(REP),()->QUERY_NOTE,()->db.rows(slowSql(5)))));
        long ms=(System.nanoTime()-t0)/1_000_000;
        assertEquals(503,p.status);assertEquals(g.timeoutMessage(),p.getMessage());
        assertTrue(p.getMessage().contains("1 s")&&!p.getMessage().contains("SELECT"),p.getMessage());
        assertTrue(ms<3_500,"cancelada antes de terminar (5 s sin cancelar): "+ms+" ms");
        assertEquals(before+1,cancelled());assertEquals(queries,consulted());
        var note=db.one("SELECT actor_id,note FROM audit_event WHERE action=? ORDER BY id DESC LIMIT 1",StatGuard.CANCELLED);
        assertEquals(QUERY_NOTE+"; cancelada por tiempo (1 s)",s(note,"note"));
        assertEquals(user(REP),n(note,"actor_id"));
        assertEquals(2,g.available());assertTrue(g.active().isEmpty());
        // Sin validar la consulta: texto fijo, nunca contenido de la peticion.
        assertThrows(Problem.class,()->g.read(user(REP),()->null,()->db.rows(slowSql(5))));
        assertEquals("Consulta cancelada antes de validarse; cancelada por tiempo (1 s)",s(db.one("SELECT note FROM audit_event WHERE action=? ORDER BY id DESC LIMIT 1",StatGuard.CANCELLED),"note"));
    }
    @Test void presupuestoComunEntreSentenciasSinResultadosParciales(){
        var g=guardOf(2);
        var durations=new ArrayList<Long>();
        long t0=System.nanoTime();
        var p=assertThrows(Problem.class,()->g.read(user(REP),()->QUERY_NOTE,()->{
            try{assertEquals(Connection.TRANSACTION_REPEATABLE_READ,DataSourceUtils.getConnection(db.dataSource()).getTransactionIsolation());}
            catch(java.sql.SQLException e){throw new IllegalStateException(e);}
            db.audit(user(REP),"PRUEBA_PARCIAL","SISTEMA",0,null,"");
            for(int i=0;i<12;i++){
                long s=System.nanoTime();
                db.rows(slowSql(0.6));
                durations.add((System.nanoTime()-s)/1_000_000);
            }
            return "completo";
        }));
        long ms=(System.nanoTime()-t0)/1_000_000;
        assertEquals(503,p.status);
        assertTrue(durations.size()>=2,"vencio por la suma de varias sentencias: "+durations);
        assertTrue(durations.stream().allMatch(d->d<2_000),"cada sentencia por si sola cabe en el plazo: "+durations);
        assertTrue(ms<4_000,"plazo comun de 2 s: "+ms+" ms");
        assertEquals(0,db.count("SELECT COUNT(*) FROM audit_event WHERE action='PRUEBA_PARCIAL'"),"sin escrituras ni resultados parciales");
    }
    @Test void cancelacionEfectivaYConexionReutilizable()throws Exception{
        var g=guardOf(1);
        var hikari=(HikariDataSource)db.dataSource();
        String idSql=MYSQL?"SELECT CONNECTION_ID() AS id":"SELECT SESSION_ID() AS id";
        var id=new AtomicLong();
        long t0=System.nanoTime();
        assertThrows(Problem.class,()->g.read(user(REP),()->QUERY_NOTE,()->{id.set(n(db.one(idSql),"id"));return db.rows(slowSql(5));}));
        assertTrue((System.nanoTime()-t0)/1_000_000<3_500,"cancelada en el servidor");
        if(MYSQL)assertEquals(0,db.count("SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE INFO LIKE ? AND ID<>CONNECTION_ID()","%"+MARK+"%"),"la sentencia no sigue en MySQL");
        int defaultIsolation;
        try(var c=hikari.getConnection()){defaultIsolation=c.getTransactionIsolation();}
        var held=new ArrayList<Connection>();
        try{
            Connection same=null;
            for(int i=0;i<hikari.getMaximumPoolSize()&&same==null;i++){
                var c=hikari.getConnection();held.add(c);
                try(var st=c.createStatement();var rs=st.executeQuery(idSql)){rs.next();if(rs.getLong(1)==id.get())same=c;}
            }
            assertNotNull(same,"la conexion de la consulta cancelada volvio al pool");
            assertTrue(same.getAutoCommit());assertEquals(defaultIsolation,same.getTransactionIsolation());
            try(var st=same.createStatement();var rs=st.executeQuery("SELECT COUNT(*) FROM communication")){assertTrue(rs.next());}
        }finally{for(var c:held)c.close();}
        assertNotNull(g.run(1,()->g.read(user(REP),()->QUERY_NOTE,()->db.rows("SELECT COUNT(*) AS n FROM communication"))));
    }
    @Test void elFalloDeLaAuditoriaNoOcultaElRechazo(){
        var g=guardOf(1);
        Supplier<String> failing=()->{throw new IllegalStateException("nombres no disponibles");};
        long before=cancelled();
        var p=assertThrows(Problem.class,()->g.read(user(REP),failing,()->db.rows(slowSql(5))));
        assertEquals(503,p.status);assertEquals(g.timeoutMessage(),p.getMessage());
        assertEquals(before,cancelled());
    }

    // --- 3. Confinamiento ---------------------------------------------------------------------------------------------
    @Test void confinamientoDeRutas(){
        var all=StatGuard.VARIABLES;
        assertEquals(List.of(),StatGuard.misplaced("D:\\","D:\\SIGER-A\\mysql\\data\\","D:/SIGER-A/mysql/tmp","#innodb_temp/"));
        assertEquals(List.of(),StatGuard.misplaced("d:","d:/x/data/","D:\\x\\tmp","D:\\x\\data\\#innodb_temp\\"),"sin distinguir mayusculas");
        assertEquals(List.of("datadir","innodb_temp_tablespaces_dir"),StatGuard.misplaced("D:\\","C:\\x\\data\\","D:\\tmp","#innodb_temp\\"),"relativa contra el datadir");
        assertEquals(List.of("innodb_temp_tablespaces_dir"),StatGuard.misplaced("D:\\","D:\\data\\","D:\\tmp","C:\\temp\\"));
        assertEquals(List.of("tmpdir"),StatGuard.misplaced("D:\\","D:\\data\\","D:\\a;C:\\b","#innodb_temp/"),"basta una ruta fuera");
        assertEquals(List.of(),StatGuard.misplaced("D:\\","D:\\data\\","D:\\a; d:\\b ","#innodb_temp/"));
        assertEquals(List.of("tmpdir"),StatGuard.misplaced("D:\\","D:\\data\\","","#innodb_temp/"),"sin tmpdir no se acepta");
        assertEquals(List.of(),StatGuard.misplaced("D:\\","D:\\data\\","tmp","#innodb_temp/"),"tmpdir relativo al datadir");
        assertEquals(all,StatGuard.misplaced("D:\\","\\\\servidor\\recurso\\data","\\\\servidor\\recurso\\tmp","#innodb_temp/"));
        assertEquals(all,StatGuard.misplaced(null,"D:\\data\\","D:\\tmp","#innodb_temp/"),"unidad desconocida");
        assertEquals(all,StatGuard.misplaced("D:\\",null,null,null));
        assertEquals(List.of(),StatGuard.misplaced("/","/var/lib/mysql/","/tmp:/var/tmp","./#innodb_temp/"));
        assertEquals("D:",StatGuard.root("d:/x"));assertEquals("\\\\srv\\rec",StatGuard.root("\\\\SRV\\Rec\\x"));assertNull(StatGuard.root("#innodb_temp/"));
    }
    @Test void unidadDeLaInstanciaSoloAjustableEnEnsayos(){
        String data="./target/datos";
        String own=java.nio.file.Path.of(data).toAbsolutePath().getRoot().toString();
        assertEquals("Z:\\",StatGuard.instanceDrive(new String[]{"test"},"Z:\\",data));
        assertEquals(own,StatGuard.instanceDrive(new String[]{"mysql"},"Z:\\",data),"instancia de la VM: se ignora");
        assertEquals(own,StatGuard.instanceDrive(new String[]{"test","mysql"},"Z:\\",data));
        assertEquals(own,StatGuard.instanceDrive(new String[]{},"Z:\\",data));
        assertEquals(own,StatGuard.instanceDrive(new String[]{"local"},"Z:\\",data));
        assertEquals(own,StatGuard.instanceDrive(new String[]{"test"},"",data));
    }
    @Test void confinamientoAlArrancar(){
        assertTrue(guard.enabled(),"esta prueba corre con los archivos de MySQL en la unidad de la instancia");
        var test=new MockEnvironment();test.setActiveProfiles("test");
        var other=new StatGuard(db,transactions,test,2,1,30,"Q:\\","./target/x");
        var vm=new MockEnvironment();vm.setActiveProfiles("mysql");
        var institutional=new StatGuard(db,transactions,vm,2,1,30,"Q:\\","Q:\\SIGER-A\\data");
        if(MYSQL){
            var v=db.one("SELECT @@datadir AS d,@@tmpdir AS t,@@innodb_temp_tablespaces_dir AS i");
            assertEquals(List.of(),StatGuard.misplaced(StatGuard.root(s(v,"d")),s(v,"d"),s(v,"t"),s(v,"i")));
            assertFalse(other.enabled());assertEquals(StatGuard.VARIABLES,other.disabledBy());
            assertFalse(institutional.enabled(),"en la VM manda la unidad de siger.data, no el ajuste");
            var p=assertThrows(Problem.class,other::requireEnabled);
            assertEquals(503,p.status);assertEquals(StatGuard.DISABLED,p.getMessage());
        }else{
            assertTrue(other.enabled(),"H2 no tiene archivos de MySQL");assertTrue(institutional.enabled());
        }
    }
}
