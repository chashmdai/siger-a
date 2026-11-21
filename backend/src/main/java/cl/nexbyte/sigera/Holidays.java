package cl.nexbyte.sigera;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import cl.nexbyte.sigera.Access.Actor;
import static cl.nexbyte.sigera.Db.*;

/**
 * Feriados del calendario habil (0.5.0, D6, gate G09). Solo TI, con fundamento y un evento de auditoria por feriado
 * (recurso CALENDARIO). Los nacionales tienen region nula; los regionales, el codigo de region del Formulario.
 * Un mismo dia no se registra dos veces para la misma region, ni como regional si ya es nacional (o al reves): el
 * calendario queda sin ambiguedades. Retirar borra la fila; la historia queda en audit_event.
 * HOLIDAY_IMPORT valida todas las lineas antes de escribir y graba todo o nada.
 */
@Service
public class Holidays {
    static final int MAX_IMPORT=200,MAX_ERRORS=20;
    static final String ADDED="FERIADO_AGREGADO",REMOVED="FERIADO_RETIRADO",KIND="CALENDARIO";
    /** Linea de importacion: "aaaa-mm-dd Nombre" con region opcional al final entre corchetes: "[15]". */
    static final Pattern LINE=Pattern.compile("^(\\S+)\\s+(.+?)(?:\\s*\\[\\s*([^\\]]*?)\\s*\\])?$");
    private final Db db;
    public Holidays(Db db){this.db=db;}

    record Entry(LocalDate day,String region,String name){
        String key(){return region==null?BusinessCalendar.NATIONAL:region;}
        String label(){return day+(region==null?" nacional ":" región "+region+" ")+name;}
    }

    Workflow.Result run(Actor a,String type,long id,Input in){
        if(!a.role(Roles.TI))Problem.denied();
        return switch(type){
            case "HOLIDAY_ADD" -> {
                var e=entry(in.text("day",10,true),in.text("region_code",4,false),in.text("name",120,true));
                String reason=in.reason();
                String clash=conflict(e,List.of());if(clash!=null)throw new Problem(409,clash);
                yield Workflow.done(KIND,insert(a,e,reason));
            }
            case "HOLIDAY_IMPORT" -> {
                var entries=parse(in.text("lines",30000,true));String reason=in.reason();
                long first=0;String note=entries.size()==1?"Importación de 1 feriado. ":"Importación de "+entries.size()+" feriados. ";
                for(var e:entries){long row=insert(a,e,note+reason);if(first==0)first=row;}
                yield new Workflow.Result(KIND,first,entries.size()==1?"Se registró 1 feriado.":"Se registraron "+entries.size()+" feriados.");
            }
            case "HOLIDAY_REMOVE" -> {
                var rows=db.rows("SELECT * FROM business_holiday WHERE id=?",id);
                Problem.require(!rows.isEmpty(),"El feriado ya no está registrado. Actualiza el calendario.");
                var r=rows.getFirst();String reason=in.reason();
                db.update("DELETE FROM business_holiday WHERE id=?",id);
                db.audit(a.id(),REMOVED,KIND,id,null,entryOf(r).label()+". "+reason);
                yield Workflow.done(KIND,id);
            }
            default -> throw new Problem(400,"Operacion de calendario desconocida.");
        };
    }

    /** Lectura para TI: feriados ordenados por fecha y region, y la fecha de hoy en Chile (para los avisos por anio). */
    public Map<String,Object> calendar(Actor a){
        if(!a.role(Roles.TI))Problem.denied();
        return Map.of("holidays",db.rows("SELECT id,holiday_day,region_code,name,created_by,created_ms FROM business_holiday ORDER BY holiday_day,region_key"),
            "today",LocalDate.now(BusinessCalendar.ZONE).toString());
    }

    private long insert(Actor a,Entry e,String reason){
        long row=db.insert("INSERT INTO business_holiday(holiday_day,region_code,region_key,name,created_by,created_ms) VALUES(?,?,?,?,?,?)",
            e.day().toString(),e.region(),e.key(),e.name(),a.id(),Lifecycle.now());
        db.audit(a.id(),ADDED,KIND,row,null,e.label()+". "+reason);
        return row;
    }

