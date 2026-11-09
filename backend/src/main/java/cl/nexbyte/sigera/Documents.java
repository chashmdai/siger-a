package cl.nexbyte.sigera;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import cl.nexbyte.sigera.Access.Actor;
import static cl.nexbyte.sigera.Db.*;
import static cl.nexbyte.sigera.Problem.require;

/** Copia fuera de la transaccion; confirmacion corta con permisos actuales. Todo entra en cuarentena. */
@Service
public class Documents {
    final Db db; final Access access; final Path directory; final TransactionTemplate tx;
    public Documents(Db db,Access access,PlatformTransactionManager manager,@Value("${siger.data}")String data){
        this.db=db;this.access=access;tx=new TransactionTemplate(manager);
        directory=Path.of(data).toAbsolutePath().normalize().resolve("documents");
    }
    private record Stored(String name,String key,String hash,long size,Path path) { }
    private Stored conserve(MultipartFile upload)throws Exception{
        require(!upload.isEmpty()&&upload.getSize()<=25L*1024*1024,"Archivo vacio o mayor a 25 MiB.");
        String name=Objects.toString(upload.getOriginalFilename(),"archivo").replaceAll("[\\\\/\\p{Cntrl}]","_");
        require(name.length()<=180&&!name.isBlank(),"Nombre de archivo invalido.");
        Files.createDirectories(directory);require(!Files.isSymbolicLink(directory),"Almacen no valido.");
        String key=UUID.randomUUID()+".bin";Path tmp=directory.resolve(key+".tmp"),dest=directory.resolve(key);
        var digest=MessageDigest.getInstance("SHA-256");long count=0;
        try(var input=upload.getInputStream();var output=Files.newOutputStream(tmp,StandardOpenOption.CREATE_NEW)){
            byte[] buffer=new byte[65536];int size;
            while((size=input.read(buffer))!=-1){count+=size;require(count<=25L*1024*1024,"Limite de archivo excedido.");digest.update(buffer,0,size);output.write(buffer,0,size);}
        }catch(Exception e){Files.deleteIfExists(tmp);throw e;}
        try{
            require(count>0,"Archivo vacio.");
            try(var file=java.nio.channels.FileChannel.open(tmp,StandardOpenOption.WRITE)){file.force(true);}
            Files.move(tmp,dest,StandardCopyOption.ATOMIC_MOVE);
        }catch(Exception e){Files.deleteIfExists(tmp);throw e;}
        return new Stored(name,key,HexFormat.of().formatHex(digest.digest()),count,dest);
    }
    private Actor active(String username){var a=access.actor(username);if(a.mustChange())throw new Problem(428,"Cambia la clave temporal.");return a;}
    public Map<String,Object> stage(String username,long scope,UUID token,MultipartFile file)throws Exception{
        Actor initial=active(username);access.scope(scope);if(!initial.registrar(scope))Problem.denied();
        var stored=conserve(file);boolean[] used={false};
        try{return tx.execute(status->{
            db.lock();var a=active(username);access.scope(scope);if(!a.registrar(scope))Problem.denied();
            var prior=db.rows("SELECT * FROM intake_upload WHERE token=?",token.toString());
            if(!prior.isEmpty()){
                var old=prior.getFirst();require(n(old,"user_id")==a.id()&&n(old,"scope_id")==scope&&s(old,"sha256").equals(stored.hash())&&s(old,"name").equals(stored.name()),"Clave de carga reutilizada con otros datos.");
                require(old.get("communication_id")==null,"El archivo ya fue incorporado.");
            }else{
                require(db.count("SELECT COUNT(*) FROM intake_upload WHERE user_id=? AND communication_id IS NULL",a.id())<12,"Ya tienes 12 adjuntos preparados. Retira los que no utilizaras.");
                db.update("INSERT INTO intake_upload(token,user_id,scope_id,name,storage_key,sha256,size_bytes,created_ms) VALUES(?,?,?,?,?,?,?,?)",token.toString(),a.id(),scope,stored.name(),stored.key(),stored.hash(),stored.size(),System.currentTimeMillis());used[0]=true;
            }
            return Map.<String,Object>of("token",token.toString(),"name",stored.name(),"size_bytes",stored.size());
        });}catch(Exception e){used[0]=false;throw e;}finally{if(!used[0])Files.deleteIfExists(stored.path());}
    }
    public List<Map<String,Object>> drafts(String username){var a=active(username);return db.rows("SELECT token,scope_id,name,size_bytes,created_ms FROM intake_upload WHERE user_id=? AND communication_id IS NULL ORDER BY created_ms",a.id());}
    public void discard(String username,UUID token)throws IOException{
        String key=tx.execute(status->{db.lock();var a=active(username);var found=db.rows("SELECT * FROM intake_upload WHERE token=? AND user_id=?",token.toString(),a.id());
            if(found.isEmpty())return null;var r=found.getFirst();require(r.get("communication_id")==null,"Ya esta incorporado a una COM; no se elimina.");
            db.update("DELETE FROM intake_upload WHERE token=?",token.toString());return s(r,"storage_key");});
        if(key!=null)Files.deleteIfExists(path(key));
    }
    /** Invocado dentro del comando COM: la misma confirmacion enlaza originales y primera atencion. */
    public void attachIntake(Actor a,long scope,long com,Object values){
        if(values==null)return;
        if(!(values instanceof List<?> tokens)||tokens.size()>12)throw new Problem(400,"Adjuntos de ingreso invalidos.");
        var unique=new HashSet<String>();
        for(Object raw:tokens){
            String token;try{token=UUID.fromString(Objects.toString(raw,"")).toString();}catch(Exception e){throw new Problem(400,"Token de adjunto invalido.");}
            require(unique.add(token),"Adjunto repetido.");var r=db.one("SELECT * FROM intake_upload WHERE token=?",token);
            require(n(r,"user_id")==a.id()&&n(r,"scope_id")==scope&&r.get("communication_id")==null,"Adjunto de otra atencion o ya incorporado.");
            try{require(Files.isRegularFile(path(s(r,"storage_key")))&&Files.size(path(s(r,"storage_key")))==n(r,"size_bytes"),"Adjunto no disponible.");}catch(IOException e){throw new Problem(409,"No se pudo verificar el archivo preparado.");}
            db.update("INSERT INTO document_item(communication_id,name,storage_key,sha256,size_bytes,content_type,uploaded_by,created_ms,document_type) VALUES(?,?,?,?,?,'application/octet-stream',?,?,'INGRESO')",com,s(r,"name"),s(r,"storage_key"),s(r,"sha256"),n(r,"size_bytes"),a.id(),System.currentTimeMillis());
            db.update("UPDATE intake_upload SET communication_id=? WHERE token=?",com,token);
        }
    }
    public Map<String,Object> upload(String username,String kind,long target,boolean reserved,Long note,Long previous,String type,String description,UUID key,MultipartFile upload)throws Exception{
        authorize(active(username),kind,target,true,reserved);
        require(Set.of("OFICIO","INFORME","CORREO","CONSTANCIA","OTRO").contains(type),"Tipo de documento invalido.");
        require(description!=null&&description.length()<=500,"Descripcion demasiado larga.");
        var stored=conserve(upload);boolean[] used={false};
        try{return tx.execute(status->{
            db.lock();Actor a=active(username);long scope=authorize(a,kind,target,true,reserved);boolean effectiveReserved=reserved;
            if(note!=null){
                var n=db.one("SELECT * FROM management_note WHERE id=?",note);
                require(kind.equals("CAS")&&Objects.equals(nullableId(n,"case_id"),target)||kind.equals("REQ")&&n.get("case_id")==null&&n(n,"requirement_id")==target,"La actuacion debe pertenecer a esta misma gestion.");
                if(b(n,"reserved")){authorize(a,kind,target,true,true);effectiveReserved=true;}
            }
            if(previous!=null){
                var old=db.one("SELECT * FROM document_item WHERE id=?",previous);
                require(!kind.equals("COM")&&Objects.equals(nullableId(old,column(kind)),target),"Solo se versionan documentos de la misma gestion, no originales de ingreso.");
                if(b(old,"reserved")){authorize(a,kind,target,true,true);effectiveReserved=true;}
            }
            var repeat=db.rows("SELECT * FROM document_item WHERE upload_key=?",key.toString());
            if(!repeat.isEmpty()){
                var d=repeat.getFirst();require(n(d,"uploaded_by")==a.id()&&Objects.equals(nullableId(d,column(kind)),target)&&s(d,"sha256").equals(stored.hash())&&s(d,"name").equals(stored.name())&&Objects.equals(nullableId(d,"note_id"),note)&&Objects.equals(nullableId(d,"previous_document_id"),previous)&&s(d,"document_type").equals(type)&&s(d,"description").equals(description)&&b(d,"reserved")==effectiveReserved,"Clave de carga reutilizada con otros datos.");
                return Map.<String,Object>of("id",n(d,"id"),"name",s(d,"name"),"quarantined",b(d,"quarantined"));
            }
            if(previous!=null)require(db.count("SELECT COUNT(*) FROM document_item WHERE previous_document_id=?",previous)==0,"Este documento ya tiene una version posterior.");
            long id=db.insert("INSERT INTO document_item(communication_id,requirement_id,case_id,name,storage_key,sha256,size_bytes,content_type,uploaded_by,created_ms,reserved,note_id,document_type,description,previous_document_id,upload_key) VALUES(?,?,?,?,?,?,?,'application/octet-stream',?,?,?,?,?,?,?,?)",kind.equals("COM")?target:null,kind.equals("REQ")?target:null,kind.equals("CAS")?target:null,stored.name(),stored.key(),stored.hash(),stored.size(),a.id(),System.currentTimeMillis(),effectiveReserved,note,type,description,previous,key.toString());
            db.audit(a.id(),"DOCUMENTO_CUSTODIADO",kind,target,scope,"Documento "+id+" en cuarentena"+(note==null?"":"; actuacion "+note)+(previous==null?"":"; version de "+previous));
            used[0]=true;return Map.<String,Object>of("id",id,"name",stored.name(),"sha256",stored.hash(),"quarantined",true);
        });}catch(Exception e){used[0]=false;throw e;}finally{if(!used[0])Files.deleteIfExists(stored.path());}
    }
    // --- Originales ciudadanos del Formulario (consumidor PULL) -------------------------------------
    /**
     * Clave de almacenamiento derivada solo de UUID firmados (operacion y adjunto), nunca del nombre ciudadano:
     * un reintento despues de un corte encuentra el mismo archivo y lo verifica en vez de descargarlo otra vez.
     */
    static String externalKey(String operation,String attachment){
        String op=UUID.fromString(operation).toString(),file=UUID.fromString(attachment).toString();
        return UUID.nameUUIDFromBytes(("SIGER_FORMULARIO_ADJUNTO\0"+op+"\0"+file).getBytes(java.nio.charset.StandardCharsets.UTF_8))+".bin";
    }
    /**
     * Custodia por streaming: escribe en un temporal, corta al superar el tamano firmado, exige tamano y SHA-256
     * exactos del descriptor, fuerza a disco y publica con movimiento atomico. Un archivo ya publicado se verifica
     * completo y nunca se reemplaza. Fuera de transaccion: el registro en SQL ocurre despues.
     */
    void custodyExternal(String key,long size,String sha256,InputStream input)throws IOException{
        Path dest=path(key);
        if(Files.exists(dest,LinkOption.NOFOLLOW_LINKS)){if(!externalIntact(key,size,sha256))throw new IOException("Original custodiado alterado.");return;}
        Files.createDirectories(directory);if(Files.isSymbolicLink(directory))throw new IOException("Almacen no valido.");
        Path tmp=directory.resolve(key+"."+UUID.randomUUID()+".tmp");
        try{
            MessageDigest digest;try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
            long count=0;
            try(var output=java.nio.channels.FileChannel.open(tmp,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){
                byte[] buffer=new byte[65536];
                for(int n;(n=input.read(buffer))!=-1;){
                    count=Math.addExact(count,n);if(count>size)throw new IOException("Original mayor que el tamano firmado.");
                    digest.update(buffer,0,n);var bytes=java.nio.ByteBuffer.wrap(buffer,0,n);while(bytes.hasRemaining())output.write(bytes);
                }
                if(count!=size||!HexFormat.of().formatHex(digest.digest()).equals(sha256))throw new IOException("Original no corresponde al descriptor firmado.");
                output.force(true);
            }
            try{Files.move(tmp,dest,StandardCopyOption.ATOMIC_MOVE);}
            catch(FileAlreadyExistsException race){if(!externalIntact(key,size,sha256))throw new IOException("Original custodiado alterado.");}
        }finally{Files.deleteIfExists(tmp);}
    }
    /** Comprobacion completa (existencia, tamano y SHA-256) de un original custodiado. */
    boolean externalIntact(String key,long size,String sha256)throws IOException{
        Path p=path(key);
        if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||Files.size(p)!=size)return false;
        MessageDigest digest;try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
        try(var in=Files.newInputStream(p,LinkOption.NOFOLLOW_LINKS)){byte[] b=new byte[65536];for(int n;(n=in.read(b))!=-1;)digest.update(b,0,n);}
        return HexFormat.of().formatHex(digest.digest()).equals(sha256);
    }
    /** Comprobacion breve dentro de la transaccion de registro (la completa se hace antes y antes de cada ACK). */
    boolean externalPresent(String key,long size){
        try{Path p=path(key);return Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)&&Files.size(p)==size;}catch(IOException e){return false;}
    }

    private static String column(String kind){return switch(kind){case "COM"->"communication_id";case "REQ"->"requirement_id";case "CAS"->"case_id";default->throw new Problem(400,"Contexto invalido.");};}
    private Path path(String key){require(key.matches("[a-f0-9-]{36}\\.bin"),"Clave documental invalida.");Path p=directory.resolve(key).normalize();require(p.startsWith(directory)&&!Files.isSymbolicLink(p),"Almacen invalido.");return p;}
    public record Download(Path path,String name,long size) { }
    public Download download(Actor a,long id)throws Exception{
        var d=db.one("SELECT * FROM document_item WHERE id=?",id);String kind=d.get("case_id")!=null?"CAS":d.get("requirement_id")!=null?"REQ":"COM";long target=n(d,column(kind));
        long scope=authorize(a,kind,target,false,b(d,"reserved"));require(!b(d,"quarantined"),"Documento en cuarentena. Requiere revision tecnica externa.");
        Path path=path(s(d,"storage_key"));require(Files.isRegularFile(path),"Original no disponible.");
        var hash=MessageDigest.getInstance("SHA-256");long size=0;
        try(var in=Files.newInputStream(path)){byte[] block=new byte[65536];int count;while((count=in.read(block))!=-1){size+=count;hash.update(block,0,count);}}
        require(size==n(d,"size_bytes")&&HexFormat.of().formatHex(hash.digest()).equals(s(d,"sha256")),"El original no supera la comprobacion de integridad.");
        authorize(active(a.username()),kind,target,false,b(d,"reserved"));
        db.audit(a.id(),"DESCARGA",kind,target,scope,"Documento "+id);return new Download(path,s(d,"name"),size);
    }
    private boolean caseCanReadOriginal(Actor a,long communication){
        for(var c:db.rows("SELECT c.* FROM case_record c JOIN requirement r ON r.id=c.requirement_id WHERE r.communication_id=?",communication))if(access.caseSourceDocuments(a,c))return true;
        for(var c:db.rows("SELECT c.* FROM case_record c JOIN communication_link l ON l.target_case_id=c.id WHERE l.communication_id=? AND l.state='ADMITIDA' AND l.returned_ms IS NULL",communication))if(access.caseSourceDocuments(a,c))return true;
        return false;
    }
    private long authorize(Actor a,String kind,long target,boolean write,boolean reserved){
        switch(kind){
            case "COM" -> {var c=db.one("SELECT * FROM communication WHERE id=?",target);
                if(!access.comRead(a,c)&&!(!write&&caseCanReadOriginal(a,target)))Problem.denied();
                if(reserved)throw new Problem(400,"La reserva de gestion no modifica el original de ingreso.");
                if(write){boolean initial=n(c,"created_by")==a.id()&&s(c,"state").equals("NUEVA");boolean duringAttention=db.rows("SELECT * FROM requirement WHERE communication_id=?",target).stream().anyMatch(r->access.attentionEditable(a,r));require(initial||duringAttention,"El ingreso ya fue encaminado; incorpora nuevos antecedentes como otra COM.");}return n(c,"origin_scope");}
            case "REQ" -> {var r=access.reqForRead(a,target);if(!access.reqMain(a,r))Problem.denied();if(reserved&&!a.head(n(r,"principal_scope")))Problem.denied();if(write)require(s(r,"state").equals("ABIERTO")&&db.count("SELECT COUNT(*) FROM case_record WHERE requirement_id=?",target)==0,"Utiliza el Caso activo o una COM hija.");return n(r,"principal_scope");}
            case "CAS" -> {var c=write?access.caseForWrite(a,target):access.caseForRead(a,target);if(reserved&&!access.reserved(a,c))Problem.denied();if(write)require(!s(c,"state").equals("CERRADO"),"No se carga gestion sobre Casos cerrados.");return n(c,"scope_id");}
            default -> throw new Problem(400,"Contexto documental invalido.");
        }
    }
}
