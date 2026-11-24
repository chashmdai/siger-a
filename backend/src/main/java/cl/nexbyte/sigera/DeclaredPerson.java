package cl.nexbyte.sigera;

import java.util.*;
import java.util.function.Predicate;
import static cl.nexbyte.sigera.Db.*;

/**
 * Persona declarada canonica (0.8.0, D33, gate E03; esquema persona-1 de DISENO 2.4) y reglas compartidas de la
 * identificacion declarada: la persona adulta de nivel superior de la ficha 1.1 y 1.2 (claves presenter_*), las
 * personas relacionadas de la ficha 1.2 y la ficha de ingreso de la COM manual. Las reglas son las de la ficha 1.1
 * (catalogos y validaciones de AdultIdentity, iguales al Formulario Publico), extraidas sin cambiar su comportamiento.
 *
 * Validar formato o digito verificador no acredita identidad. Nada se inventa: vacio nunca es error, un valor igual al
 * de la version anterior se conserva sin revalidarse, los campos historicos (legacy_*) nunca se crean ni se reescriben
 * y un nombre historico no se divide. Los codigos de relacion y de tipo de institucion se validan solo contra el
 * catalogo estadistico VIGENTE (sin valores fijos en el codigo); el servidor deriva la version con que se registraron
 * (relationship_version, type_version) y el cliente no puede enviarla. Tambien calcula el resumen de cambios entre
 * versiones (COMPLEMENTO: de vacio a valor; RECTIFICACION: de un valor a otro, incluido quitarlo; DISENO 2.5).
 */
final class DeclaredPerson {
    private DeclaredPerson(){}
    /** persona-1 (DISENO 2.4) con legacy_document y legacy_nationality (diferencia de referencia aprobada en E03). */
    static final List<String> FIELDS=List.of("role","first_name","middle_name","last_name","second_last_name","legacy_name",
        "identity_type","run_number","run_dv","document_kind","document_number","legacy_document","nationality","legacy_nationality",
        "birth_date","age_years","legacy_age","region_code","commune","address","email","mobile","other_contact","preferred_name",
        "treatment","relationship_code","relationship_text","schooling","location_text");
    static final List<String> NAME_PARTS=List.of("first_name","middle_name","last_name","second_last_name");
    static final List<String> LEGACY=List.of("legacy_name","legacy_document","legacy_nationality","legacy_age");
    static final List<String> IDENTITY_TYPES=List.of("RUN","DOCUMENTO_EXTRANJERO","NO_DISPONIBLE");
    /** Trato del Formulario (adult-treatment, nna-treatment; formulario-web 6f886eb). */
    static final List<String> TREATMENTS=List.of("ella","el","elle","nombre","otra","noresponde");
    static final List<String> INSTITUTION_FIELDS=List.of("name","type_code","role","region_code","commune");
    static final String RELATIONSHIP="RELACION",INSTITUTION_TYPE="TIPO_INSTITUCION";
    static final int MAX_INSTITUTIONS=20;
    /** Equivalencias de una persona 1.0/1.1 (texto libre) con persona-1: el texto se conserva como historico. */
    static final Map<String,String> FROM_11=Map.of("name","legacy_name","age","legacy_age","identity_document","legacy_document",
        "nationality","legacy_nationality","relationship","relationship_text","location","location_text");

    /** Claves de un contenedor: persona-1 o la persona adulta de nivel superior de la ficha (presenter_*). */
    record Keys(String first,String middle,String last,String secondLast,String runNumber,String runDv,String birth,String docKind,
                String docNumber,String type,String runType,String docType,String email,String mobile,String region,String commune,String nationality){
        List<String> names(){return List.of(first,middle,last,secondLast);}
    }
    static final Keys CANONICAL=new Keys("first_name","middle_name","last_name","second_last_name","run_number","run_dv","birth_date",
        "document_kind","document_number","identity_type","RUN","DOCUMENTO_EXTRANJERO","email","mobile","region_code","commune","nationality");
    static final Keys FICHA=new Keys("presenter_first_name","presenter_middle_name","presenter_last_name","presenter_second_last_name",
        "presenter_run_number","presenter_run_dv","presenter_birth_date","presenter_document_kind","presenter_document_number","identity_type",
        "RUN","OTRO","contact_email","contact_phone","region","commune","nationality");

