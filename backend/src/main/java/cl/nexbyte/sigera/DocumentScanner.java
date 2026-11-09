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

    Verdict scan(Path file,Path work){
        if(!configured())return new Verdict("ERROR",null,engine,null,"SIN_MOTOR");
        try{
            String version=signatures();
            if(version==null)return new Verdict("ERROR",null,engine,null,"MOTOR_NO_OPERATIVO_O_FIRMAS_DESACTUALIZADAS");
            List<String> command;
            if(engine.equals("CLAMAV")){
                Files.createDirectories(work);
                command=List.of(executable.toString(),"--database="+database,"--tempdir="+work,"--max-filesize=25M","--max-scansize=50M","--max-files=2000",
                    "--max-recursion=16","--max-scantime=45000","--alert-exceeds-max=yes","--no-summary","--official-db-only=yes","--bytecode-unsigned=no",file.toString());
            }else command=List.of(executable.toString(),"-Scan","-ScanType","3","-File",file.toString(),"-DisableRemediation");
            var out=runner.run(command,timeoutMs);
            // ClamAV: 0 limpio, 1 detectado (incluye limites excedidos). Defender: 0 sin amenazas, 2 amenaza.
            int infected=engine.equals("CLAMAV")?1:2;
            String verdict=out.exitCode()==0?"LIMPIO":out.exitCode()==infected?"INFECTADO":"ERROR";
            return new Verdict(verdict,out.exitCode(),engine,version,verdict.equals("ERROR")?"ANALISIS_INCOMPLETO":null);
        }catch(InterruptedException e){Thread.currentThread().interrupt();return new Verdict("ERROR",null,engine,null,"ANALISIS_INTERRUMPIDO");}
        catch(IOException|RuntimeException e){return new Verdict("ERROR",null,engine,null,"ANALISIS_NO_EJECUTADO");}
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
