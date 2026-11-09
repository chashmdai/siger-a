package cl.nexbyte.sigera;

import java.io.InputStream;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Identificacion y contacto DECLARADOS de la persona adulta en la ficha de primera atencion (1.1). Catalogos y reglas
 * del Formulario Publico (formulario-web main bbed7ad: territorio.json, paises.json, run.ts, personalData.ts,
 * contacts.ts), los mismos que usa el frontend (frontend/src/features/attention/adult). Validar el formato o el digito
 * verificador no acredita identidad, existencia registral ni titularidad: no se consulta ClaveUnica, Registro Civil ni
 * PISEE, y nada de esto se presenta como verificado.
 */
final class AdultIdentity {
    private AdultIdentity(){}
    static final Map<String,String> REGIONS;
    static final Map<String,List<String>> COMMUNES;
    static final Set<String> COUNTRIES;
    static final String NATIONALITY_PREFIX;
    static final Set<String> DOCUMENT_KINDS=Set.of("PASAPORTE","IDENTIDAD_EXTRANJERA","OTRO");
    static{
        var json=JsonMapper.builder().build();
        JsonNode territory=read(json,"/catalog/territorio.json"),countries=read(json,"/catalog/paises.json");
        var regions=new LinkedHashMap<String,String>();territory.path("regiones").properties().forEach(e->regions.put(e.getKey(),e.getValue().asString()));
        var communes=new LinkedHashMap<String,List<String>>();
        territory.path("comunas").properties().forEach(e->{var list=new ArrayList<String>();e.getValue().forEach(n->list.add(n.asString()));communes.put(e.getKey(),List.copyOf(list));});
        var names=new HashSet<String>();countries.path("paises").properties().forEach(e->names.add(e.getValue().asString()));
        REGIONS=Collections.unmodifiableMap(regions);COMMUNES=Collections.unmodifiableMap(communes);COUNTRIES=Set.copyOf(names);
        NATIONALITY_PREFIX=countries.path("especial").asString();
        if(REGIONS.size()!=16||COUNTRIES.size()<200||NATIONALITY_PREFIX.isBlank())throw new IllegalStateException("Catalogos de la ficha adulta incompletos.");
    }
    private static JsonNode read(JsonMapper json,String resource){
        try(InputStream in=AdultIdentity.class.getResourceAsStream(resource)){return json.readTree(Objects.requireNonNull(in,resource));}
        catch(java.io.IOException e){throw new IllegalStateException("Catalogo no disponible: "+resource);}
    }

    static boolean validRegion(String code){return REGIONS.containsKey(code);}
    static boolean validCommune(String region,String commune){return COMMUNES.getOrDefault(region,List.of()).contains(commune);}
    static boolean validNationality(String value){
        if(COUNTRIES.contains(value))return true;
        if(!value.startsWith(NATIONALITY_PREFIX))return false;
        String detail=value.substring(NATIONALITY_PREFIX.length());
        return detail.length()<=120&&!detail.isBlank()&&Pattern.compile("\\p{L}").matcher(detail).find()&&!Pattern.compile("[\\x00-\\x1f\\x7f-\\x9f]").matcher(detail).find();
    }

    /** Solo validacion matematica del digito verificador. */
    static boolean validRun(String numero,String dv){
        if(!numero.matches("[1-9][0-9]{0,7}")||!dv.matches("[0-9Kk]"))return false;
        int sum=0,multiplier=2;
        for(int i=numero.length()-1;i>=0;i--){sum+=(numero.charAt(i)-'0')*multiplier;multiplier=multiplier==7?2:multiplier+1;}
        int value=11-sum%11;
        return dv.toUpperCase(Locale.ROOT).equals(value==11?"0":value==10?"K":String.valueOf(value));
    }
    static String formatRun(String numero,String dv){
        var out=new StringBuilder(numero);for(int i=out.length()-3;i>0;i-=3)out.insert(i,'.');
        return out+"-"+dv.toUpperCase(Locale.ROOT);
    }