    // --- Reglas de identificacion y contacto (las de la ficha 1.1) ------------------------------
    /** Nombres, nacimiento, RUN y documento extranjero; normaliza en out y agrega los problemas a errors. */
    static void identity(Map<String,Object> out,Keys k,Predicate<String> kept,List<String> errors,String prefix){
        Map<String,String> labels=Map.of(k.first(),"Primer nombre",k.middle(),"Segundo nombre",k.last(),"Primer apellido",k.secondLast(),"Segundo apellido");
        for(String key:k.names()){
            out.put(key,AdultIdentity.normalizeName(s(out,key)));String p=AdultIdentity.nameProblem(s(out,key));
            check(kept,errors,key,p==null?null:prefix+labels.get(key)+": "+p+".");
        }
        String birth=AdultIdentity.birthProblem(s(out,k.birth()),AdultIdentity.today());check(kept,errors,k.birth(),birth==null?null:prefix+"Fecha de nacimiento: "+birth+".");
        String type=s(out,k.type()),number=s(out,k.runNumber()).replaceAll("[.\\s]",""),dv=s(out,k.runDv()).toUpperCase(Locale.ROOT);
        out.put(k.runNumber(),number);out.put(k.runDv(),dv);
        if(!number.isEmpty()||!dv.isEmpty()){
            if(!type.equals(k.runType()))errors.add(prefix+"El RUN corresponde a la identificacion RUN chileno.");
            else if(number.isEmpty()||dv.isEmpty())errors.add(prefix+"Completa el numero y el digito verificador del RUN, o deja el RUN en blanco.");
            else if(!AdultIdentity.validRun(number,dv))errors.add(prefix+"El numero y el digito verificador del RUN no coinciden.");
        }
        String kind=s(out,k.docKind());out.put(k.docNumber(),AdultIdentity.normalizeDocument(s(out,k.docNumber())));
        if(!kind.isEmpty()||!s(out,k.docNumber()).isEmpty()){
            if(!type.equals(k.docType()))errors.add(prefix+"El documento extranjero corresponde a la identificacion Documento extranjero u otro.");
            if(!kind.isEmpty()&&!AdultIdentity.DOCUMENT_KINDS.contains(kind))errors.add(prefix+"Tipo de documento no permitido.");
            String doc=AdultIdentity.documentProblem(s(out,k.docNumber()));check(kept,errors,k.docNumber(),doc==null?null:prefix+"Numero de documento: "+doc+".");
        }
    }
    /** Celular, correo, region, comuna y nacionalidad con los catalogos y reglas del Formulario. */
    static void contact(Map<String,Object> out,Keys k,Predicate<String> kept,List<String> errors,String prefix){
        out.put(k.email(),AdultIdentity.trim(s(out,k.email())));
        String phone=s(out,k.mobile()),email=s(out,k.email()),region=s(out,k.region()),commune=s(out,k.commune()),nationality=s(out,k.nationality());
        if(!phone.isEmpty()&&!AdultIdentity.validMobile(phone))check(kept,errors,k.mobile(),prefix+"Celular: escribe los ocho digitos despues de +56 9.");
        if(!email.isEmpty()&&!AdultIdentity.validEmail(email))check(kept,errors,k.email(),prefix+"Correo: revisa el formato.");
        if(!region.isEmpty()&&!AdultIdentity.validRegion(region))check(kept,errors,k.region(),prefix+"Region: selecciona una region del listado.");
        if(!commune.isEmpty()&&!(AdultIdentity.validRegion(region)&&AdultIdentity.validCommune(region,commune)))check(kept,errors,k.commune(),prefix+"Comuna: selecciona una comuna de la region indicada.");
        if(!nationality.isEmpty()&&!AdultIdentity.validNationality(nationality))check(kept,errors,k.nationality(),prefix+"Nacionalidad: selecciona un pais o la opcion Otra / situacion especial.");
    }
    private static void check(Predicate<String> kept,List<String> errors,String key,String problem){if(problem!=null&&!kept.test(key))errors.add(problem);}
    /** Nombre para mostrar: las partes unidas (vacio si no hay partes). */
    static String displayName(Map<String,Object> p,Keys k){
        return String.join(" ",k.names().stream().map(x->s(p,x)).filter(x->!x.isEmpty()).toList());
    }
    /** Documento resumido: RUN con formato o tipo y numero del documento extranjero (vacio si no hay). */
    static String documentSummary(Map<String,Object> p,Keys k){
        String type=s(p,k.type()),number=s(p,k.runNumber()),dv=s(p,k.runDv()),doc=s(p,k.docNumber());
        if(type.equals(k.runType())&&!number.isEmpty()&&!dv.isEmpty())return AdultIdentity.formatRun(number,dv);
        if(type.equals(k.docType())&&!doc.isEmpty())return documentLabel(s(p,k.docKind()))+" "+doc;
        return "";
    }
    static String documentLabel(String kind){return switch(kind){case "PASAPORTE"->"Pasaporte";case "IDENTIDAD_EXTRANJERA"->"Documento de identidad extranjero";case "OTRO"->"Otro documento";default->"Documento";};}

