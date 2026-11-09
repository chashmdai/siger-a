package cl.nexbyte.sigera;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Analisis antivirus de un original custodiado, requisito para liberar documentos reales (DOC_RELEASE).
 * Motores admitidos (siger.documents.av.engine): CLAMAV (clamscan.exe con sus bases, mismos limites que SMR) o
 * DEFENDER (MpCmdRun.exe de Microsoft Defender). NONE (por defecto) no analiza: sin motor no se libera nada real.
 * Comandos fijos sin shell, tiempo maximo, salida acotada. Antes de confiar en un veredicto se comprueba que el motor
 * este operativo y con firmas recientes; cualquier duda es ERROR (falla cerrado), nunca LIMPIO.
 */
@Component
public class DocumentScanner {
    record Verdict(String verdict,Integer exitCode,String engine,String engineVersion,String detail){}
    record Output(int exitCode,String text){}
    /** Ejecucion de un comando fijo; reemplazable en pruebas. */
    @FunctionalInterface interface Runner {Output run(List<String> command,long timeoutMs)throws IOException,InterruptedException;}

    private final String engine;private final Path executable,database;private final long timeoutMs,maxSignatureAgeMs;
    private final Runner runner;private final Clock clock;

    @Autowired
    public DocumentScanner(Environment env){this(env,DocumentScanner::process,Clock.systemUTC());}
    DocumentScanner(Environment env,Runner runner,Clock clock){
        this.engine=env.getProperty("siger.documents.av.engine","NONE").strip().toUpperCase(Locale.ROOT);
        if(!Set.of("NONE","CLAMAV","DEFENDER").contains(engine))throw new IllegalStateException("siger.documents.av.engine debe ser NONE, CLAMAV o DEFENDER.");
        String exe=env.getProperty("siger.documents.av.executable",""),db=env.getProperty("siger.documents.av.database","");
        this.executable=exe.isBlank()?null:Path.of(exe).toAbsolutePath().normalize();
        this.database=db.isBlank()?null:Path.of(db).toAbsolutePath().normalize();
        this.timeoutMs=env.getProperty("siger.documents.av.timeout-ms",Long.class,300_000L);
        this.maxSignatureAgeMs=env.getProperty("siger.documents.av.max-signature-age-hours",Long.class,72L)*3_600_000L;
        this.runner=runner;this.clock=clock;
        if(!engine.equals("NONE")){
            String name=engine.equals("CLAMAV")?"clamscan.exe":"MpCmdRun.exe";
            if(executable==null||!executable.getFileName().toString().equalsIgnoreCase(name)||!Files.isRegularFile(executable))
                throw new IllegalStateException("siger.documents.av.executable debe apuntar a "+name+" existente.");
            if(engine.equals("CLAMAV")&&(database==null||!Files.isDirectory(database)))throw new IllegalStateException("ClamAV exige siger.documents.av.database con sus bases de firmas.");
            if(timeoutMs<10_000||timeoutMs>1_800_000||maxSignatureAgeMs<3_600_000L)throw new IllegalStateException("Limites del analisis antivirus fuera de rango.");
        }
    }
    boolean configured(){return !engine.equals("NONE");}
    String engine(){return engine;}

