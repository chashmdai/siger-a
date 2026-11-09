package cl.nexbyte.sigera;

import java.time.Instant;
import java.util.Map;

/** Validacion comun; ningun comando acepta estado, autor ni rol efectivos del navegador. */
public record Input(Map<String,Object> values) {
    public Input { values = values == null ? Map.of() : values; }
    public String text(String key, int max, boolean required) {
        Object value=values.get(key);
        if (value != null && !(value instanceof String)) throw new Problem(400,"Campo invalido: "+key);
        String s=value==null?"":value.toString().strip();
        if (s.length()>max || required && s.isEmpty() || s.indexOf(0)>=0) throw new Problem(400,"Revisa el campo "+key+" (maximo "+max+" caracteres).");
        return s;
    }
    public long id(String key) {
        Long n=optionalId(key); if(n==null)throw new Problem(400,"Falta "+key);return n;
    }
    public Long optionalId(String key) {
        Object v=values.get(key);if(v==null||v.toString().isBlank())return null;
        try { long n=Long.parseLong(v.toString());if(n<=0)throw new NumberFormatException();return n; }
        catch(NumberFormatException e){throw new Problem(400,"Identificador invalido: "+key);}
    }
    public boolean flag(String key) { return Boolean.TRUE.equals(values.get(key)); }
    public long instant(String key, boolean required) {
        String value=text(key,40,required);if(value.isEmpty())return 0;
        try{return Instant.parse(value).toEpochMilli();}catch(Exception e){throw new Problem(400,"Fecha invalida: "+key);}
    }
    public String choice(String key, String... choices) {
        String value=text(key,40,true);
        for(String c:choices)if(c.equals(value))return value;
        throw new Problem(400,"Opcion invalida: "+key);
    }
    public String reason() { return text("reason",12000,true); }
}
