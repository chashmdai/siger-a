package cl.nexbyte.sigera;

import java.util.*;
import tools.jackson.databind.JsonNode;

/**
 * Proyeccion del presentador de un original externo autenticado (Formulario completo 1.0/1.1) hacia los
 * campos presenter e identity_document de la COM. Es solo lectura: el documento original no se modifica.
 *
 * Reglas del contrato (IdentificacionCiudadana del Formulario, commit a84d2e7):
 * - El presentador es la unica persona con rol PRESENTADOR; NNA afectados y otras personas no se usan.
 * - Los agregados nombres/apellidos (ClaveUnica o SRCeI) se usan tal cual y nunca se dividen; sin agregado,
 *   se usan en orden los componentes primer/segundo nombre o apellido disponibles.
 * - Un valor sirve si es texto no vacio, distinto de "Desconocido" y sin codigo "U".
 * - La procedencia se conserva en el texto: ClaveUnica (solo si la persona esta autenticada y el nivel es
 *   IDENTIDAD_AUTENTICADA), contrastado en SRCeI (no autentica) o declarado. Otra combinacion no se proyecta.
 * - No se completa, corrige ni trunca: si falta el dato o no cabe en la columna, el campo queda vacio y el
 *   original conserva todo.
 */
record ExternalPresenter(String presenter,String identityDocument) {
    static final int PRESENTER_MAX=200,IDENTITY_MAX=100;
    private static final ExternalPresenter NONE=new ExternalPresenter(null,null);
    private static final Map<String,String> FIELD_LABELS=Map.of("nombres","nombres","apellidos","apellidos",
        "primerNombre","primer nombre","segundoNombre","segundo nombre","primerApellido","primer apellido","segundoApellido","segundo apellido");

    private record Part(String field,String value,String source){}

    static ExternalPresenter project(JsonNode comunicacion){
        JsonNode presenter=null;
        int presenters=0;
        for(var p:comunicacion.path("personas"))if("PRESENTADOR".equals(p.path("rol").asString())){presenters++;presenter=p;}
        if(presenters!=1)return NONE;
        boolean authenticated=presenter.path("autenticada").isBoolean()&&presenter.path("autenticada").asBoolean();
        var attrs=presenter.path("atributos");

        var parts=new ArrayList<Part>();
        group(parts,attrs,authenticated,"nombres","primerNombre","segundoNombre");
        group(parts,attrs,authenticated,"apellidos","primerApellido","segundoApellido");
        String name=null;
        if(!parts.isEmpty()){
            var values=parts.stream().map(Part::value).toList();
            var sources=parts.stream().map(Part::source).distinct().toList();
            String provenance=sources.size()==1?sources.getFirst()
                :String.join("; ",parts.stream().map(x->FIELD_LABELS.get(x.field())+": "+x.source()).toList());
            name=fit(String.join(" ",values)+" ("+provenance+")",PRESENTER_MAX);
        }

        String identity=null;
        String number=value(attrs.path("runNumero")),dv=value(attrs.path("runDv"));
        String numberSource=source(attrs.path("runNumero"),authenticated),dvSource=source(attrs.path("runDv"),authenticated);
        if(number!=null&&dv!=null&&numberSource!=null&&numberSource.equals(dvSource)&&number.matches("[0-9]{1,9}")&&dv.matches("[0-9Kk]")
            &&consistent(presenter.path("runNumero"),number)&&consistent(presenter.path("runDv"),dv))
            identity=fit("RUN "+number+"-"+dv+" ("+numberSource+")",IDENTITY_MAX);
        return new ExternalPresenter(name,identity);
    }

    /** Agregado si sirve; si no, los componentes que sirvan, en orden. */
    private static void group(List<Part> out,JsonNode attrs,boolean authenticated,String aggregate,String... components){
        if(usable(attrs.path(aggregate),authenticated)){out.add(part(attrs,aggregate,authenticated));return;}
        for(String c:components)if(usable(attrs.path(c),authenticated))out.add(part(attrs,c,authenticated));
    }
    private static Part part(JsonNode attrs,String field,boolean authenticated){
        return new Part(field,value(attrs.path(field)),source(attrs.path(field),authenticated));
    }
    private static boolean usable(JsonNode a,boolean authenticated){return value(a)!=null&&source(a,authenticated)!=null;}
    private static String value(JsonNode a){
        if(!a.isObject()||!a.path("valor").isString())return null;
        String v=a.path("valor").asString();
        if(v.isBlank()||"Desconocido".equals(v)||"U".equals(a.path("codigo").asString()))return null;
        return v;
    }
    /** Procedencia legible del atributo, o null si fuente y nivel no corresponden al contrato. */
    private static String source(JsonNode a,boolean authenticated){
        String fuente=a.path("fuente").asString(),nivel=a.path("nivel").asString();
        if("CLAVE_UNICA_USERINFO".equals(fuente))return authenticated&&"IDENTIDAD_AUTENTICADA".equals(nivel)?"autenticado con ClaveÚnica":null;
        if("SRCEI".equals(fuente))return "CONTRASTADO_EN_FUENTE".equals(nivel)?"contrastado en SRCeI, no autenticado":null;
        if("DECLARACION_CIUDADANA".equals(fuente))return "DECLARADO_NO_CONTRASTADO".equals(nivel)?"declarado":null;
        return null;
    }
    /** El RUN de la persona, si viene informado, debe coincidir con el del atributo. */
    private static boolean consistent(JsonNode top,String value){return top.isNull()||top.isMissingNode()||top.isString()&&top.asString().equals(value);}
    private static String fit(String text,int max){return text.length()<=max?text:null;}
}