    /**
     * Analiza un original. workRoot es el directorio temporal de la instancia: ClamAV recibe una subcarpeta propia que
     * se elimina siempre al terminar (finally), tambien ante error, interrupcion o tiempo excedido.
     */
    Verdict scan(Path file,Path workRoot){
        if(!configured())return new Verdict("ERROR",null,engine,null,"SIN_MOTOR");
        Path work=null;
        try{
            String version=signatures();
            if(version==null)return new Verdict("ERROR",null,engine,null,"MOTOR_NO_OPERATIVO_O_FIRMAS_DESACTUALIZADAS");
            List<String> command;
            if(engine.equals("CLAMAV")){
                work=Files.createDirectories(workRoot.resolve("av-"+UUID.randomUUID()));
                command=List.of(executable.toString(),"--database="+database,"--tempdir="+work,"--max-filesize=25M","--max-scansize=50M","--max-files=2000",
                    "--max-recursion=16","--max-scantime=45000","--alert-exceeds-max=yes","--no-summary","--official-db-only=yes","--bytecode-unsigned=no",file.toString());
            }else command=List.of(executable.toString(),"-Scan","-ScanType","3","-File",file.toString(),"-DisableRemediation");
            var out=runner.run(command,timeoutMs);
            if(engine.equals("DEFENDER"))return defender(file,out,version);
            if(out.exitCode()==0)return new Verdict("LIMPIO",0,engine,version,null);
            if(out.exitCode()!=1)return new Verdict("ERROR",out.exitCode(),engine,version,"ANALISIS_INCOMPLETO");
            return clamFound(file,out.text(),version);
        }catch(InterruptedException e){Thread.currentThread().interrupt();return new Verdict("ERROR",null,engine,null,"ANALISIS_INTERRUMPIDO");}
        catch(IOException|RuntimeException e){return new Verdict("ERROR",null,engine,null,"ANALISIS_NO_EJECUTADO");}
        finally{delete(work);}
    }

    /**
     * ClamAV termina con 1 tanto ante malware como cuando --alert-exceeds-max marca un limite excedido
     * (Heuristics.Limits.Exceeded.*). Solo una firma de deteccion distinta, informada para ESTE archivo, es una deteccion
     * acreditada (INFECTADO, permanente). Limites excedidos o una salida 1 sin resultado legible son ERROR: el original
     * sigue en cuarentena y puede volver a analizarse, pero no queda marcado como infectado.
     */
    private Verdict clamFound(Path file,String text,String version){
        String prefix=file.toString()+": ";boolean limits=false;String signature=null;
        for(String line:text.split("\\R")){
            line=line.strip();
            if(!line.startsWith(prefix)||!line.endsWith(" FOUND"))continue;
            String name=line.substring(prefix.length(),line.length()-" FOUND".length()).strip();
            if(!name.matches("[A-Za-z0-9._:-]{1,200}"))continue;
            if(name.startsWith("Heuristics.Limits.Exceeded"))limits=true;
            else if(signature==null)signature=name;
        }
        if(signature!=null){String detail="FIRMA:"+signature;return new Verdict("INFECTADO",1,engine,version,detail.substring(0,Math.min(64,detail.length())));}
        return new Verdict("ERROR",1,engine,version,limits?"LIMITES_EXCEDIDOS":"ANALISIS_INCOMPLETO");
    }

    /**
     * MpCmdRun usa el codigo 2 tanto para "amenaza encontrada" como para fallos del motor (p. ej. hr=0x800106ba con el
     * servicio inactivo, verificado en Windows Server 2022). Por eso el codigo solo no acredita nada: LIMPIO exige 0 y
     * "found no threats" para este archivo; INFECTADO exige 2 y "found N threats" (N>0) sin mensaje de fallo. Todo lo
     * demas es ERROR y el original sigue en cuarentena sin quedar marcado como infectado.
     */
    private Verdict defender(Path file,Output out,String version){
        String text=out.text();
        var failure=java.util.regex.Pattern.compile("Failed with hr = (0x[0-9A-Fa-f]{8})").matcher(text);
        if(failure.find())return new Verdict("ERROR",out.exitCode(),engine,version,"MOTOR_FALLIDO_"+failure.group(1).toLowerCase(Locale.ROOT));
        String scanned="Scanning "+file+" found ";
        int at=text.indexOf(scanned);
        if(at<0)return new Verdict("ERROR",out.exitCode(),engine,version,"ANALISIS_INCOMPLETO");
        String rest=text.substring(at+scanned.length());
        if(out.exitCode()==0&&rest.startsWith("no threats"))return new Verdict("LIMPIO",0,engine,version,null);
        var threats=java.util.regex.Pattern.compile("^([0-9]{1,6}) threats").matcher(rest);
        if(out.exitCode()==2&&threats.find()&&Integer.parseInt(threats.group(1))>0){
            var name=java.util.regex.Pattern.compile("Threat\\s*:\\s*([A-Za-z0-9!/:._-]{1,200})").matcher(text);
            String detail=name.find()?"AMENAZA:"+name.group(1):"DETECCION_DEFENDER";
            return new Verdict("INFECTADO",2,engine,version,detail.substring(0,Math.min(64,detail.length())));
        }
        return new Verdict("ERROR",out.exitCode(),engine,version,"ANALISIS_INCOMPLETO");
    }

