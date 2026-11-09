package cl.nexbyte.sigera;

import java.time.*;
import java.util.*;
import tools.jackson.databind.JsonNode;

/**
 * Proyeccion normalizada de un original externo ya autenticado y validado (Formulario completo 1.0/1.1).
 * Es de solo lectura sobre el documento: nunca lo modifica. Se calcula una vez, al conservarlo.
 *
 * Fuentes de las reglas (Formulario a84d2e7 y SMR 02d4184):
 * - IdentificacionCiudadana: roles, atributos, procedencias (CLAVE_UNICA_USERINFO autentica; SRCEI contrasta;
 *   DECLARACION_CIUDADANA declara; SIN_DATO), agregados nombres/apellidos que nunca se dividen y valores utiles.
 * - XmlSrcei: diccionarios de sexo, estado civil y nacionalidad; "U" y codigos desconocidos = Desconocido;
 *   fechas validas aaaa-mm-dd y fechas truncadas (codigo TRUNCADA), que no permiten calcular edad.
 * - DeclaracionNna: RUN [1-9][0-9]{0,7} y digito verificador por modulo 11.
 * - ValidacionAdultos / campos NNA / CamposFormulario de SMR: identificadores y opciones de las respuestas.
 * Los valores proyectados no llevan textos de procedencia: la procedencia viaja en columnas propias.
 */
record ExternalProjection(Com com,List<Person> persons,List<Field> fields) {
    static final ZoneId ZONE=ZoneId.of("America/Santiago");
    record Com(String presenter,String identityDocument,String contact,String contactEmail,String contactPhone,
               String contactRestrictions,String presenterDomicile,String subject,String body){}
    record Person(String key,int index,String role,boolean authenticated,String givenNames,String givenSource,String surnames,String surnameSource,
                  String runNumber,String runDv,String runSource,Boolean runValid,String identificationType,String foreignType,String foreignNumber,
                  String birthDate,String birthSource,Integer ageAtIntake,Integer declaredAge,String registryState,Long registryCheckedMs,List<Attribute> attributes){}
    record Attribute(String key,boolean discrepancy,String value,String code,String meaning,String source,String level,long observedMs,String evidence){}
    record Field(String group,String personKey,int item,String key,String value){}

    static final Map<String,String> SEX=Map.of("F","Femenino","M","Masculino","I","Indeterminado");
    static final Map<String,String> CIVIL=Map.of("S","Soltero","C","Casado","V","Viudo","D","Divorciado","P","Conviviente civil","J","Separado judicialmente");
    static final Map<String,String> NATIONALITY=Map.of("C","Chilena","E","Extranjera","N","Nacionalizado");
    static final Map<String,String> REGIONS=Map.ofEntries(Map.entry("15","Arica y Parinacota"),Map.entry("01","Tarapacá"),Map.entry("02","Antofagasta"),
        Map.entry("03","Atacama"),Map.entry("04","Coquimbo"),Map.entry("05","Valparaíso"),Map.entry("13","Metropolitana de Santiago"),
        Map.entry("06","Libertador General Bernardo O'Higgins"),Map.entry("07","Maule"),Map.entry("16","Ñuble"),Map.entry("08","Biobío"),
        Map.entry("09","La Araucanía"),Map.entry("14","Los Ríos"),Map.entry("10","Los Lagos"),Map.entry("11","Aysén del General Carlos Ibáñez del Campo"),
        Map.entry("12","Magallanes y de la Antártica Chilena"));
    private static final Map<String,String> MOTIVE=Map.of("consulta","Consulta","vulneracion","Vulneración de derechos","promocion","Promoción de derechos");
    private static final Map<String,String> TOPIC=Map.of("general","Consulta general","entrevista","Solicitud de entrevista","estadisticas","Solicitud de estadísticas");
    private static final Map<String,String> METHOD=Map.of("celular","Celular","email","Correo electrónico","social","Red social","unsafe","No tiene una forma segura de contacto");
    private static final Map<String,String> SAFE=Map.of("yes","Sí","unsure","No está seguro o segura","no","Necesita acordar otra forma");
    /**
     * Relato de hechos: "relato" (vulneracion, promocion) y "nna-story". Los textos de consulta general, motivo de
     * entrevista y descripcion de solicitud estadistica son solicitudes, no relatos: quedan en las respuestas con su rotulo.
     */
    private static final List<String> NARRATIVE=List.of("relato","nna-story");

