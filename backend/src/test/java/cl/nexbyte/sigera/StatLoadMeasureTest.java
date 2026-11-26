package cl.nexbyte.sigera;

import com.zaxxer.hikari.HikariDataSource;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static cl.nexbyte.sigera.Db.*;
import static cl.nexbyte.sigera.ImmutabilityTest.map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Medicion del motor estadistico con 50.005 COM sinteticas (0.8.0, gate E08b; reutilizable en E14). Desactivada por
 * defecto: solo corre con -Dsiger.stats.measure=true y la suite sobre un MySQL 8.4 aislado y desechable
 * (siger.mysql.suite=true). La poblacion es determinista (aritmetica sobre el numero de la COM; sin azar ni reloj) y se
 * inserta por SQL en su propio esquema: COM, SOL, REQ, Casos, hitos, comandos, clasificaciones, derechos, contexto,
 * caracterizacion, versiones declaradas con personas y constancias tipificadas.
 *
 * Mide cada consulta pesada (tiempo, temporales en disco, mayor tamano de los temporales de sesion de InnoDB y de tmpdir,
 * espacio libre de la unidad de los temporales) y calibra max_join_size (menor potencia de 10 con que la consulta pasa).
 * Seguridad del equipo: no empieza con menos de {@link #START_FREE} bytes libres en la unidad de tmpdir y, si durante una
 * consulta quedan menos de {@link #STOP_FREE}, la cancela (KILL QUERY) y falla. No cambia internal_tmp_mem_storage_engine.
 * Resultado en target/medicion-e08b.txt.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_medicion;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.seed-demo=true","siger.data=./target/test-medicion-${random.uuid}","siger.scheduling=false","siger.stats.timeout-seconds=${siger.stats.measure-timeout:600}"})
@ActiveProfiles("test")
@EnabledIfSystemProperty(named="siger.stats.measure",matches="true")
public class StatLoadMeasureTest extends StatFixture {
    static final long TOTAL=50_005;
    static final long START_FREE=20L<<30,STOP_FREE=15L<<30;
    static final long FROM=java.time.OffsetDateTime.parse("2024-07-01T04:00:00Z").toInstant().toEpochMilli();
    static final long SPAN=java.time.OffsetDateTime.parse("2026-07-01T04:00:00Z").toInstant().toEpochMilli()-FROM;
    static final String REP="rep.medicion";
    static final String[] CHANNELS={"FORMULARIO_WEB_PUBLICO","CORREO","PRESENCIAL","TELEFONO","OFICIO","REDES_SOCIALES"};
    static final String[] COMPLEXITY={"BAJA","MEDIANA","ALTA"};
    static final String[] MATTERS={"UPR","UPD","UEP"};

    record Consulta(String name,Map<String,Object> body){}
    static Map<String,Object> cp(){return map("type","CUENTA_PUBLICA","from","2025-07-01");}
    static List<Consulta> consultas(){
        return List.of(
            new Consulta("ingresos por canal con periodo anterior",map("measure","INGRESOS","rows",List.of("CANAL"),"period",cp(),"compare",map("type","PERIODO_ANTERIOR"))),
            new Consulta("serie mensual",map("measure","INGRESOS","rows",List.of("PERIODO_MES"),"period",cp())),
            new Consulta("solicitud por requirente",map("measure","INGRESOS","rows",List.of("TIPO_SOLICITUD"),"columns",List.of("TIPO_REQUIRENTE"),"period",cp())),
            new Consulta("region del requirente al REQ",map("measure","INGRESOS","rows",List.of("REGION_REQUIRENTE"),"period",cp(),"time_mode","AL_REQ")),
            new Consulta("tramo de edad del NNA",map("measure","INGRESOS","rows",List.of("TRAMO_EDAD_NNA"),"period",cp())),
            new Consulta("region del NNA por sede vigente",map("measure","INGRESOS","rows",List.of("REGION_NNA"),"columns",List.of("SEDE_VIGENTE"),"period",cp())),
            new Consulta("derecho por contexto",map("measure","REQUERIMIENTOS_CON_VALOR","rows",List.of("DERECHO"),"columns",List.of("CONTEXTO"),"period",cp())),
            new Consulta("actuaciones por tipo",map("measure","ACTUACIONES","rows",List.of("TIPO_ACTUACION"),"period",cp())),
            new Consulta("completitud",map("measure","COMPLETITUD","rows",List.of("VARIABLE"),"columns",List.of("ESTADO_VARIABLE"),"period",cp())),
            new Consulta("solicitud a una fecha",map("measure","INGRESOS","rows",List.of("TIPO_SOLICITUD"),"period",cp(),"time_mode","FECHA","as_of","2026-03-31T23:59:59-03:00")));
    }

    Connection raw()throws Exception{return new Connection(DriverManager.getConnection(System.getProperty("siger.mysql.base")+"/?sslMode=REQUIRED",System.getProperty("siger.mysql.user"),System.getProperty("siger.mysql.password")));}
    record Connection(java.sql.Connection c) implements AutoCloseable{
        long number(String sql)throws Exception{try(var st=c.createStatement();var rs=st.executeQuery(sql)){return rs.next()?rs.getLong(1):0;}}
        String text(String sql)throws Exception{try(var st=c.createStatement();var rs=st.executeQuery(sql)){return rs.next()?rs.getString(1):"";}}
        void exec(String sql)throws Exception{try(var st=c.createStatement()){st.execute(sql);}}
        @Override public void close()throws Exception{c.close();}
    }

    @Test void medicion()throws Exception{
        Assumptions.assumeTrue(MySqlSuite.enabled(),"solo sobre MySQL 8.4 aislado");
        String schema=MySqlSuite.schema("sigera_medicion");
        try(var mon=raw()){
            String tmpdir=mon.text("SELECT @@tmpdir"),datadir=mon.text("SELECT @@datadir");
            assertEquals(List.of(),StatGuard.misplaced(StatGuard.root(datadir),datadir,tmpdir,mon.text("SELECT @@innodb_temp_tablespaces_dir")));
            assertEquals("TempTable",mon.text("SELECT @@internal_tmp_mem_storage_engine"),"sin forzar MEMORY");
            File drive=new File(StatGuard.root(tmpdir.split(";")[0])+"\\");
            assertTrue(drive.getUsableSpace()>=START_FREE,"espacio libre insuficiente en "+drive+" para medir: "+(drive.getUsableSpace()>>30)+" GiB");
            var out=new StringBuilder();
            out.append("Medicion E08b - MySQL ").append(mon.text("SELECT VERSION()")).append(", esquema ").append(schema)
                .append(", tmp_table_size ").append(mon.text("SELECT @@tmp_table_size")).append(", temptable_max_ram ").append(mon.text("SELECT @@temptable_max_ram"))
                .append(", max_join_size ").append(mon.text("SELECT @@max_join_size")).append('\n');
            long t0=System.currentTimeMillis();
            generate();
            out.append("Generacion: ").append(System.currentTimeMillis()-t0).append(" ms; ");
            for(String t:List.of("communication","solicitation","requirement","case_record","stat_event","stat_declared_version","stat_declared_person","management_note","milestone","command_log"))
                out.append(t).append('=').append(db.count("SELECT COUNT(*) FROM "+t)).append(' ');
            out.append('\n');
            assertEquals(TOTAL,db.count("SELECT COUNT(*) FROM communication"));
            mon.exec("ANALYZE TABLE `"+schema+"`.communication,`"+schema+"`.requirement,`"+schema+"`.case_record,`"+schema+"`.stat_event,`"+schema+"`.stat_declared_version,`"+schema+"`.stat_declared_person,`"+schema+"`.milestone,`"+schema+"`.command_log,`"+schema+"`.management_note");
            var actor=access.actor(REP);
            out.append("consulta;ms;celdas;tmp_disco;tmp_memoria;archivos_tmp;innodb_temp_max_MiB;tmpdir_max_MiB;libre_min_GiB;max_join_size_minimo\n");
            for(var q:consultas()){
                var before=status(mon);
                var peak=new AtomicLong();var peakTmp=new AtomicLong();var minFree=new AtomicLong(Long.MAX_VALUE);var stop=new AtomicBoolean();var killed=new AtomicBoolean();
                var watcher=new Thread(()->{
                    try(var w=raw()){
                        while(!stop.get()){
                            peak.accumulateAndGet(w.number("SELECT COALESCE(SUM(SIZE),0) FROM information_schema.INNODB_SESSION_TEMP_TABLESPACES"),Math::max);
                            peakTmp.accumulateAndGet(size(tmpdir),Math::max);
                            long free=drive.getUsableSpace();minFree.accumulateAndGet(free,Math::min);
                            if(free<STOP_FREE&&!killed.getAndSet(true))
                                for(long id:ids(w,schema))w.exec("KILL QUERY "+id);
                            Thread.sleep(200);
                        }
                    }catch(Exception e){throw new IllegalStateException(e);}
                });
                watcher.start();
                long s=System.currentTimeMillis();
                Map<String,Object> r;
                try{r=engine.query(actor,q.body());}finally{stop.set(true);watcher.join();}
                long ms=System.currentTimeMillis()-s;
                assertFalse(killed.get(),"consulta cancelada por espacio libre bajo: "+q.name());
                var after=status(mon);
                out.append(q.name()).append(';').append(ms).append(';').append(((List<?>)r.get("cells")).size()).append(';')
                    .append(after[0]-before[0]).append(';').append(after[1]-before[1]).append(';').append(after[2]-before[2]).append(';')
                    .append(peak.get()>>20).append(';').append(peakTmp.get()>>20).append(';').append(minFree.get()>>30).append(';')
                    .append(minimumJoinSize(mon,actor,q)).append('\n');
            }
            Files.writeString(Path.of("target","medicion-e08b.txt"),out.toString());
            System.out.println(out);
            Files.writeString(Path.of("target","medicion-e10.txt"),cuts(actor));
        }
    }
    /**
     * E10: generacion de un corte institucional de la Cuenta Publica dentro de STAT_CUT_CREATE (el comando completo, con el
     * lock global tomado de principio a fin), su verificacion y las consultas sobre el corte (las que admite un corte: modo
     * vigente y sin comparacion temporal), cada una con las mismas celdas que la consulta vigente.
     */
    String cuts(Access.Actor actor){
        String gest=staff("gest.medicion","REPORTES","GESTION_ESTADISTICA");
        var out=new StringBuilder("Medicion E10 - corte de la Cuenta Publica sobre "+TOTAL+" COM\n");
        long s=System.currentTimeMillis();
        long cut=cut(gest,null,cp());
        long create=System.currentTimeMillis()-s;
        out.append("STAT_CUT_CREATE (lock global);").append(create).append(" ms;");
        for(var t:StatCuts.TABLES)out.append(' ').append(t.name()).append('=').append(db.count("SELECT COUNT(*) FROM "+t.name()+" WHERE cut_id=?",cut));
        out.append('\n');
        s=System.currentTimeMillis();
        assertEquals(true,cutsService().verify(actor,cut).get("ok"));
        out.append("verificacion;").append(System.currentTimeMillis()-s).append(" ms\n");
        out.append("consulta sobre el corte;ms;celdas;ms de la misma consulta vigente\n");
        for(var q:consultas()){
            if(q.body().containsKey("compare")||q.body().containsKey("time_mode"))continue;
            var frozen=new LinkedHashMap<>(q.body());frozen.put("cut_id",cut);
            s=System.currentTimeMillis();
            var r=engine.query(actor,frozen);
            long ms=System.currentTimeMillis()-s;
            s=System.currentTimeMillis();
            var live=engine.query(actor,q.body());
            long liveMs=System.currentTimeMillis()-s;
            assertEquals(live.get("cells"),r.get("cells"),q.name());
            out.append(q.name()).append(';').append(ms).append(';').append(((List<?>)r.get("cells")).size()).append(';').append(liveMs).append('\n');
        }
        System.out.println(out);
        return out.toString();
    }
    @org.springframework.beans.factory.annotation.Autowired StatCuts cutService;
    StatCuts cutsService(){return cutService;}
    @org.springframework.beans.factory.annotation.Autowired StatExport exportService;

    @Test @EnabledIfSystemProperty(named="siger.stats.measure-e11",matches="true")
    void medicionE11()throws Exception{
        assertTrue(MySqlSuite.enabled(),"MySQL desechable obligatorio");
        assertEquals(30,guard.timeoutSeconds(),"plazo de producción para medir el ZIP");
        String schema=MySqlSuite.schema("sigera_medicion");
        var log=Path.of("target","medicion-e11.txt");
        try(var mon=raw()){
            String tmpdir=mon.text("SELECT @@tmpdir"),datadir=mon.text("SELECT @@datadir");
            assertEquals(List.of(),StatGuard.misplaced("D:",datadir,tmpdir,mon.text("SELECT @@innodb_temp_tablespaces_dir")));
            assertEquals("TempTable",mon.text("SELECT @@internal_tmp_mem_storage_engine"));
            assertTrue(mon.number("SELECT @@innodb_lock_wait_timeout")>guard.timeoutSeconds());
            File drive=new File("D:\\");assertTrue(drive.getUsableSpace()>=START_FREE);
            generate();assertEquals(TOTAL,db.count("SELECT COUNT(*) FROM communication"));
            mon.exec("ANALYZE TABLE `"+schema+"`.communication,`"+schema+"`.requirement,`"+schema+"`.case_record,`"+schema+"`.stat_event,`"+schema+"`.stat_declared_version,`"+schema+"`.stat_declared_person,`"+schema+"`.milestone,`"+schema+"`.command_log,`"+schema+"`.management_note");
            var stop=new AtomicBoolean();var killed=new AtomicBoolean();var minimum=new AtomicLong(Long.MAX_VALUE);var maxHeap=new AtomicLong();
            var monitorError=new AtomicReference<Exception>();
            var watcher=Thread.startVirtualThread(()->{
                try(var w=raw()){
                    while(!stop.get()){
                        long free=drive.getUsableSpace();minimum.accumulateAndGet(free,Math::min);
                        maxHeap.accumulateAndGet(ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed(),Math::max);
                        if(free<STOP_FREE&&!killed.getAndSet(true))for(long id:ids(w,schema))w.exec("KILL QUERY "+id);
                        Thread.sleep(100);
                    }
                }catch(Exception e){monitorError.set(e);}
            });
            // Cierre de E11: con la memoria de produccion (-Xmx1024m, Service.ps1); la condicion de detencion del GO es 75 %.
            long heapMax=Runtime.getRuntime().maxMemory();
            assertTrue(heapMax<=1024L<<20,"medir con -Xmx1024m (argLine); maximo actual "+(heapMax>>20)+" MiB");
            long cutMs;var zips=new ArrayList<Zip>();
            try(var gc=new AfterGc()){
                String gest=staff("gest.e11.medicion","REPORTES","GESTION_ESTADISTICA");
                var actor=access.actor(staff("exp.e11.medicion","REPORTES","EXPORTACION","REPORTES_PERSONAS"));
                var second=access.actor(staff("exp.e11.medicion2","REPORTES","EXPORTACION","REPORTES_PERSONAS"));
                System.gc();gc.reset();maxHeap.set(0);
                long start=System.nanoTime();long id=cut(gest,null,cp());cutMs=(System.nanoTime()-start)/1_000_000;
                String headline="COM="+TOTAL+"; corte="+cutMs+" ms; filas_corte="+db.count("SELECT COUNT(*) FROM stat_cut_row WHERE cut_id=?",id)
                    +"; heap_max_MiB="+(heapMax>>20)+"; despues_gc_max_MiB_corte="+(gc.reset()>>20)+"; heap_usado_max_MiB_corte="+(maxHeap.get()>>20)+"\n";
                Files.writeString(log,headline);System.out.print(headline);
                Map<String,Object> twoYears=map("measure","REQUERIMIENTOS_CON_VALOR","rows",List.of("DERECHO"),"columns",List.of("CONTEXTO"),"period",days("2024-07-01","2026-06-30"));
                Map<String,Object> account=map("measure","REQUERIMIENTOS_CON_VALOR","rows",List.of("DERECHO"),"columns",List.of("CONTEXTO"),"period",cp());
                var frozen=new LinkedHashMap<>(account);frozen.put("cut_id",id);
                for(var step:List.of(Map.entry("vigente Cuenta Publica",account),Map.entry("corte Cuenta Publica",frozen),Map.entry("vigente 2024-07-01 a 2026-06-30",twoYears))){
                    System.gc();long base=gc.reset();maxHeap.set(0);
                    var z=zip(step.getKey(),actor,step.getValue()).withMemory(base,gc.reset(),maxHeap.get());
                    zips.add(z);Files.writeString(log,z.line(),StandardOpenOption.APPEND);System.out.print(z.line());
                }
                System.gc();long base=gc.reset();maxHeap.set(0);
                var ready=new java.util.concurrent.CountDownLatch(2);var go=new java.util.concurrent.CountDownLatch(1);
                var pair=new ArrayList<java.util.concurrent.CompletableFuture<Zip>>();
                for(var who:List.of(actor,second))pair.add(java.util.concurrent.CompletableFuture.supplyAsync(()->{
                    ready.countDown();try{go.await();}catch(InterruptedException e){throw new IllegalStateException(e);}
                    return zip("simultaneo "+who.username(),who,account);
                },java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()));
                ready.await();start=System.nanoTime();go.countDown();
                var both=pair.stream().map(java.util.concurrent.CompletableFuture::join).toList();
                long wall=(System.nanoTime()-start)/1_000_000;long after=gc.reset(),peak=maxHeap.get();
                for(var z:both){var m=z.withMemory(base,after,peak);zips.add(m);Files.writeString(log,m.line(),StandardOpenOption.APPEND);System.out.print(m.line());}
                String pairLine="dos ZIP simultaneos (Cuenta Publica); pared="+wall+" ms; despues_gc_max_MiB="+(after>>20)+"; heap_usado_max_MiB="+(peak>>20)+"; libre_min_GiB="+(minimum.get()>>30)+"\n";
                Files.writeString(log,pairLine,StandardOpenOption.APPEND);System.out.print(pairLine);
            }finally{stop.set(true);watcher.join();}
            assertNull(monitorError.get());assertFalse(killed.get());
            // Criterios de la decision del responsable sobre la detencion de E11 (2026-11-26).
            assertTrue(cutMs<=13_000,"E11: STAT_CUT_CREATE supera 13 s");
            for(var z:zips){
                if(z.error()==null)assertTrue(z.ms()<30_000,"E11: el ZIP supera el plazo");
                assertTrue(z.afterGc()<=(768L<<20),z.name()+": memoria despues de recolectar sobre el 75 % de 1.024 MiB");
            }
            for(String name:List.of("vigente Cuenta Publica","corte Cuenta Publica")){
                var z=zips.stream().filter(x->x.name().equals(name)).findFirst().orElseThrow();
                assertNull(z.error(),name+": "+z.error());assertTrue(z.ms()<=25_000,name+": supera 25 s");
            }
            assertEquals("HTTP 409",zips.stream().filter(x->x.name().startsWith("vigente 2024")).findFirst().orElseThrow().error(),"dos anios superan 30.000 folios");
            var pairs=zips.stream().filter(x->x.name().startsWith("simultaneo")).toList();
            assertEquals(1,pairs.stream().filter(x->x.error()==null).count(),"uno de los dos simultaneos se genera");
            var busy=pairs.stream().filter(x->"HTTP 429".equals(x.error())).toList();
            assertEquals(1,busy.size(),"el segundo ZIP recibe 429");assertTrue(busy.getFirst().ms()<1_000,"429 de inmediato, sin cola");
        }
    }
    /** Un ZIP medido: tiempo total, fases de StatExport.PHASES y memoria (antes, mayor despues de recolectar y mayor uso muestreado). */
    record Zip(String name,long ms,long bytes,Map<String,Long> phases,String error,long baseAfterGc,long afterGc,long heapPeak){
        Zip withMemory(long base,long after,long peak){return new Zip(name,ms,bytes,phases,error,base,after,peak);}
        String line(){
            return "ZIP "+name+"; total="+ms+" ms; "+(error==null?"bytes="+bytes:"error="+error)+"; fases_ms="+phases
                +"; base_despues_gc_MiB="+(baseAfterGc>>20)+"; despues_gc_max_MiB="+(afterGc>>20)+"; heap_usado_max_MiB="+(heapPeak>>20)+"\n";
        }
    }
    Zip zip(String name,Access.Actor actor,Map<String,Object> q){
        var phases=new LinkedHashMap<String,Long>();StatExport.PHASES.set(phases);
        long start=System.nanoTime();
        try{
            var d=exportService.export(actor,"zip",q);
            return new Zip(name,(System.nanoTime()-start)/1_000_000,d.bytes().length,new LinkedHashMap<>(phases),null,0,0,0);
        }catch(Problem p){
            return new Zip(name,(System.nanoTime()-start)/1_000_000,0,new LinkedHashMap<>(phases),"HTTP "+p.status,0,0,0);
        }catch(OutOfMemoryError e){
            return new Zip(name,(System.nanoTime()-start)/1_000_000,0,new LinkedHashMap<>(phases),"OutOfMemoryError",0,0,0);
        }finally{StatExport.PHASES.remove();}
    }
    /**
     * Mayor ocupacion del heap despues de cada recoleccion (suma de los grupos HEAP de GcInfo.getMemoryUsageAfterGc, por la
     * notificacion de cada recolector) desde el ultimo reset(); a falta de notificaciones, la CollectionUsage de los grupos.
     */
    static final class AfterGc implements javax.management.NotificationListener,AutoCloseable {
        final AtomicLong max=new AtomicLong();
        final Set<String> heap=new HashSet<>();
        AfterGc()throws Exception{
            for(var p:ManagementFactory.getMemoryPoolMXBeans())if(p.getType()==java.lang.management.MemoryType.HEAP)heap.add(p.getName());
            for(var gc:ManagementFactory.getGarbageCollectorMXBeans())((javax.management.NotificationEmitter)gc).addNotificationListener(this,null,null);
        }
        @Override public void handleNotification(javax.management.Notification n,Object handback){
            if(!n.getType().equals(com.sun.management.GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION))return;
            var info=com.sun.management.GarbageCollectionNotificationInfo.from((javax.management.openmbean.CompositeData)n.getUserData());
            long used=0;for(var e:info.getGcInfo().getMemoryUsageAfterGc().entrySet())if(heap.contains(e.getKey()))used+=e.getValue().getUsed();
            max.accumulateAndGet(used,Math::max);
        }
        long reset(){
            long collection=0;
            for(var p:ManagementFactory.getMemoryPoolMXBeans())if(p.getType()==java.lang.management.MemoryType.HEAP&&p.getCollectionUsage()!=null)collection+=p.getCollectionUsage().getUsed();
            long seen=max.getAndSet(0);return seen>0?seen:collection;
        }
        @Override public void close()throws Exception{
            for(var gc:ManagementFactory.getGarbageCollectorMXBeans())((javax.management.NotificationEmitter)gc).removeNotificationListener(this);
        }
    }
    /** Created_tmp_disk_tables, Created_tmp_tables y Created_tmp_files del servidor (solo esta prueba lo usa). */
    static long[] status(Connection c)throws Exception{
        return new long[]{c.number("SELECT VARIABLE_VALUE FROM performance_schema.global_status WHERE VARIABLE_NAME='Created_tmp_disk_tables'"),
            c.number("SELECT VARIABLE_VALUE FROM performance_schema.global_status WHERE VARIABLE_NAME='Created_tmp_tables'"),
            c.number("SELECT VARIABLE_VALUE FROM performance_schema.global_status WHERE VARIABLE_NAME='Created_tmp_files'")};
    }
    static List<Long> ids(Connection c,String schema)throws Exception{
        var out=new ArrayList<Long>();
        try(var st=c.c().prepareStatement("SELECT ID FROM information_schema.PROCESSLIST WHERE DB=? AND COMMAND='Query' AND ID<>CONNECTION_ID()")){
            st.setString(1,schema);
            try(var rs=st.executeQuery()){while(rs.next())out.add(rs.getLong(1));}
        }
        return out;
    }
    static long size(String tmpdir){
        long total=0;
        for(String d:tmpdir.split(";"))try(var files=Files.walk(Path.of(d.strip()))){
            total+=files.filter(Files::isRegularFile).mapToLong(p->{try{return Files.size(p);}catch(Exception e){return 0;}}).sum();
        }catch(Exception ignored){}
        return total;
    }
    /** Menor max_join_size (potencia de 10, hasta 10^15) con que la consulta pasa; la variable global se restaura siempre. */
    String minimumJoinSize(Connection mon,Access.Actor actor,Consulta q)throws Exception{
        var hikari=(HikariDataSource)db.dataSource();
        String original=mon.text("SELECT @@GLOBAL.max_join_size");
        try{
            for(int e=4;e<=15;e++){
                mon.exec("SET GLOBAL max_join_size="+(long)Math.pow(10,e));
                hikari.getHikariPoolMXBean().softEvictConnections();
                try{engine.query(actor,q.body());return "1e"+e;}
                catch(RuntimeException x){
                    boolean tooBig=false;
                    for(Throwable t=x;t!=null;t=t.getCause())if(t instanceof java.sql.SQLException s&&s.getErrorCode()==1104)tooBig=true;
                    if(!tooBig)throw x;
                }
            }
            return ">1e15";
        }finally{
            mon.exec("SET GLOBAL max_join_size="+original);
            hikari.getHikariPoolMXBean().softEvictConnections();
        }
    }

    // --- Poblacion determinista ---------------------------------------------------------------------------------------
    long c0;
    void generate()throws Exception{
        catalogs(CATALOGS);
        staff(REP,"REPORTES");
        long admin=user("administrativo");
        var scopes=db.rows("SELECT id FROM org_scope WHERE enabled=TRUE ORDER BY id").stream().map(x->n(x,"id")).toList();
        long existing=db.count("SELECT COUNT(*) FROM communication");
        long count=TOTAL-existing;
        c0=db.count("SELECT COALESCE(MAX(id),0) FROM communication");
        long s0=db.count("SELECT COALESCE(MAX(id),0) FROM solicitation"),
            r0=db.count("SELECT COALESCE(MAX(id),0) FROM requirement"),k0=db.count("SELECT COALESCE(MAX(id),0) FROM case_record"),
            v0=db.count("SELECT COALESCE(MAX(id),0) FROM stat_declared_version");
        db.update("CREATE TABLE med_digit(d INT PRIMARY KEY)");
        db.update("INSERT INTO med_digit VALUES(0),(1),(2),(3),(4),(5),(6),(7),(8),(9)");
        db.update("CREATE TABLE med_seq(n BIGINT PRIMARY KEY)");
        db.update("INSERT INTO med_seq SELECT a.d+10*b.d+100*c.d+1000*d.d+10000*e.d+100000*f.d+1 FROM med_digit a,med_digit b,med_digit c,med_digit d,med_digit e,med_digit f WHERE a.d+10*b.d+100*c.d+1000*d.d+10000*e.d+100000*f.d<?",count);
        db.update("CREATE TABLE med_scope(k INT PRIMARY KEY,id BIGINT)");
        for(int i=0;i<scopes.size();i++)db.update("INSERT INTO med_scope VALUES(?,?)",i,scopes.get(i));
        int ns=scopes.size();
        String scope="(SELECT id FROM med_scope WHERE k=MOD(s.n,"+ns+"))";
        String received=FROM+"+FLOOR(s.n*"+SPAN+"/"+(count+1)+")";
        String created="("+received+"+CASE WHEN MOD(s.n,23)=0 THEN 259200000 ELSE MOD(s.n,5)*60000 END)";
        db.update("INSERT INTO communication(id,folio,channel,received_ms,created_ms,created_by,origin_scope,subject,body,state) SELECT "+c0+"+s.n,CONCAT('MED-',s.n),"
            +"ELT(MOD(s.n,6)+1,'"+String.join("','",CHANNELS)+"'),"+received+","+created+",?,"+scope+",'Ingreso sintetico','Antecedentes sinteticos',"
            +"CASE WHEN MOD(s.n,50)=0 THEN 'INVALIDA' WHEN MOD(s.n,10)=8 THEN 'NUEVA' WHEN MOD(s.n,10)=7 THEN 'ENVIADA' WHEN MOD(s.n,10)=6 THEN 'ATENDIDA' ELSE 'ENVIADA' END FROM med_seq s",admin);
        db.update("INSERT INTO solicitation(id,communication_id,destination_scope,state,classification,created_by,created_ms) SELECT "+s0+"+s.n,"+c0+"+s.n,"+scope+","
            +"CASE WHEN MOD(s.n,10)=7 THEN 'PENDIENTE' ELSE 'ASUMIDA' END,'Clasificacion sintetica',?,"+created+"+3600000 FROM med_seq s WHERE MOD(s.n,10)<8 AND MOD(s.n,10)<>6 AND MOD(s.n,50)<>0",admin);
        db.update("INSERT INTO requirement(id,folio,communication_id,solicitation_id,principal_scope,attention_user,attention_scope,complexity,state,created_ms,created_by,matter,closed_ms,closed_by) SELECT "
            +r0+"+s.n,CONCAT('MED-REQ-',s.n),"+c0+"+s.n,CASE WHEN MOD(s.n,10)=6 THEN NULL ELSE "+s0+"+s.n END,"+scope+",?,"+scope+","
            +"ELT(MOD(s.n,3)+1,'"+String.join("','",COMPLEXITY)+"'),CASE WHEN MOD(s.n,3)=0 THEN 'CERRADO' ELSE 'ABIERTO' END,"+created+"+86400000,?,"
            +"ELT(MOD(s.n,3)+1,'"+String.join("','",MATTERS)+"'),CASE WHEN MOD(s.n,3)=0 THEN "+created+"+864000000 END,CASE WHEN MOD(s.n,3)=0 THEN ? END FROM med_seq s WHERE MOD(s.n,10)<7 AND MOD(s.n,50)<>0",admin,admin,admin);
        db.update("INSERT INTO case_record(id,folio,requirement_id,scope_id,responsible_id,objective,matter,created_ms,created_by) SELECT "+k0+"+s.n,CONCAT('MED-CAS-',s.n),"+r0+"+s.n,"+scope+",?,'Objetivo sintetico',"
            +"ELT(MOD(s.n,3)+1,'"+String.join("','",MATTERS)+"'),"+created+"+172800000,? FROM med_seq s WHERE MOD(s.n,10)<4 AND MOD(s.n,50)<>0",admin,admin);
        db.update("INSERT INTO milestone(event,resource_type,resource_id,communication_id,requirement_id,actor_id,scope_id,at_ms) SELECT 'REQ_CREADO','REQ',q.id,q.communication_id,q.id,?,q.principal_scope,q.created_ms FROM requirement q WHERE q.id>?",admin,r0);
        db.update("INSERT INTO command_log(command_key,actor_id,payload_hash,result_kind,result_id,result_message,created_ms) SELECT CONCAT('med-',LPAD(q.id,32,'0')),?,REPEAT('0',64),'REQ',q.id,'REQ creado',q.created_ms FROM requirement q WHERE q.id>?",admin,r0);
        // Clasificaciones: version VIGENTE de cada catalogo y sus codigos seleccionables, en orden.
        classify("TIPO_SOLICITUD","COM",c0,"s.n","MOD(s.n,10)<8",1,admin,created);
        classify("TIPO_REQUIRENTE","COM",c0,"s.n","MOD(s.n,10)<7",1,admin,created);
        classify("DERECHO","REQ",r0,"s.n","MOD(s.n,10)<7 AND MOD(s.n,50)<>0",2,admin,created);
        classify("CONTEXTO","REQ",r0,"s.n","MOD(s.n,10)<6 AND MOD(s.n,50)<>0",1,admin,created);
        classify("CARACTERIZACION","REQ",r0,"s.n","MOD(s.n,10)<5 AND MOD(s.n,50)<>0",1,admin,created);
        classify("DERECHO","CAS",k0,"s.n","MOD(s.n,10)<4 AND MOD(s.n,50)<>0",1,admin,created);
        // Version declarada de la ficha de ingreso con quien presenta y un NNA (region, edad y nacionalidad).
        var regions=new ArrayList<>(AdultIdentity.REGIONS.keySet());Collections.sort(regions);
        var countries=new ArrayList<>(AdultIdentity.COUNTRIES);Collections.sort(countries);countries.remove("Chile");countries.addFirst("Chile");
        String region=" ELT(MOD(%s,"+regions.size()+")+1,'"+String.join("','",regions)+"')";
        db.update("INSERT INTO stat_declared_version(id,communication_id,source,source_id,registered_ms,requester_region,facts_region) SELECT "+v0+"+s.n,"+c0+"+s.n,'MEDICION',s.n,"+created+","
            +"CASE WHEN MOD(s.n,9)=0 THEN NULL ELSE"+region.formatted("s.n")+" END,"+region.formatted("s.n*7")+" FROM med_seq s");
        db.update("INSERT INTO stat_declared_person(version_id,person_order,person_role,region_code,age_years,age_origin,nationality) SELECT "+v0+"+s.n,1,'REQUIRENTE',"+region.formatted("s.n")+",18+MOD(s.n,60),'DECLARADA',"
            +"ELT(MOD(s.n,5)+1,'"+String.join("','",countries.subList(0,5))+"') FROM med_seq s");
        db.update("INSERT INTO stat_declared_person(version_id,person_order,person_role,region_code,age_years,age_origin,nationality) SELECT "+v0+"+s.n,2,'NNA',"+region.formatted("s.n*3")+",CASE WHEN MOD(s.n,13)=0 THEN NULL ELSE MOD(s.n,18) END,"
            +"CASE WHEN MOD(s.n,13)=0 THEN NULL WHEN MOD(s.n,2)=0 THEN 'NACIMIENTO' ELSE 'DECLARADA' END,ELT(MOD(s.n,4)+1,'"+String.join("','",countries.subList(0,4))+"') FROM med_seq s WHERE MOD(s.n,4)<>3");
        var action=db.one("SELECT v.id FROM stat_catalog_version v WHERE v.`current_catalog`='TIPO_ACTUACION'");
        var actions=db.rows("SELECT code FROM stat_catalog_item WHERE version_id=? AND selectable=TRUE ORDER BY sort",n(action,"id")).stream().map(x->s(x,"code")).toList();
        db.update("INSERT INTO management_note(requirement_id,case_id,author_id,effective_ms,registered_ms,body,reserved,action_type,action_catalog_version) SELECT "+r0+"+s.n,CASE WHEN MOD(s.n,10)<4 THEN "+k0+"+s.n END,?,"
            +created+"+259200000,"+created+"+259200000,'Constancia sintetica',MOD(s.n,17)=0,ELT(MOD(s.n,"+actions.size()+")+1,'"+String.join("','",actions)+"'),? FROM med_seq s WHERE MOD(s.n,10)<5 AND MOD(s.n,50)<>0",admin,n(action,"id"));
        for(String t:List.of("med_scope","med_seq","med_digit"))db.update("DROP TABLE "+t);
    }
    void classify(String variable,String record,long base,String key,String where,int values,long admin,String created){
        long version=n(db.one("SELECT id FROM stat_catalog_version WHERE `current_catalog`=?",variable),"id");
        var codes=db.rows("SELECT code FROM stat_catalog_item WHERE version_id=? AND selectable=TRUE ORDER BY sort",version).stream().map(x->s(x,"code")).toList();
        String list="'"+String.join("','",codes)+"'";
        for(int v=0;v<values;v++){
            String event=StatValues.MULTIPLE.contains(variable)?"ADD":"SET";
            db.update("INSERT INTO stat_event(communication_id,record_type,record_id,variable,event,status,catalog_version_id,code,origin,author_id,recorded_ms) SELECT "
                +c0+"+s.n,?,"+base+"+"+key+",?,?,'INFORMADO',?,ELT(MOD("+key+"*"+(v*5+1)+"+"+v+","+codes.size()+")+1,"+list+"),'FUNCIONARIO',?,"
                +created+"+"+(172800000L+v)+" FROM med_seq s WHERE "+where,record,variable,event,version,admin);
        }
    }
}