    /** Valida una entrada: fecha ISO real entre 2000 y 2099, region del catalogo (vacia = nacional) y nombre. */
    static Entry entry(String day,String region,String name){
        String problem=entryProblem(day,region,name);if(problem!=null)throw new Problem(400,problem);
        return new Entry(LocalDate.parse(day),region.isEmpty()?null:region,name);
    }
    private static String entryProblem(String day,String region,String name){
        LocalDate d;
        try{if(!day.matches("\\d{4}-\\d{2}-\\d{2}"))throw new DateTimeParseException("",day,0);d=LocalDate.parse(day);}
        catch(DateTimeParseException ex){return "Fecha inválida: usa aaaa-mm-dd con un día que exista.";}
        if(d.getYear()<2000||d.getYear()>2099)return "La fecha debe estar entre 2000 y 2099.";
        if(!region.isEmpty()&&!AdultIdentity.validRegion(region))return "Región desconocida: usa el código de región del Formulario (por ejemplo 15).";
        if(name.isBlank()||name.length()>120)return "Indica el nombre del feriado (máximo 120 caracteres).";
        if(name.chars().anyMatch(Character::isISOControl))return "El nombre del feriado tiene caracteres no admitidos.";
        return null;
    }

    /** Choque con lo registrado o con las entradas previas del mismo lote; nulo si no hay. */
    private String conflict(Entry e,List<Entry> batch){
        String day=e.day().toString();
        var keys=new ArrayList<String>();
        db.rows("SELECT region_key FROM business_holiday WHERE holiday_day=?",day).forEach(r->keys.add(s(r,"region_key")));
        boolean stored=!keys.isEmpty();
        for(var b:batch)if(b.day().equals(e.day()))keys.add(b.key());
        if(keys.contains(e.key()))return day+" ya está registrado como feriado "+(e.region()==null?"nacional":"de la región "+e.region())+(stored?".":" en otra línea.");
        if(e.region()!=null&&keys.contains(BusinessCalendar.NATIONAL))return day+" ya es feriado nacional: no se registra ademas como regional.";
        if(e.region()==null&&!keys.isEmpty())return day+" ya tiene feriados regionales ("+String.join(", ",keys)+"): retíralos antes de registrarlo como nacional.";
        return null;
    }

    /** Lineas "aaaa-mm-dd Nombre [region]"; vacias y comentarios (#) se omiten. Todo valido o ningun registro. */
    List<Entry> parse(String text){
        var entries=new ArrayList<Entry>();var errors=new ArrayList<String>();boolean format=false;int number=0;
        for(String raw:text.split("\\R",-1)){
            number++;String line=raw.strip();if(line.isEmpty()||line.startsWith("#"))continue;
            var m=LINE.matcher(line);
            String problem;Entry e=null;
            if(!m.matches())problem="escribe aaaa-mm-dd y el nombre, con la región opcional al final entre corchetes.";
            else{
                problem=entryProblem(m.group(1),m.group(3)==null?"":m.group(3),m.group(2).strip());
                if(problem==null){e=new Entry(LocalDate.parse(m.group(1)),m.group(3)==null||m.group(3).isEmpty()?null:m.group(3),m.group(2).strip());}
            }
            if(problem!=null)format=true;
            else{problem=conflict(e,entries);if(problem==null)entries.add(e);}
            if(problem!=null&&errors.size()<MAX_ERRORS)errors.add("Línea "+number+": "+problem);
            else if(problem!=null&&errors.size()==MAX_ERRORS){errors.add("Hay más líneas con problemas.");}
        }
        if(!errors.isEmpty())throw new Problem(format?400:409,"No se registró ningún feriado. "+String.join(" ",errors));
        if(entries.isEmpty())throw new Problem(400,"No hay feriados que importar: escribe una línea por feriado.");
        if(entries.size()>MAX_IMPORT)throw new Problem(400,"Máximo "+MAX_IMPORT+" feriados por importación.");
        return entries;
    }

    private static Entry entryOf(Map<String,Object> r){
        return new Entry(LocalDate.parse(s(r,"holiday_day")),r.get("region_code")==null?null:s(r,"region_code"),s(r,"name"));
    }
}