    // --- Catalogos estadisticos vigentes ---------------------------------------------------------
    /** Version VIGENTE y codigos seleccionables de RELACION y TIPO_INSTITUCION (sin vigente: no se admite el codigo). */
    record Codes(Map<String,Long> versions,Map<String,Set<String>> selectable){
        static Codes load(Db db){
            var versions=new HashMap<String,Long>();var selectable=new HashMap<String,Set<String>>();
            for(String catalog:List.of(RELATIONSHIP,INSTITUTION_TYPE))
                for(var v:db.rows("SELECT id FROM stat_catalog_version WHERE `current_catalog`=?",catalog)){
                    long id=n(v,"id");versions.put(catalog,id);
                    var codes=new HashSet<String>();
                    for(var i:db.rows("SELECT code FROM stat_catalog_item WHERE version_id=? AND selectable=TRUE",id))codes.add(s(i,"code"));
                    selectable.put(catalog,codes);
                }
            return new Codes(versions,selectable);
        }
    }
    /**
     * Codigo de catalogo (relacion o tipo de institucion): vacio = sin codigo; igual al de la version anterior = se
     * conserva con su version aunque el catalogo haya cambiado; si no, debe ser un item seleccionable de la VIGENTE.
     */
    static void code(Map<String,Object> out,String codeKey,String versionKey,String catalog,List<Map<String,Object>> previous,Codes codes,List<String> errors,String label){
        String code=s(out,codeKey);
        if(code.isEmpty()){out.put(versionKey,"");return;}
        for(var p:previous)if(code.equals(s(p,codeKey))&&!s(p,versionKey).isEmpty()){out.put(versionKey,p.get(versionKey));return;}
        Long version=codes.versions().get(catalog);
        if(version==null)errors.add(label+": no hay un listado vigente; registra el dato como texto.");
        else if(!codes.selectable().getOrDefault(catalog,Set.of()).contains(code))errors.add(label+": selecciona una opcion del listado vigente.");
        else{out.put(versionKey,version);return;}
        out.put(versionKey,"");
    }

