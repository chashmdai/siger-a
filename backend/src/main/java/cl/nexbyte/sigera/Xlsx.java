package cl.nexbyte.sigera;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.*;

/** Libro OOXML de solo datos y contenedor ZIP con orden y fechas reproducibles. */
final class Xlsx {
    static final String MAIN="http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    static final String REL="http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    static final String PACKAGE="http://schemas.openxmlformats.org/package/2006/relationships";
    private static final String XML="<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
    private static final LocalDateTime ZIP_TIME=LocalDateTime.of(2000,1,1,0,0);
    static final int MAX_ROWS=1_048_576, MAX_COLUMNS=16_384;

    record Sheet(String name,Iterable<? extends List<?>> rows){}

    private Xlsx(){}

    static byte[] write(List<Sheet> sheets){
        var names=new HashSet<String>();
        for(var sheet:sheets){
            if(sheet.name().isBlank()||sheet.name().length()>31||sheet.name().matches(".*[\\[\\]:*?/\\\\].*")||!names.add(sheet.name().toLowerCase(Locale.ROOT)))
                throw new IllegalArgumentException("Nombre de hoja inválido.");
        }
        var out=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(out,StandardCharsets.UTF_8)){
            StringBuilder types=new StringBuilder("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>");
            StringBuilder workbook=new StringBuilder("<workbook xmlns=\""+MAIN+"\" xmlns:r=\""+REL+"\"><sheets>");
            StringBuilder rels=new StringBuilder("<Relationships xmlns=\""+PACKAGE+"\">");
            for(int i=1;i<=sheets.size();i++){
                String part="worksheets/sheet"+i+".xml";
                types.append("<Override PartName=\"/xl/").append(part).append("\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
                workbook.append("<sheet name=\"").append(escape(sheets.get(i-1).name())).append("\" sheetId=\"").append(i).append("\" r:id=\"rId").append(i).append("\"/>");
                rels.append(relationship(i,"worksheet",part));
            }
            xml(zip,"[Content_Types].xml",types.append("</Types>").toString());
            xml(zip,"_rels/.rels","<Relationships xmlns=\""+PACKAGE+"\">"+relationship(1,"officeDocument","xl/workbook.xml")+"</Relationships>");
            xml(zip,"xl/workbook.xml",workbook.append("</sheets></workbook>").toString());
            xml(zip,"xl/_rels/workbook.xml.rels",rels.append("</Relationships>").toString());
            for(int i=1;i<=sheets.size();i++)sheet(zip,"xl/worksheets/sheet"+i+".xml",sheets.get(i-1));
        }catch(IOException e){throw new UncheckedIOException(e);}
        return out.toByteArray();
    }

    private static String relationship(int id,String type,String path){
        return "<Relationship Id=\"rId"+id+"\" Type=\""+REL+"/"+type+"\" Target=\""+path+"\"/>";
    }
    private static void xml(ZipOutputStream zip,String name,String body)throws IOException{
        entry(zip,name);zip.write((XML+body).getBytes(StandardCharsets.UTF_8));zip.closeEntry();
    }
    static byte[] zip(Map<String,byte[]> files){
        var out=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(out,StandardCharsets.UTF_8)){
            for(var file:files.entrySet()){entry(zip,file.getKey());zip.write(file.getValue());zip.closeEntry();}
        }catch(IOException e){throw new UncheckedIOException(e);}
        return out.toByteArray();
    }
    private static void entry(ZipOutputStream zip,String name)throws IOException{
        var e=new ZipEntry(name);e.setTimeLocal(ZIP_TIME);zip.putNextEntry(e);
    }

    private static void sheet(ZipOutputStream zip,String name,Sheet sheet)throws IOException{
        entry(zip,name);
        var writer=new BufferedWriter(new OutputStreamWriter(zip,StandardCharsets.UTF_8));
        writer.write(XML+"<worksheet xmlns=\""+MAIN+"\"><sheetData>");
        int row=0;
        var refs=new ArrayList<String>();
        for(var cells:sheet.rows()){
            if(++row>MAX_ROWS||cells.size()>MAX_COLUMNS)throw new Problem(409,"La tabla supera el tamaño admitido por XLSX. Acota el periodo o los filtros.");
            while(refs.size()<cells.size())refs.add(column(refs.size()+1));
            var line=new StringBuilder("<row r=\"").append(row).append("\">");
            for(int col=0;col<cells.size();col++){
                Object value=cells.get(col);
                if(value==null)continue;
                line.append("<c r=\"").append(refs.get(col)).append(row).append('"');
                if(value instanceof Number number&&numeric(number))line.append("><v>").append(number).append("</v></c>");
                else line.append(" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(escape(safe(value.toString()))).append("</t></is></c>");
            }
            writer.write(line.append("</row>").toString());
        }
        writer.write("</sheetData></worksheet>");writer.flush();zip.closeEntry();
    }
    private static boolean numeric(Number n){
        // Los enteros grandes viajan como texto para conservar todos sus dígitos en Excel.
        try{return new BigDecimal(n.toString()).stripTrailingZeros().precision()<=15&&Math.abs(n.doubleValue())<1e15;}
        catch(NumberFormatException e){return false;}
    }
    private static String safe(String s){return !s.isEmpty()&&"=+-@\t\r\n".indexOf(s.charAt(0))>=0?"'"+s:s;}
    private static String column(int n){
        var out=new StringBuilder();
        for(;n>0;n=(n-1)/26)out.append((char)('A'+(n-1)%26));
        return out.reverse().toString();
    }
    private static String escape(String s){
        var out=new StringBuilder(s.length());
        s.codePoints().forEach(c->{
            switch(c){
                case '&'->out.append("&amp;");case '<'->out.append("&lt;");case '>'->out.append("&gt;");
                case '"'->out.append("&quot;");case '\r'->out.append("&#13;");
                default->{if(c==9||c==10||c>=32&&c<=0xd7ff||c>=0xe000&&c<=0xfffd||c>=0x10000)out.appendCodePoint(c);}
            }
        });
        return out.toString();
    }
}
