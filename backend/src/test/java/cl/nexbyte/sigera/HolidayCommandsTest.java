package cl.nexbyte.sigera;

import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;
import static cl.nexbyte.sigera.Db.*;

/** Calendario habil (G09): HOLIDAY_ADD, HOLIDAY_IMPORT y HOLIDAY_REMOVE solo para TI, auditados y aplicados al calculo. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigerholiday;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.seed-demo=true","siger.data=./target/test-holiday-${random.uuid}"})
@ActiveProfiles("test")
class HolidayCommandsTest {
    @Autowired Workflow flow; @Autowired Access access; @Autowired Db db; @Autowired Holidays holidays; @Autowired Queries queries;
    static final String REASON="Resolucion exenta sintetica de calendario";

    @BeforeEach void clean(){db.update("UPDATE staff SET must_change=FALSE");db.update("DELETE FROM business_holiday");}
    Workflow.Result run(String who,String type,Long id,Map<String,Object> data){return flow.run(who,new Workflow.Command(UUID.randomUUID(),type,id,null,data));}
    Problem refusal(String who,String type,Long id,Map<String,Object> data){return assertThrows(Problem.class,()->run(who,type,id,data));}
    static Map<String,Object> add(String day,String region,String name){
        var m=new HashMap<String,Object>(Map.of("day",day,"name",name,"reason",REASON));if(region!=null)m.put("region_code",region);return m;
    }
    static Map<String,Object> lines(String text){return Map.of("lines",text,"reason",REASON);}
    long holidays(){return db.count("SELECT COUNT(*) FROM business_holiday");}
    long events(String action){return db.count("SELECT COUNT(*) FROM audit_event WHERE action=? AND resource_type='CALENDARIO'",action);}
    static long at(String local){return LocalDateTime.parse(local).atZone(BusinessCalendar.ZONE).toInstant().toEpochMilli();}
    @SuppressWarnings("unchecked") static List<Map<String,Object>> list(Map<String,Object> m,String key){return (List<Map<String,Object>>)m.get(key);}

    @Test void agregarRetirarYAuditar(){
        long before=events(Holidays.ADDED);
        long id=run("ti","HOLIDAY_ADD",null,add("2026-12-25",null,"Navidad")).id();
        var row=db.one("SELECT * FROM business_holiday WHERE id=?",id);
        assertEquals("2026-12-25",s(row,"holiday_day"));assertNull(row.get("region_code"));assertEquals("NAC",s(row,"region_key"));
        assertEquals(n(db.one("SELECT id FROM staff WHERE username='ti'"),"id"),n(row,"created_by"));
        var event=db.one("SELECT * FROM audit_event WHERE action=? AND resource_id=?",Holidays.ADDED,id);
        assertEquals("CALENDARIO",s(event,"resource_type"));assertNull(event.get("scope_id"));
        assertEquals("2026-12-25 nacional Navidad. "+REASON,s(event,"note"));
        assertEquals(before+1,events(Holidays.ADDED));

        // Regional en otro dia; la lectura de TI los entrega ordenados, con la fecha de hoy en Chile.
        long regional=run("ti","HOLIDAY_ADD",null,add("2026-08-20","16","Feriado regional sintetico")).id();
        var calendar=holidays.calendar(access.actor("ti"));
        assertEquals(List.of("2026-08-20","2026-12-25"),list(calendar,"holidays").stream().map(h->s(h,"holiday_day")).toList());
        assertEquals("16",s(list(calendar,"holidays").getFirst(),"region_code"));
        assertEquals(java.time.LocalDate.now(BusinessCalendar.ZONE).toString(),calendar.get("today"));

        // Retirar borra la fila y deja la historia; retirar de nuevo es un conflicto.
        run("ti","HOLIDAY_REMOVE",regional,Map.of("reason",REASON));
        assertEquals(0,db.count("SELECT COUNT(*) FROM business_holiday WHERE id=?",regional));
        assertEquals("2026-08-20 región 16 Feriado regional sintetico. "+REASON,s(db.one("SELECT note FROM audit_event WHERE action=? AND resource_id=?",Holidays.REMOVED,regional),"note"));
        assertEquals(409,refusal("ti","HOLIDAY_REMOVE",regional,Map.of("reason",REASON)).status);
        assertEquals(400,refusal("ti","HOLIDAY_REMOVE",id,Map.of()).status,"fundamento obligatorio");

        // Misma clave y mismos datos: idempotente, una sola fila.
        var key=UUID.randomUUID();var cmd=new Workflow.Command(key,"HOLIDAY_ADD",null,null,add("2026-09-18",null,"Independencia Nacional"));
        assertEquals(flow.run("ti",cmd).id(),flow.run("ti",cmd).id());
        assertEquals(1,db.count("SELECT COUNT(*) FROM business_holiday WHERE holiday_day='2026-09-18'"));
    }

    @Test void validacionesYChoques(){
        run("ti","HOLIDAY_ADD",null,add("2026-12-25",null,"Navidad"));
        run("ti","HOLIDAY_ADD",null,add("2026-06-07","15","Feriado regional sintetico"));
        // Formato: dia inexistente, sin ceros, fuera de rango, region desconocida, sin nombre o sin fundamento.
        for(var bad:List.of(add("2026-02-30",null,"X"),add("2026-2-3",null,"X"),add("1999-12-31",null,"X"),add("2100-01-01",null,"X"),add("2026-07-01","99","X"),add("2026-07-01",null,"")))
            assertEquals(400,refusal("ti","HOLIDAY_ADD",null,bad).status,bad.toString());
        assertEquals(400,refusal("ti","HOLIDAY_ADD",null,Map.of("day","2026-07-01","name","X")).status);
        // Choques: mismo dia y region; regional sobre nacional; nacional sobre regional.
        assertTrue(refusal("ti","HOLIDAY_ADD",null,add("2026-12-25",null,"Otra")).getMessage().contains("ya está registrado como feriado nacional"));
        assertTrue(refusal("ti","HOLIDAY_ADD",null,add("2026-06-07","15","Otra")).getMessage().contains("ya está registrado como feriado de la región 15"));
        assertTrue(refusal("ti","HOLIDAY_ADD",null,add("2026-12-25","05","Otra")).getMessage().contains("ya es feriado nacional"));
        var p=refusal("ti","HOLIDAY_ADD",null,add("2026-06-07",null,"Otra"));
        assertEquals(409,p.status);assertTrue(p.getMessage().contains("ya tiene feriados regionales (15)"));
        // Otro regional el mismo dia en otra region si se admite.
        run("ti","HOLIDAY_ADD",null,add("2026-06-07","01","Otro regional sintetico"));
        assertEquals(3,holidays());
        // La base tambien lo impide (unicidad sobre region_key, que nunca es nula): dos nacionales el mismo dia.
        long ti=n(db.one("SELECT id FROM staff WHERE username='ti'"),"id");
        assertThrows(Exception.class,()->db.update("INSERT INTO business_holiday(holiday_day,region_code,region_key,name,created_by,created_ms) VALUES('2026-12-25',NULL,'NAC','Duplicado',?,0)",ti));
    }

    @Test void importarTodoONada(){
        run("ti","HOLIDAY_ADD",null,add("2026-01-01",null,"Año Nuevo"));
        long audited=events(Holidays.ADDED);
        String ok="""
            # Feriados 2027 sinteticos
            2027-01-01 Año Nuevo

            2027-06-07 Asalto y Toma del Morro de Arica [15]
            2027-08-20   Nacimiento sintetico  [ 16 ]
            2027-12-25 Navidad
            """;
        var result=run("ti","HOLIDAY_IMPORT",null,lines(ok));
        assertEquals("Se registraron 4 feriados.",result.message());
        assertEquals(5,holidays());assertEquals(audited+4,events(Holidays.ADDED));
        var arica=db.one("SELECT * FROM business_holiday WHERE holiday_day='2027-06-07'");
        assertEquals("15",s(arica,"region_code"));assertEquals("Asalto y Toma del Morro de Arica",s(arica,"name"));
        assertEquals("Nacimiento sintetico",s(db.one("SELECT name FROM business_holiday WHERE holiday_day='2027-08-20'"),"name"));
        assertEquals("2027-06-07 región 15 Asalto y Toma del Morro de Arica. Importación de 4 feriados. "+REASON,
            s(db.one("SELECT note FROM audit_event WHERE action=? AND resource_id=?",Holidays.ADDED,n(arica,"id")),"note"));

        // Una linea mal escrita: 400 con el numero de linea y sin registrar nada.
        var bad=refusal("ti","HOLIDAY_IMPORT",null,lines("2028-01-01 Año Nuevo\n2028-13-01 Mes inexistente\n2028-05-01 Día del Trabajo [99]\nsolo texto"));
        assertEquals(400,bad.status);
        assertTrue(bad.getMessage().startsWith("No se registró ningún feriado."));
        assertTrue(bad.getMessage().contains("Línea 2:")&&bad.getMessage().contains("Línea 3:")&&bad.getMessage().contains("Línea 4:"),bad.getMessage());
        assertFalse(bad.getMessage().contains("Línea 1:"));
        // Choque con lo registrado o dentro del mismo lote: 409, todo o nada.
        var clash=refusal("ti","HOLIDAY_IMPORT",null,lines("2028-01-01 Año Nuevo\n2027-12-25 Navidad"));
        assertEquals(409,clash.status);assertTrue(clash.getMessage().contains("Línea 2: 2027-12-25 ya está registrado"),clash.getMessage());
        var twice=refusal("ti","HOLIDAY_IMPORT",null,lines("2028-01-01 Año Nuevo\n2028-01-01 Repetido"));
        assertTrue(twice.getMessage().contains("en otra línea"),twice.getMessage());
        assertTrue(refusal("ti","HOLIDAY_IMPORT",null,lines("2028-03-01 Regional [05]\n2028-03-01 Nacional")).getMessage().contains("ya tiene feriados regionales (05)"));
        assertEquals(400,refusal("ti","HOLIDAY_IMPORT",null,lines("# solo comentarios\n\n")).status);
        assertEquals(400,refusal("ti","HOLIDAY_IMPORT",null,Map.of("lines","2028-01-01 Año Nuevo")).status,"fundamento obligatorio");
        var many=new StringBuilder();var day=java.time.LocalDate.parse("2030-01-01");for(int i=0;i<201;i++)many.append(day.plusDays(i)).append(" Sintetico\n");
        assertEquals(400,refusal("ti","HOLIDAY_IMPORT",null,lines(many.toString())).status);
        assertEquals(5,holidays());assertEquals(audited+4,events(Holidays.ADDED));
    }

    @Test void soloTiYCalculoConLoRegistrado(){
        for(String who:List.of("direccion.upd","auditoria","distribucion","gabinete")){
            assertEquals(403,refusal(who,"HOLIDAY_ADD",null,add("2026-12-25",null,"Navidad")).status,who);
            assertEquals(403,refusal(who,"HOLIDAY_IMPORT",null,lines("2026-12-25 Navidad")).status,who);
            assertEquals(403,refusal(who,"HOLIDAY_REMOVE",1L,Map.of("reason",REASON)).status,who);
            assertEquals(403,assertThrows(Problem.class,()->holidays.calendar(access.actor(who))).status,who);
        }
        assertEquals(0,holidays());

        run("ti","HOLIDAY_IMPORT",null,lines("2026-12-25 Navidad\n2026-08-20 Regional sintetico [16]"));
        // AUDITORIA ve los eventos del calendario en su auditoria; TI tambien.
        for(String who:List.of("auditoria","ti"))
            assertTrue(list(queries.administration(access.actor(who)),"audit").stream().anyMatch(e->s(e,"resource_type").equals("CALENDARIO")),who);

        // El calculo usa lo registrado: Navidad (viernes) para todos; el regional solo para su region.
        var cal=BusinessCalendar.load(db);
        assertEquals(1,cal.businessDays(at("2026-12-24T10:00"),at("2026-12-28T10:00")));
        assertEquals(1,cal.businessDays(at("2026-08-19T10:00"),at("2026-08-21T10:00"),"16"));
        assertEquals(2,cal.businessDays(at("2026-08-19T10:00"),at("2026-08-21T10:00"),"08"));
        assertEquals(List.of(2027),cal.missingYears(at("2026-06-01T10:00"),at("2027-01-15T10:00")));
    }
}