    // --- Persona canonica -----------------------------------------------------------------------
    /** Vista canonica de una persona guardada: persona-1 tal cual; 1.0/1.1 con su texto libre como historico. */
    static Map<String,Object> canonical(Map<?,?> raw){
        var p=new LinkedHashMap<String,Object>();raw.forEach((k,v)->p.put(k.toString(),v));
        if(!p.containsKey("name"))return p;
        var out=new LinkedHashMap<String,Object>();
        p.forEach((k,v)->out.put(FROM_11.getOrDefault(k,k),v));
        return out;
    }
    static List<Map<String,Object>> canonicalList(Object raw){
        var out=new ArrayList<Map<String,Object>>();
        if(raw instanceof List<?> list)for(Object o:list)if(o instanceof Map<?,?> m)out.add(canonical(m));
        return out;
    }
    private static String age(Object value,String label){
        if(value==null)return "";
        if(value instanceof Integer||value instanceof Long)return String.valueOf(value);
        if(!(value instanceof String text))throw new Problem(400,label+"Edad: escribe la edad en anos cumplidos.");
        return text.strip();
    }
    /**
     * Normaliza y valida una persona-1. previous = personas de la version anterior en vista canonica (para conservar
     * valores historicos identicos). requireDatum exige algun dato ademas del rol (ingreso de la COM manual).
     */
    static Map<String,Object> person(Object raw,Set<String> roles,List<Map<String,Object>> previous,Codes codes,String label,boolean requireDatum,List<String> errors){
        if(!(raw instanceof Map<?,?> source))throw new Problem(400,"Persona relacionada invalida.");
        var p=new LinkedHashMap<String,Object>();source.forEach((k,v)->p.put(k.toString(),v));
        if(!FIELDS.containsAll(p.keySet()))throw new Problem(400,"Datos de persona no reconocidos.");
        String prefix=label+": ";
        String ageText=age(p.remove("age_years"),prefix);
        var in=new Input(p);var out=new LinkedHashMap<String,Object>();
        out.put("role",in.choice("role",roles.toArray(String[]::new)));
        for(String key:FIELDS){
            if(key.equals("role"))continue;
            if(key.equals("age_years")){out.put(key,ageText);continue;}
            int limit=switch(key){
                case "first_name","middle_name","last_name","second_last_name"->100;case "run_number"->12;case "run_dv"->1;case "birth_date"->10;
                case "document_number"->40;case "identity_type","document_kind","relationship_code","commune"->100;case "region_code"->4;case "treatment"->20;
                case "nationality","email"->200;case "mobile"->80;case "other_contact"->300;default->500;};
            out.put(key,in.text(key,limit,false));
        }
        Predicate<String> kept=key->!s(out,key).isEmpty()&&previous.stream().anyMatch(q->s(out,key).equals(s(q,key)));
        String type=s(out,"identity_type");if(!type.isEmpty()&&!IDENTITY_TYPES.contains(type))errors.add(prefix+"Tipo de identificacion no permitido.");
        String treatment=s(out,"treatment");if(!treatment.isEmpty()&&!TREATMENTS.contains(treatment))errors.add(prefix+"Trato: selecciona una opcion del listado.");
        identity(out,CANONICAL,kept,errors,prefix);
        contact(out,CANONICAL,kept,errors,prefix);
        if(!ageText.isEmpty()&&!kept.test("age_years")){
            int max=s(out,"role").equals("NNA")?17:130;
            if(!ageText.matches("[0-9]{1,3}")||Integer.parseInt(ageText)>max)errors.add(prefix+"Edad: escribe un numero entero entre 0 y "+max+".");
            else out.put("age_years",String.valueOf(Integer.parseInt(ageText)));
        }
        for(String key:LEGACY)if(!s(out,key).isEmpty()&&!kept.test(key))errors.add(prefix+"los datos historicos no se crean ni se modifican.");
        code(out,"relationship_code","relationship_version",RELATIONSHIP,previous,codes,errors,prefix+"Relacion");
        if(requireDatum&&FIELDS.stream().filter(k->!k.equals("role")).allMatch(k->s(out,k).isEmpty()))
            errors.add(prefix+"registra algun antecedente disponible de la persona, sin inventar identidad.");
        return out;
    }
    /** Persona sin datos (solo rol o nada): se omite, por ejemplo, el presentador de un ingreso institucional. */
    static boolean blank(Object raw){
        if(raw==null)return true;
        if(!(raw instanceof Map<?,?> m))return false;
        return m.entrySet().stream().allMatch(e->e.getKey().toString().equals("role")||e.getValue()==null||e.getValue().toString().isBlank());
    }
    /** Nombre para mostrar de una persona canonica: partes o nombre historico. */
    static String name(Map<String,Object> p){String n=displayName(p,CANONICAL);return n.isEmpty()?s(p,"legacy_name"):n;}

