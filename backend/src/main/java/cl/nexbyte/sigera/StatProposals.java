package cl.nexbyte.sigera;

import java.util.*;
import org.springframework.stereotype.Component;
import static cl.nexbyte.sigera.Db.*;

/**
 * Propuestas de clasificacion (0.8.0, D29-D30, gate E05; DISENO 4.2), regla version 1. Una propuesta no es un estado ni
 * un evento: se calcula cada vez desde los datos estructurados ya proyectados del Formulario (external_field, grupo
 * CAMPO, solo lectura) y nunca cuenta como INFORMADO. Sin inferencia sobre relatos.
 * - TIPO_SOLICITUD: Formulario adulto por motivo (consulta -> CONSULTA_ORIENTACION, vulneracion -> VULNERACION,
 *   promocion -> PROMOCION). Formulario NNA, COM manual y atencion directa: sin propuesta.
 * - TIPO_REQUIRENTE: Formulario NNA -> NNA; Formulario adulto -> PERSONA_NATURAL_ADULTA, salvo promocion con
 *   organizacion=si -> PERSONA_JURIDICA_ORGANISMO. Manual y directa: sin propuesta.
 * Valores del Formulario confirmados en formulario-web 6f886eb (ValidacionAdultos: motivo consulta|vulneracion|
 * promocion, organizacion si|no). La comparacion es exacta en Java; el SQL equivalente (sqlCode) compara con la
 * intercalacion de la base, que en MySQL no distingue mayusculas ni tildes: da lo mismo porque el contrato del
 * Formulario solo admite esos valores exactos (lo prueba StatClassificationTest sobre H2 y MySQL).
 * La propuesta solo se ofrece si el codigo es un item seleccionable del catalogo VIGENTE de la variable.
 */
@Component
public class StatProposals {
    static final String RULE="PROPUESTA_V1";
    static final String ADULT="FORMULARIO_WEB_PUBLICO",NNA="FORMULARIO_WEB_NNA";
    static final Map<String,String> MOTIVE=Map.of("consulta","CONSULTA_ORIENTACION","vulneracion","VULNERACION","promocion","PROMOCION");
    static final Set<String> VARIABLES=Set.of(StatValues.TIPO_SOLICITUD,StatValues.TIPO_REQUIRENTE);
    private final Db db;
    public StatProposals(Db db){this.db=db;}

    /** Regla pura: codigo propuesto para la variable segun el canal y los campos del Formulario, o null. */
    static String rule(String variable,String channel,Map<String,String> fields){
        if(variable.equals(StatValues.TIPO_SOLICITUD))return ADULT.equals(channel)?MOTIVE.get(fields.get("motivo")):null;
        if(variable.equals(StatValues.TIPO_REQUIRENTE)){
            if(NNA.equals(channel))return "NNA";
            if(!ADULT.equals(channel))return null;
            return "promocion".equals(fields.get("motivo"))&&"si".equals(fields.get("organizacion"))?"PERSONA_JURIDICA_ORGANISMO":"PERSONA_NATURAL_ADULTA";
        }
        return null;
    }

    /** Propuestas de la COM por variable (solo codigos seleccionables del catalogo vigente). */
    Map<String,String> of(long com){
        var c=db.one("SELECT channel FROM communication WHERE id=?",com);
        var fields=new HashMap<String,String>();
        for(var f:db.rows("SELECT field_key,value_text FROM external_field WHERE communication_id=? AND field_group='CAMPO' AND field_key IN ('motivo','organizacion')",com))
            fields.put(s(f,"field_key"),s(f,"value_text"));
        var out=new LinkedHashMap<String,String>();
        for(String variable:List.of(StatValues.TIPO_SOLICITUD,StatValues.TIPO_REQUIRENTE)){
            String code=rule(variable,s(c,"channel"),fields);
            if(code!=null&&StatValues.selectableCurrent(db,variable,code))out.put(variable,code);
        }
        return out;
    }

    /** SQL portable equivalente a of(): codigo propuesto de la variable para la COM con alias c (null si no hay). */
    static String sqlCode(String c,String variable){
        String motive="(SELECT MAX(f.value_text) FROM external_field f WHERE f.communication_id="+c+".id AND f.field_group='CAMPO' AND f.field_key='motivo')";
        String organization="(SELECT MAX(f.value_text) FROM external_field f WHERE f.communication_id="+c+".id AND f.field_group='CAMPO' AND f.field_key='organizacion')";
        String raw;
        if(variable.equals(StatValues.TIPO_SOLICITUD)){
            var when=new StringBuilder();
            MOTIVE.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->when.append(" WHEN '").append(e.getKey()).append("' THEN '").append(e.getValue()).append("'"));
            raw="CASE WHEN "+c+".channel='"+ADULT+"' THEN (CASE "+motive+when+" END) END";
        }else if(variable.equals(StatValues.TIPO_REQUIRENTE)){
            raw="CASE WHEN "+c+".channel='"+NNA+"' THEN 'NNA' WHEN "+c+".channel='"+ADULT+"' THEN (CASE WHEN "+motive+"='promocion' AND "+organization
                +"='si' THEN 'PERSONA_JURIDICA_ORGANISMO' ELSE 'PERSONA_NATURAL_ADULTA' END) END";
        }else throw new IllegalArgumentException(variable);
        return "(SELECT MAX(pi.code) FROM stat_catalog_item pi JOIN stat_catalog_version pv ON pv.id=pi.version_id WHERE pv.`current_catalog`='"+variable
            +"' AND pi.selectable=TRUE AND pi.code=("+raw+"))";
    }
}