    /** Elimina la carpeta temporal del analisis y su contenido; nunca sale de ella ni sigue enlaces. */
    private static void delete(Path work){
        if(work==null||!Files.isDirectory(work,java.nio.file.LinkOption.NOFOLLOW_LINKS))return;
        try(var walk=Files.walk(work)){
            for(var p:walk.sorted(Comparator.reverseOrder()).toList())if(p.startsWith(work))try{Files.deleteIfExists(p);}catch(IOException ignored){}
        }catch(IOException|RuntimeException ignored){}
    }

    /** Version de firmas vigente o null si el motor no esta operativo o sus firmas superan la antiguedad maxima. */
    String signatures()throws IOException,InterruptedException{
        Instant updated;String version;
        if(engine.equals("CLAMAV")){
            // "ClamAV 1.4.1/27450/Mon Nov  4 09:31:12 2026"
            var out=runner.run(List.of(executable.toString(),"--version","--database="+database),60_000);
            String[] parts=out.text().strip().split("/");
            if(out.exitCode()!=0||parts.length<3||!parts[0].startsWith("ClamAV "))return null;
            try{updated=LocalDateTime.parse(parts[2].strip().replaceAll("\\s+"," "),DateTimeFormatter.ofPattern("EEE MMM d HH:mm:ss yyyy",Locale.ENGLISH)).atZone(ZoneId.systemDefault()).toInstant();}
            catch(RuntimeException e){return null;}
            version=parts[0].substring(7)+"/"+parts[1];
        }else{
            // Estado del servicio: motor activo, proteccion en tiempo real y fecha de firmas (solo lectura).
            var out=runner.run(List.of(Path.of(System.getenv().getOrDefault("SystemRoot","C:\\Windows"),"System32","WindowsPowerShell","v1.0","powershell.exe").toString(),
                "-NoProfile","-NonInteractive","-Command",
                "$s=Get-MpComputerStatus;if(-not($s.AMServiceEnabled -and $s.AntivirusEnabled)){exit 3};$s.AntivirusSignatureVersion+'|'+$s.AntivirusSignatureLastUpdated.ToUniversalTime().ToString('o')"),60_000);
            String[] parts=out.text().strip().split("\\|");
            if(out.exitCode()!=0||parts.length!=2)return null;
            try{updated=Instant.parse(parts[1].strip());}catch(RuntimeException e){return null;}
            version=parts[0].strip();
        }
        if(version.length()>120||Duration.between(updated,clock.instant()).toMillis()>maxSignatureAgeMs)return null;
        return version;
    }

    private static Output process(List<String> command,long timeoutMs)throws IOException,InterruptedException{
        var p=new ProcessBuilder(command).redirectErrorStream(true).start();
        var text=new StringBuilder();
        var reader=Thread.ofVirtual().start(()->{
            try(var in=p.getInputStream()){byte[] b=in.readNBytes(16_384);text.append(new String(b,StandardCharsets.UTF_8));in.transferTo(java.io.OutputStream.nullOutputStream());}
            catch(IOException ignored){}
        });
        if(!p.waitFor(timeoutMs,TimeUnit.MILLISECONDS)){p.destroyForcibly();p.waitFor(10,TimeUnit.SECONDS);throw new IOException("Tiempo de analisis excedido");}
        reader.join(5_000);
        synchronized(text){return new Output(p.exitValue(),text.toString());}
    }
}
