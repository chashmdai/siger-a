package cl.nexbyte.sigera;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import cl.nexbyte.sigera.BusinessCalendar.Holiday;
import static org.junit.jupiter.api.Assertions.*;

/** Calendario habil (G09): calculo puro en hora de Chile, sin Spring ni base de datos. */
class BusinessCalendarTest {
    static long at(String local){return LocalDateTime.parse(local).atZone(BusinessCalendar.ZONE).toInstant().toEpochMilli();}
    static Holiday national(String day){return new Holiday(LocalDate.parse(day),null);}
    static Holiday regional(String day,String region){return new Holiday(LocalDate.parse(day),region);}
    static final BusinessCalendar EMPTY=BusinessCalendar.of(List.of());

    @Test void ejemploDelPlanFinDeSemanaYMismoDia(){
        // Viernes 17:00 -> lunes 09:00: 64 h corridas y 1 dia habil (el dia de inicio no cuenta).
        assertEquals(64.0,BusinessCalendar.hours(at("2026-11-20T17:00"),at("2026-11-23T09:00")));
        assertEquals(1,EMPTY.businessDays(at("2026-11-20T17:00"),at("2026-11-23T09:00")));
        // Mismo dia = 0, aunque pasen horas; mismo instante = 0 horas.
        assertEquals(0,EMPTY.businessDays(at("2026-11-18T08:00"),at("2026-11-18T23:59")));
        assertEquals(0.0,BusinessCalendar.hours(at("2026-11-18T08:00"),at("2026-11-18T08:00")));
        // Sabado -> domingo y viernes -> domingo: 0; jueves -> viernes: 1; lunes -> lunes siguiente: 5.
        assertEquals(0,EMPTY.businessDays(at("2026-11-21T10:00"),at("2026-11-22T10:00")));
        assertEquals(0,EMPTY.businessDays(at("2026-11-20T10:00"),at("2026-11-22T10:00")));
        assertEquals(1,EMPTY.businessDays(at("2026-11-19T10:00"),at("2026-11-20T10:00")));
        assertEquals(5,EMPTY.businessDays(at("2026-11-16T10:00"),at("2026-11-23T10:00")));
        // Un anio completo de 2026 (sin feriados): 261 dias de lunes a viernes entre el 31-12-2025 y el 31-12-2026.
        assertEquals(261,EMPTY.businessDays(at("2025-12-31T12:00"),at("2026-12-31T12:00")));
        // Antes de 1970 el conteo sigue siendo correcto (division entera hacia abajo).
        assertEquals(1,EMPTY.businessDays(at("1969-12-26T12:00"),at("1969-12-29T12:00")));
        // Fin anterior al inicio: error explicito, nunca un numero negativo.
        assertThrows(IllegalArgumentException.class,()->EMPTY.businessDays(at("2026-11-23T09:00"),at("2026-11-20T17:00")));
        assertThrows(IllegalArgumentException.class,()->BusinessCalendar.hours(2,1));
    }

    @Test void coincideConElConteoDiaPorDia(){
        // Contraste con el conteo ingenuo en 400 dias de periodos, con feriados en semana y fin de semana.
        var holidays=List.of(national("2026-01-01"),national("2026-05-21"),national("2026-09-18"),national("2026-09-19"),national("2026-12-25"),regional("2026-08-20","16"));
        var cal=BusinessCalendar.of(holidays);
        var nat=Set.of(LocalDate.parse("2026-01-01"),LocalDate.parse("2026-05-21"),LocalDate.parse("2026-09-18"),LocalDate.parse("2026-12-25"));
        LocalDate base=LocalDate.parse("2025-12-20");
        for(int start=0;start<400;start+=7)for(int len=0;len<60;len+=3){
            LocalDate a=base.plusDays(start),b=a.plusDays(len);long expected=0;
            for(LocalDate d=a.plusDays(1);!d.isAfter(b);d=d.plusDays(1))if(d.getDayOfWeek().getValue()<=5&&!nat.contains(d))expected++;
            assertEquals(expected,cal.businessDays(at(a+"T09:30"),at(b+"T15:00")),a+" -> "+b);
        }
    }