    private static final String WORD="\\p{L}[\\p{L}\\p{M}]*";
    private static final Pattern NAME_SHAPE=Pattern.compile("^"+WORD+"(?:(?:[ '\\-]|\\. ?)"+WORD+")*\\.?$");
    /** NFC, apostrofo recto y un espacio entre palabras. */
    static String normalizeName(String value){
        return trim(Normalizer.normalize(value,Normalizer.Form.NFC).replaceAll("[\u2018\u2019`\u00b4]","'").replaceAll("(?U)\\s+"," "));
    }
    /** Espacios Unicode como trim() y \s con /u del navegador (incluye el espacio sin quiebre). */
    static String trim(String value){return value.replaceAll("^(?U)\\s+|(?U)\\s+$","");}
    /** Admite una inicial (dato parcial que entrega la persona). null si no hay problema. */
    static String nameProblem(String raw){
        String value=normalizeName(raw);
        if(value.isEmpty())return null;
        if(Pattern.compile("\\p{N}").matcher(value).find())return "no incluye numeros";
        if(Pattern.compile("[^\\p{L}\\p{M} '.\\-]").matcher(value).find())return "usa solo letras, espacios, guion, apostrofo o punto";
        if(value.length()>100)return "admite hasta 100 caracteres";
        if(!NAME_SHAPE.matcher(value).matches())return "los espacios y signos deben ir entre letras";
        return null;
    }
    static String normalizeDocument(String value){return trim(Normalizer.normalize(value,Normalizer.Form.NFC).replaceAll("(?U)\\s+"," "));}
    static String documentProblem(String raw){
        String value=normalizeDocument(raw);
        if(value.isEmpty())return null;
        if(value.length()>40)return "admite hasta 40 caracteres";
        if(Pattern.compile("[^\\p{L}\\p{N} ./\\-]").matcher(value).find())return "usa solo letras, numeros, espacios, puntos, guiones o barras";
        if(value.codePoints().filter(Character::isLetterOrDigit).count()<3)return "escribe el numero completo";
        return null;
    }
    /** Fecha real, no futura y dentro de 130 anos (hora de Chile). */
    static String birthProblem(String value,LocalDate today){
        if(value.isEmpty())return null;
        LocalDate date;
        try{if(!value.matches("\\d{4}-\\d{2}-\\d{2}"))throw new DateTimeParseException("",value,0);date=LocalDate.parse(value);}catch(DateTimeParseException e){return "no es una fecha valida";}
        if(date.isAfter(today))return "no puede ser posterior a hoy";
        if(date.isBefore(today.minusYears(130)))return "revisa el ano de nacimiento";
        return null;
    }
    static LocalDate today(){return LocalDate.now(ZoneId.of("America/Santiago"));}

    private static final Pattern EMAIL_LOCAL_FORBIDDEN=Pattern.compile("[\\s@<>()\\[\\]:;,\"\\\\\\x00-\\x1f\\x7f]",Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern EMAIL_LABEL=Pattern.compile("^[\\p{L}\\p{N}](?:[\\p{L}\\p{N}-]{0,61}[\\p{L}\\p{N}])?$");
    static boolean validEmail(String raw){
        if(raw.indexOf('\r')>=0||raw.indexOf('\n')>=0||raw.indexOf('\0')>=0)return false;
        String value=trim(raw);String[] parts=value.split("@",-1);
        if(value.length()>254||parts.length!=2)return false;
        String local=parts[0],domain=parts[1];
        if(local.isEmpty()||local.length()>64||local.startsWith(".")||local.endsWith(".")||local.contains("..")||EMAIL_LOCAL_FORBIDDEN.matcher(local).find())return false;
        String[] labels=domain.split("\\.",-1);
        return labels.length>=2&&Arrays.stream(labels).allMatch(l->EMAIL_LABEL.matcher(l).matches());
    }
    /** Celular chileno normalizado: +569 y ocho digitos. */
    static boolean validMobile(String value){return value.matches("\\+569[0-9]{8}");}
}