    static ExternalProjection of(JsonNode comunicacion,Instant received){
        LocalDate intake=received.atZone(ZONE).toLocalDate();
        var persons=new ArrayList<Person>();
        int index=0;
        for(var p:comunicacion.path("personas"))persons.add(person(p,index++,intake));
        var fields=new ArrayList<Field>();
        var campos=new LinkedHashMap<String,String>();
        comunicacion.path("datos").path("campos").properties().forEach(e->{campos.put(e.getKey(),e.getValue().asString());fields.add(new Field("CAMPO",null,0,e.getKey(),e.getValue().asString()));});
        int item=0;
        for(var d:comunicacion.path("datos").path("personas")){
            String key=d.path("personaId").asString();int i=item++;
            d.path("detalles").properties().forEach(e->fields.add(new Field("PERSONA",key,i,e.getKey(),e.getValue().asString())));
        }
        item=0;
        for(var inst:comunicacion.path("datos").path("instituciones")){
            int i=item++;
            inst.properties().forEach(e->fields.add(new Field("INSTITUCION",null,i,e.getKey(),e.getValue().isNull()?null:e.getValue().asString())));
        }
        var presenter=persons.stream().filter(p->"PRESENTADOR".equals(p.role())).findFirst().orElseThrow();
        boolean nna="NNA".equals(comunicacion.path("tipo").asString());
        // aceptaCanalDigital es una propiedad del recorrido (falso en el flujo NNA), no una preferencia de contacto:
        // no se proyecta como restriccion. Las preferencias reales son los medios declarados.
        return new ExternalProjection(com(presenter,campos,nna),List.copyOf(persons),List.copyOf(fields));
    }