    @Test void feriadosNacionalesYRegionales(){
        var cal=BusinessCalendar.of(List.of(national("2026-12-25"),regional("2026-08-20","16"),national("2026-09-19")));
        // Navidad 2026 es viernes: jueves 24 -> lunes 28 = 1 (solo el lunes).
        assertEquals(1,cal.businessDays(at("2026-12-24T10:00"),at("2026-12-28T10:00")));
        assertEquals(2,EMPTY.businessDays(at("2026-12-24T10:00"),at("2026-12-28T10:00")));
        // El 19-09-2026 es sabado: no se descuenta dos veces.
        assertEquals(5,cal.businessDays(at("2026-09-14T10:00"),at("2026-09-21T10:00")));
        // Un feriado regional (jueves 20-08-2026, region 16) cuenta solo para su region.
        long from=at("2026-08-19T10:00"),to=at("2026-08-21T10:00");
        assertEquals(1,cal.businessDays(from,to,"16"));
        assertEquals(2,cal.businessDays(from,to,"05"));
        assertEquals(2,cal.businessDays(from,to));
        assertEquals(2,cal.businessDays(from,to,null));
        // Los nacionales rigen tambien con region.
        assertEquals(1,cal.businessDays(at("2026-12-24T10:00"),at("2026-12-28T10:00"),"16"));
        assertEquals(1,cal.businessDays(at("2026-12-24T10:00"),at("2026-12-28T10:00"),"05"));
    }

    @Test void cambioDeHorarioDeChile(){
        // 05-04-2026 00:00 (-03) vuelve a 04-04 23:00 (-04): el sabado 4 tiene 25 horas.
        assertEquals(25.0,BusinessCalendar.hours(at("2026-04-04T12:00"),at("2026-04-05T12:00")));
        // 06-09-2026 00:00 (-04) salta a 01:00 (-03): el domingo 6 tiene 23 horas.
        assertEquals(23.0,BusinessCalendar.hours(at("2026-09-05T12:00"),at("2026-09-06T12:00")));
        // Viernes 17:00 -> lunes 09:00 sobre cada cambio: 65 h y 63 h corridas, siempre 1 dia habil.
        assertEquals(65.0,BusinessCalendar.hours(at("2026-04-03T17:00"),at("2026-04-06T09:00")));
        assertEquals(63.0,BusinessCalendar.hours(at("2026-09-04T17:00"),at("2026-09-07T09:00")));
        assertEquals(1,EMPTY.businessDays(at("2026-04-03T17:00"),at("2026-04-06T09:00")));
        assertEquals(1,EMPTY.businessDays(at("2026-09-04T17:00"),at("2026-09-07T09:00")));
        // La fecha se toma en hora de Chile, no en UTC: lunes 23:30 (-03) ya es martes en UTC, y sigue siendo lunes.
        long mondayNight=at("2026-11-23T23:30"),tuesdayMorning=at("2026-11-24T08:00");
        assertEquals(LocalDate.parse("2026-11-23"),BusinessCalendar.date(mondayNight));
        assertEquals(1,EMPTY.businessDays(mondayNight,tuesdayMorning));
        // La hora repetida del 04-04 (23:30 en -03 y en -04) sigue en el sabado.
        long repeated=java.time.ZonedDateTime.of(LocalDateTime.parse("2026-04-04T23:30"),BusinessCalendar.ZONE).withLaterOffsetAtOverlap().toInstant().toEpochMilli();
        assertEquals(LocalDate.parse("2026-04-04"),BusinessCalendar.date(repeated));
    }

    @Test void aniosSinFeriadosNacionales(){
        var cal=BusinessCalendar.of(List.of(national("2026-01-01"),regional("2027-06-07","15")));
        assertEquals(List.of(),cal.missingYears(at("2026-03-01T10:00"),at("2026-11-01T10:00")));
        // 2027 solo tiene un feriado regional: igual se advierte que faltan los nacionales.
        assertEquals(List.of(2025,2027),cal.missingYears(at("2025-12-01T10:00"),at("2027-02-01T10:00")));
        assertEquals(List.of(2026),EMPTY.missingYears(at("2026-01-01T00:00"),at("2026-12-31T23:00")));
    }
}
