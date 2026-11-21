package cl.nexbyte.sigera;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Calendario habil (0.5.0, D6, gate G09), zona America/Santiago. Calculo puro sobre una instantanea de los feriados
 * (business_holiday, que mantiene TI); los reportes (G11) lo cargan una vez con load(db).
 * - Horas corridas = (fin - inicio) / 3.600.000: el cambio de horario de Chile da dias de 23 o 25 horas.
 * - Dias habiles = cantidad de dias d, de lunes a viernes y sin feriado, con fecha(inicio) < d <= fecha(fin): el dia
 *   de inicio no cuenta (como en el computo de plazos administrativos) y el mismo dia da 0.
 * - Feriados: los nacionales (region nula) rigen siempre; los regionales solo cuando se pide esa region. Un feriado
 *   en sabado o domingo no altera el computo.
 * Sin recorrer dia por dia: semanas completas y busqueda binaria en los feriados que caen de lunes a viernes.
 */
final class BusinessCalendar {
    static final ZoneId ZONE=ZoneId.of("America/Santiago");
    static final String NATIONAL="NAC";
    static final double HOUR_MS=3_600_000d;

    /** Feriado: region nula = nacional; si no, codigo de region del Formulario. */
    record Holiday(LocalDate day,String region){}

    private final long[] national;
    private final Map<String,long[]> regional;
    private final Set<Integer> nationalYears;

    private BusinessCalendar(Collection<Holiday> holidays){
        var nat=new TreeSet<Long>();var byRegion=new HashMap<String,TreeSet<Long>>();var years=new HashSet<Integer>();
        for(var h:holidays){
            if(h.region()==null)years.add(h.day().getYear());
            if(!weekday(h.day()))continue;
            if(h.region()==null)nat.add(h.day().toEpochDay());else byRegion.computeIfAbsent(h.region(),k->new TreeSet<>()).add(h.day().toEpochDay());
        }
        national=array(nat);
        var merged=new HashMap<String,long[]>();
        byRegion.forEach((region,days)->{var all=new TreeSet<>(nat);all.addAll(days);merged.put(region,array(all));});
        regional=Map.copyOf(merged);nationalYears=Set.copyOf(years);
    }
    static BusinessCalendar of(Collection<Holiday> holidays){return new BusinessCalendar(holidays);}
    /** Instantanea de los feriados registrados (dentro de la transaccion de quien la pide). */
    static BusinessCalendar load(Db db){
        return of(db.rows("SELECT holiday_day,region_code FROM business_holiday").stream()
            .map(r->new Holiday(LocalDate.parse(Db.s(r,"holiday_day")),r.get("region_code")==null?null:Db.s(r,"region_code"))).toList());
    }

    static LocalDate date(long ms){return Instant.ofEpochMilli(ms).atZone(ZONE).toLocalDate();}
    static boolean weekday(LocalDate d){return d.getDayOfWeek().getValue()<=5;}

    /** Horas corridas entre dos instantes (fin no anterior al inicio). */
    static double hours(long from,long to){order(from,to);return (to-from)/HOUR_MS;}

    /** Dias habiles con los feriados nacionales. */
    long businessDays(long from,long to){return businessDays(from,to,null);}
    /** Dias habiles con los feriados nacionales mas los de la region indicada (nula = solo nacionales). */
    long businessDays(long from,long to,String region){
        order(from,to);
        long a=date(from).toEpochDay(),b=date(to).toEpochDay();
        long[] holidays=region==null?national:regional.getOrDefault(region,national);
        return weekdaysUpTo(b)-weekdaysUpTo(a)-(upper(holidays,b)-upper(holidays,a));
    }

    /** Anios del periodo sin feriados nacionales cargados: los reportes advierten "sin feriados cargados para AAAA". */
    List<Integer> missingYears(long from,long to){
        order(from,to);var out=new ArrayList<Integer>();
        for(int y=date(from).getYear();y<=date(to).getYear();y++)if(!nationalYears.contains(y))out.add(y);
        return out;
    }

    private static void order(long from,long to){if(to<from)throw new IllegalArgumentException("El termino es anterior al inicio.");}
    /** Dias de lunes a viernes acumulados hasta el dia epoch x (inclusive), desde un lunes de referencia. */
    private static long weekdaysUpTo(long x){
        long k=x+3; // 1970-01-01 fue jueves: k=0 es el lunes 1969-12-29.
        return 5*Math.floorDiv(k,7)+Math.min(Math.floorMod(k,7)+1,5);
    }
    /** Cantidad de elementos <= x en un arreglo ordenado sin repetidos. */
    private static long upper(long[] sorted,long x){
        int i=Arrays.binarySearch(sorted,x);return i>=0?i+1:-(i+1);
    }
    private static long[] array(SortedSet<Long> days){return days.stream().mapToLong(Long::longValue).toArray();}
}