    // --- Personas -------------------------------------------------------------------------
    private static Person person(JsonNode p,int index,LocalDate intake){
        var attrs=p.path("atributos");
        var attributes=new ArrayList<Attribute>();
        attrs.properties().forEach(e->attributes.add(attribute(e.getKey(),e.getValue(),false)));
        p.path("discrepancias").properties().forEach(e->attributes.add(attribute(e.getKey(),e.getValue(),true)));

        String[] given=names(attrs,"nombres","primerNombre","segundoNombre"),last=names(attrs,"apellidos","primerApellido","segundoApellido");

        String number=null,dv=null,runSource=null;Boolean valid=null;
        String n=usable(attrs.path("runNumero")),d=usable(attrs.path("runDv"));
        if(n!=null&&d!=null&&attrs.path("runNumero").path("fuente").asString().equals(attrs.path("runDv").path("fuente").asString())){
            String nn=runNumber(n),dd=d.strip().toUpperCase(Locale.ROOT);
            if(nn!=null&&dd.matches("[0-9K]")&&consistent(p.path("runNumero"),nn)&&consistent(p.path("runDv"),dd)){
                number=nn;dv=dd;runSource=attrs.path("runNumero").path("fuente").asString();valid=modulo11(nn,dd);
            }
        }

        String birth=null,birthSource=null;Integer age=null;
        String rawBirth=usable(attrs.path("fechaNacimiento"));
        LocalDate born=date(rawBirth);
        if(born!=null&&!born.isAfter(intake)){birth=rawBirth;birthSource=attrs.path("fechaNacimiento").path("fuente").asString();age=age(born,intake);}
        String declared=usable(attrs.path("edad"));
        Integer declaredAge=declared!=null&&declared.matches("[0-9]{1,3}")?Integer.valueOf(declared):null;

        Long checked=null;
        try{if(p.path("consultadaEn").isString())checked=Instant.parse(p.path("consultadaEn").asString()).toEpochMilli();}catch(DateTimeException ignored){/* sin fecha valida no se informa */}
        return new Person(p.path("id").asString(),index,p.path("rol").asString(),p.path("autenticada").asBoolean(),given[0],given[1],last[0],last[1],
            // Columnas acotadas: si el valor no cabe queda vacio aqui; el atributo integro esta en external_attribute.
            number,dv,runSource,valid,exact(usable(attrs.path("tipoIdentificacion")),40),exact(usable(attrs.path("tipoDocumento")),255),exact(usable(attrs.path("numeroDocumento")),255),
            birth,birthSource,age,declaredAge,p.path("estadoConsulta").isString()?exact(p.path("estadoConsulta").asString(),24):null,checked,List.copyOf(attributes));
    }
    /** Agregado tal cual si sirve; si no, componentes utiles en orden unidos por espacio. Devuelve {valor, fuente|MIXTA}. */
    private static String[] names(JsonNode attrs,String aggregate,String... components){
        String whole=usable(attrs.path(aggregate));
        if(whole!=null)return new String[]{whole,attrs.path(aggregate).path("fuente").asString()};
        var values=new ArrayList<String>();var sources=new LinkedHashSet<String>();
        for(String c:components){String v=usable(attrs.path(c));if(v!=null){values.add(v);sources.add(attrs.path(c).path("fuente").asString());}}
        if(values.isEmpty())return new String[]{null,null};
        return new String[]{String.join(" ",values),sources.size()==1?sources.getFirst():"MIXTA"};
    }
    private static Attribute attribute(String key,JsonNode a,boolean discrepancy){
        String value=a.path("valor").isString()?a.path("valor").asString():null;
        String code=a.path("codigo").isString()?a.path("codigo").asString():null;
        String source=a.path("fuente").asString();
        return new Attribute(key,discrepancy,value,code,meaning(key,value,code,source),source,a.path("nivel").asString(),
            Instant.parse(a.path("observadoEn").asString()).toEpochMilli(),a.path("evidencia").asString());
    }
    /**
     * Lectura del diccionario SRCeI; el valor y el codigo originales se conservan aparte, sin cambios.
     * "U" es sin informacion; un codigo fuera del diccionario es no reconocido (no se asume que signifique U).
     * Sin codigo y sin valor declarado codificado, no se interpreta.
     */
    static String meaning(String key,String value,String code,String source){
        if(key.endsWith("Truncada")&&"TRUNCADA".equals(code))return "Fecha incompleta";
        var dictionary=switch(key){case "sexo"->SEX;case "estadoCivil"->CIVIL;case "nacionalidad"->NATIONALITY;default->null;};
        if(dictionary==null)return null;
        String c=code!=null&&!code.isBlank()?code.strip():"sexo".equals(key)&&"DECLARACION_CIUDADANA".equals(source)&&value!=null?value.strip():null;
        if(c==null)return null;
        if("U".equals(c))return "Sin información";
        return dictionary.getOrDefault(c,"Código no reconocido");
    }
    /** Valor util segun el Formulario: texto no vacio, distinto de "Desconocido", sin codigo U y con fuente informada. */
    static String usable(JsonNode a){
        if(!a.isObject()||!a.path("valor").isString()||"SIN_DATO".equals(a.path("fuente").asString()))return null;
        String v=a.path("valor").asString();
        if(v.isBlank()||"Desconocido".equals(v)||"U".equals(a.path("codigo").isString()?a.path("codigo").asString():null))return null;
        return v;
    }
    /** RUN sin puntos, espacios ni ceros a la izquierda; null si no es un numero de RUN. */
    static String runNumber(String raw){
        String digits=raw.replace(".","").replace(" ","").replaceFirst("^0+","");
        return digits.matches("[1-9][0-9]{0,8}")?digits:null;
    }
    static boolean modulo11(String number,String dv){
        int sum=0,m=2;
        for(int i=number.length()-1;i>=0;i--){sum+=(number.charAt(i)-'0')*m;m=m==7?2:m+1;}
        int value=11-sum%11;
        return (value==11?"0":value==10?"K":Integer.toString(value)).equals(dv);
    }
    private static boolean consistent(JsonNode top,String normalized){
        if(top.isNull()||top.isMissingNode())return true;
        if(!top.isString())return false;
        String t=top.asString().strip().toUpperCase(Locale.ROOT);
        return normalized.equals(t.length()==1?t:runNumber(t));
    }
    /** Fecha completa aaaa-mm-dd valida; cualquier otra forma (incluida la truncada) no es una fecha. */
    static LocalDate date(String value){
        if(value==null||!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))return null;
        try{return LocalDate.parse(value);}catch(DateTimeException e){return null;}
    }
    /**
     * Anos cumplidos en la fecha dada. Period.between: el cumpleanos del dia ya cuenta; quien nacio un 29 de
     * febrero cumple el 1 de marzo en anos no bisiestos (el 28 de febrero aun no).
     */
    static int age(LocalDate born,LocalDate on){return Period.between(born,on).getYears();}

    // --- COM ------------------------------------------------------------------------------
    private static Com com(Person p,Map<String,String> c,boolean nna){
        String name=join(" ",p.givenNames(),p.surnames());
        String identity=p.runNumber()!=null&&Boolean.TRUE.equals(p.runValid())?p.runNumber()+"-"+p.runDv()
            :p.foreignNumber()!=null?join(" ",p.foreignType(),p.foreignNumber()):null;
        // NNA: correo y celular del medio elegido en cada preferencia (principal antes que alternativo).
        String email=nna?byMethod(c,"email"):value(c,"email"),phone=nna?byMethod(c,"celular"):value(c,"telefono");

        var contact=new ArrayList<String>();
        for(String slot:List.of("primary","secondary")){
            String method=value(c,"nna-"+slot+"-method");if(method==null)continue;
            String detail=switch(method){
                case "celular"->value(c,"nna-"+slot+"-celular");
                case "email"->value(c,"nna-"+slot+"-email");
                case "social"->join(" ",first(c,"nna-"+slot+"-other-network","nna-"+slot+"-network"),value(c,"nna-"+slot+"-handle"));
                default->null;
            };
            contact.add(("primary".equals(slot)?"Contacto principal: ":"Contacto alternativo: ")+join(" ",METHOD.getOrDefault(method,method),detail));
        }
        var restrictions=new ArrayList<String>();
        // Solo el medio principal admite "unsafe" (ValidacionFormulario); un alternativo informado sigue siendo valido.
        if("unsafe".equals(value(c,"nna-primary-method")))restrictions.add(value(c,"nna-secondary-method")==null
            ?"No tiene una forma segura de contacto."
            :"Contacto principal: sin forma segura de contacto; existe un contacto alternativo informado.");
        // nna-safe solo se pregunta cuando el medio principal no es "unsafe": califica al contacto principal.
        String safe=value(c,"nna-safe");if(safe!=null)restrictions.add("Seguridad del contacto principal: "+SAFE.getOrDefault(safe,safe)+".");
        String care=value(c,"nna-contact-instructions");if(care!=null)restrictions.add("Precauciones para el contacto: "+care);

        String domicile=nna?join(", ",value(c,"nna-commune"),region(value(c,"nna-region")))
            :join(", ",value(c,"adult-address"),value(c,"adult-commune"),region(value(c,"adult-region")));
        String subject=nna?"Formulario web NNA":join(" · ","Formulario web adulto",label(MOTIVE,value(c,"motivo")),label(TOPIC,value(c,"tema")));
        String body=NARRATIVE.stream().map(k->value(c,k)).filter(Objects::nonNull).findFirst().orElse("");
        return new Com(clip(name,200),exact(identity,100),clip(String.join("; ",contact),300),exact(email,200),exact(phone,80),
            clip(String.join("\n",restrictions),1000),clip(domicile,500),clip(subject,240),body);
    }
    private static String value(Map<String,String> c,String key){String v=c.get(key);return v==null||v.isBlank()?null:v;}
    private static String first(Map<String,String> c,String... keys){for(String k:keys){String v=value(c,k);if(v!=null)return v;}return null;}
    private static String byMethod(Map<String,String> c,String method){
        for(String slot:List.of("primary","secondary"))if(method.equals(value(c,"nna-"+slot+"-method"))){String v=value(c,"nna-"+slot+"-"+method);if(v!=null)return v;}
        return null;
    }
    private static String label(Map<String,String> options,String v){return v==null?null:options.getOrDefault(v,v);}
    private static String region(String code){return code==null?null:REGIONS.getOrDefault(code,code);}
    private static String join(String sep,String... parts){
        var out=Arrays.stream(parts).filter(s->s!=null&&!s.isBlank()).toList();
        return out.isEmpty()?null:String.join(sep,out);
    }
    /** Proyeccion de presentacion: si no cabe, se acorta con "…"; el valor integro queda en external_*. */
    static String clip(String text,int max){
        if(text==null||text.isEmpty())return null;
        if(text.length()<=max)return text;
        int end=max-1;
        if(Character.isHighSurrogate(text.charAt(end-1)))end--;
        return text.substring(0,end)+"…";
    }
    /** Identificadores y medios de contacto no se acortan: si no caben, la columna queda vacia. */
    private static String exact(String text,int max){return text!=null&&text.length()<=max?text:null;}
}
