package cl.nexbyte.sigera;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * CSV de las exportaciones (G12): UTF-8 con BOM, separador punto y coma, decimales con coma y fechas aaaa-mm-dd hh:mm en
 * hora de Chile, como lo abre una planilla con configuracion regional de Chile. Cada celda va entre comillas; las que
 * empiezan con = + - @ o tabulador se anteponen con un apostrofo (inyeccion de formulas). Los saltos de linea se reemplazan.
 */
final class Csv {
    static final char SEPARATOR=';';
    static final DateTimeFormatter WHEN=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(BusinessCalendar.ZONE);
    private final StringBuilder out=new StringBuilder("﻿");
    Csv(List<String> header){row(new ArrayList<>(header));}

    /** Una fila; null es una celda vacia. Los numeros se escriben con coma decimal. */
    Csv row(List<?> cells){
        var parts=new ArrayList<String>();
        for(var c:cells)parts.add(cell(c));
        out.append(String.join(String.valueOf(SEPARATOR),parts)).append("\r\n");
        return this;
    }
    static String cell(Object value){
        String v=switch(value){
            case null -> "";
            case Double d -> decimal(d);
            case Float f -> decimal(f.doubleValue());
            default -> value.toString();
        };
        String safe=v.replace("\r"," ").replace("\n"," ");
        if(safe.matches("^[=+@\\t-].*"))safe="'"+safe;
        return "\""+safe.replace("\"","\"\"")+"\"";
    }
    /** Dos decimales como maximo, coma decimal y sin separador de miles. */
    static String decimal(double d){
        String s=String.format(Locale.ROOT,"%.2f",Reports.round(d));
        if(s.contains("."))s=s.replaceAll("0+$","").replaceAll("\\.$","");
        return s.replace('.',',');
    }
    /** Instante en hora de Chile; 0 o nulo = vacio. */
    static String when(Long ms){return ms==null||ms<=0?null:WHEN.format(Instant.ofEpochMilli(ms));}
    byte[] bytes(){return out.toString().getBytes(StandardCharsets.UTF_8);}
}
