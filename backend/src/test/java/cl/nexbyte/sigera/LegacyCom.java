package cl.nexbyte.sigera;

import java.util.*;

/**
 * COM manual anterior a 0.8.0 para las pruebas de lectura historica (gate E05). El formato sin ficha de ingreso se
 * retiro de COM_CREATE (400), pero las COM ya registradas conservan sus datos en las columnas de texto libre de
 * communication y en communication_person, y deben seguir legibles e indexadas. La COM se crea con el comando (folio,
 * hito y auditoria vigentes) con la ficha de ingreso vacia, y luego se deja por SQL exactamente como la dejaba el
 * formato anterior: sin ficha de ingreso, columnas de texto libre (vacias si no se indican, como Input.text) y una fila
 * de communication_person por persona; por ultimo se recalcula su indice de personas.
 */
final class LegacyCom {
    private LegacyCom(){}
    static final List<String> COLUMNS=List.of("presenter","contact","identity_document","contact_email","contact_phone","contact_restrictions","presenter_domicile");

    static long create(Workflow flow,Db db,PersonIndex index,String who,Map<String,Object> base,Map<String,Object> legacy){
        var data=new LinkedHashMap<String,Object>(base);data.put("intake",Map.of());
        long com=flow.run(who,new Workflow.Command(UUID.randomUUID(),"COM_CREATE",null,null,data)).id();
        db.update("DELETE FROM intake_revision WHERE communication_id=?",com);
        db.update("DELETE FROM intake_record WHERE communication_id=?",com);
        for(String column:COLUMNS)db.update("UPDATE communication SET "+column+"=? WHERE id=?",text(legacy.get(column)),com);
        if(legacy.get("persons") instanceof List<?> persons)for(Object item:persons){
            var p=(Map<?,?>)item;
            db.update("INSERT INTO communication_person(communication_id,person_role,name,identity_document,relationship,location_text) VALUES(?,?,?,?,?,?)",
                com,text(p.get("role")),text(p.get("name")),text(p.get("identity_document")),text(p.get("relationship")),text(p.get("location")));
        }
        index.refresh(com);
        return com;
    }
    private static String text(Object value){return value==null?"":value.toString().strip();}
}