    // --- Hechos e instituciones -----------------------------------------------------------------
    /** Region, comuna y lugar de los hechos (catalogo del Formulario; un valor anterior identico se conserva). */
    static void facts(Input in,Map<String,Object> out,Map<String,Object> previous,List<String> errors){
        out.put("facts_region_code",in.text("facts_region_code",4,false));out.put("facts_commune",in.text("facts_commune",100,false));out.put("facts_place",in.text("facts_place",500,false));
        Predicate<String> kept=key->!s(out,key).isEmpty()&&s(out,key).equals(s(previous,key));
        String region=s(out,"facts_region_code"),commune=s(out,"facts_commune");
        if(!region.isEmpty()&&!AdultIdentity.validRegion(region))check(kept,errors,"facts_region_code","Region de los hechos: selecciona una region del listado.");
        if(!commune.isEmpty()&&!(AdultIdentity.validRegion(region)&&AdultIdentity.validCommune(region,commune)))check(kept,errors,"facts_commune","Comuna de los hechos: selecciona una comuna de la region indicada.");
    }
    /** Instituciones (nombre, tipo del catalogo vigente, rol o vinculo, region y comuna); type_version la deriva el servidor. */
    static List<Map<String,Object>> institutions(Object raw,Object previousRaw,Codes codes,List<String> errors){
        if(raw==null)return List.of();
        if(!(raw instanceof List<?> list)||list.size()>MAX_INSTITUTIONS)throw new Problem(400,"Se admiten hasta "+MAX_INSTITUTIONS+" instituciones.");
        var previous=canonicalList(previousRaw);var out=new ArrayList<Map<String,Object>>();int i=0;
        for(Object item:list){
            i++;
            if(!(item instanceof Map<?,?> source))throw new Problem(400,"Institucion invalida.");
            var p=new LinkedHashMap<String,Object>();source.forEach((k,v)->p.put(k.toString(),v));
            if(!INSTITUTION_FIELDS.containsAll(p.keySet()))throw new Problem(400,"Datos de institucion no reconocidos.");
            var in=new Input(p);var row=new LinkedHashMap<String,Object>();String prefix="Institucion "+i+": ";
            row.put("name",in.text("name",200,false));row.put("type_code",in.text("type_code",40,false));row.put("role",in.text("role",300,false));
            row.put("region_code",in.text("region_code",4,false));row.put("commune",in.text("commune",100,false));
            if(s(row,"name").isEmpty())errors.add(prefix+"indica el nombre.");
            Predicate<String> kept=key->!s(row,key).isEmpty()&&previous.stream().anyMatch(q->s(row,key).equals(s(q,key)));
            String region=s(row,"region_code"),commune=s(row,"commune");
            if(!region.isEmpty()&&!AdultIdentity.validRegion(region))check(kept,errors,"region_code",prefix+"Region: selecciona una region del listado.");
            if(!commune.isEmpty()&&!(AdultIdentity.validRegion(region)&&AdultIdentity.validCommune(region,commune)))check(kept,errors,"commune",prefix+"Comuna: selecciona una comuna de la region indicada.");
            code(row,"type_code","type_version",INSTITUTION_TYPE,previous,codes,errors,prefix+"Tipo");
            out.add(row);
        }
        return out;
    }

    // --- Resumen de cambios (DISENO 2.5) ---------------------------------------------------------
    /**
     * Campos complementados (de vacio a valor) y rectificados (de un valor a otro o a vacio) entre dos versiones, por
     * ruta (people[0].first_name). Las personas se comparan en su vista canonica (pasar de 1.1 a 1.2 no es un cambio);
     * no cuentan schema_version, las versiones de catalogo derivadas ni las claves de derived. null si no hay anterior.
     */
    static Map<String,List<String>> changes(Map<String,Object> previous,Map<String,Object> next,Set<String> derived){
        if(previous==null)return null;
        var before=new TreeMap<String,String>();var after=new TreeMap<String,String>();
        flatten("",view(previous),before);flatten("",view(next),after);
        var complement=new TreeSet<String>();var rectify=new TreeSet<String>();
        var paths=new TreeSet<String>();paths.addAll(before.keySet());paths.addAll(after.keySet());
        for(String path:paths){
            if(derived.contains(path))continue;
            String a=before.getOrDefault(path,""),b=after.getOrDefault(path,"");
            if(a.equals(b))continue;
            (a.isEmpty()?complement:rectify).add(path);
        }
        var out=new LinkedHashMap<String,List<String>>();out.put("COMPLEMENTO",List.copyOf(complement));out.put("RECTIFICACION",List.copyOf(rectify));
        return out;
    }
    static String summary(Map<String,List<String>> changes){
        return changes==null?"":"Campos complementados: "+changes.get("COMPLEMENTO").size()+"; rectificados: "+changes.get("RECTIFICACION").size()+".";
    }
    private static Map<String,Object> view(Map<String,Object> data){
        var out=new LinkedHashMap<String,Object>(data);
        for(String key:List.of("people","persons"))if(out.get(key) instanceof List<?>)out.put(key,canonicalList(out.get(key)));
        return out;
    }
    private static void flatten(String path,Object value,Map<String,String> out){
        if(value instanceof Map<?,?> map){
            for(var e:map.entrySet()){
                String key=e.getKey().toString();
                if(key.equals("schema_version")||key.endsWith("_version"))continue;
                flatten(path.isEmpty()?key:path+"."+key,e.getValue(),out);
            }
        }else if(value instanceof List<?> list){
            for(int i=0;i<list.size();i++)flatten(path+"["+i+"]",list.get(i),out);
        }else{
            String text=value==null?"":String.valueOf(value).strip();
            if(!text.isEmpty())out.put(path,text);
        }
    }
}
