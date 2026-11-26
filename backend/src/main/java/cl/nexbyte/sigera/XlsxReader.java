package cl.nexbyte.sigera;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.zip.*;
import javax.xml.stream.*;

/** Lector acotado de la primera hoja de datos de un XLSX. Nunca extrae entradas al disco ni resuelve relaciones externas. */
final class XlsxReader {
    static final int ENTRIES=96, EXPANDED=32*1024*1024, RATIO=160, ROWS=20_000;
    private final int maxEntries,maxBytes,maxRatio,maxRows;

    XlsxReader(int entries,int bytes,int ratio,int rows){
        if(entries<1||bytes<1||ratio<1||rows<1)throw new IllegalArgumentException("Límites XLSX inválidos.");
        maxEntries=entries;maxBytes=bytes;maxRatio=ratio;maxRows=rows;
    }
    static XlsxReader standard(){return new XlsxReader(ENTRIES,EXPANDED,RATIO,ROWS);}

    List<List<String>> read(byte[] bytes){
        try{
            var parts=parts(bytes);
            var workbook=workbook(parts);
            var shared=shared(parts.get("xl/sharedStrings.xml"));
            var dates=dates(parts.get("xl/styles.xml"));
            return rows(parts.get(workbook.path()),shared,dates,workbook.date1904());
        }catch(IOException|XMLStreamException|IllegalArgumentException|ArithmeticException e){throw bad();}
    }

    private Map<String,byte[]> parts(byte[] bytes)throws IOException,XMLStreamException{
        var parts=new LinkedHashMap<String,byte[]>();
        var names=new HashSet<String>();
        int total=0,entries=0,rows=0;
        byte[] chunk=new byte[8192];
        try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes),StandardCharsets.UTF_8)){
            for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()){
                String name=entry.getName();
                if(++entries>maxEntries||!safePath(name)||!names.add(name.toLowerCase(Locale.ROOT)))throw bad();
                if(entry.isDirectory())continue;
                var out=new ByteArrayOutputStream();
                for(int n=zip.read(chunk);n!=-1;n=zip.read(chunk)){
                    if(n>maxBytes-total||total+(long)n>bytes.length*(long)maxRatio)throw bad();
                    total+=n;out.write(chunk,0,n);
                }
                if(out.size()>Math.max(1,entry.getCompressedSize())*(long)maxRatio)throw bad();
                byte[] content=out.toByteArray();
                String lower=name.toLowerCase(Locale.ROOT);
                if(lower.matches(".*(vbaproject|externallink|connections|embeddings|activex).*"))throw bad();
                if(lower.endsWith(".xml")||lower.endsWith(".rels"))rows=inspect(content,lower,rows);
                parts.put(name,content);
                zip.closeEntry();
            }
        }
        if(!parts.containsKey("[Content_Types].xml"))throw bad();
        return parts;
    }
    private static boolean safePath(String s){
        return !s.isEmpty()&&!s.startsWith("/")&&!s.contains("\\")&&!s.contains(":")&&!s.contains("%")
            &&Arrays.stream(s.split("/")).noneMatch(p->p.isEmpty()||p.equals(".")||p.equals(".."));
    }
    private int inspect(byte[] content,String name,int rows)throws XMLStreamException{
        var xml=xml(content);
        try{
            while(xml.hasNext()){
                int next=xml.next();
                if(next==XMLStreamConstants.DTD||next==XMLStreamConstants.ENTITY_REFERENCE)throw bad();
                if(next!=XMLStreamConstants.START_ELEMENT)continue;
                String tag=xml.getLocalName();
                if(Set.of("f","formula","formula1","formula2","definedName","externalReference","externalLink").contains(tag))throw bad();
                if(tag.equals("row")&&name.startsWith("xl/worksheets/")&&++rows>maxRows)throw bad();
                if(tag.equals("Relationship")){
                    String target=attr(xml,"Target"),mode=attr(xml,"TargetMode");
                    if(mode!=null&&!mode.equals("Internal")||target==null||target.contains(":")||target.contains("\\")||target.contains("%")||target.startsWith("//"))throw bad();
                }
            }
            return rows;
        }finally{xml.close();}
    }
    private static XMLStreamReader xml(byte[] bytes)throws XMLStreamException{
        if(bytes==null)throw bad();
        XMLInputFactory f=XMLInputFactory.newDefaultFactory();
        f.setProperty(XMLInputFactory.SUPPORT_DTD,false);
        f.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES,false);
        f.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES,false);
        f.setXMLResolver((publicId,systemId,base,namespace)->{throw new XMLStreamException("Resolución XML prohibida.");});
        return f.createXMLStreamReader(new ByteArrayInputStream(bytes));
    }
    private static String attr(XMLStreamReader xml,String name){return xml.getAttributeValue(null,name);}

    private record Workbook(String path,boolean date1904){}
    private static Workbook workbook(Map<String,byte[]> parts)throws XMLStreamException{
        String first=null;boolean date1904=false;
        var xml=xml(parts.get("xl/workbook.xml"));
        try{
            while(xml.hasNext())if(xml.next()==XMLStreamConstants.START_ELEMENT){
                if(xml.getLocalName().equals("sheet")&&first==null)first=xml.getAttributeValue(Xlsx.REL,"id");
                if(xml.getLocalName().equals("workbookPr"))date1904=Set.of("true","1").contains(Objects.toString(attr(xml,"date1904"),""));
            }
        }finally{xml.close();}
        if(first==null)throw bad();
        xml=xml(parts.get("xl/_rels/workbook.xml.rels"));
        try{
            while(xml.hasNext())if(xml.next()==XMLStreamConstants.START_ELEMENT&&xml.getLocalName().equals("Relationship")&&first.equals(attr(xml,"Id"))){
                String target=attr(xml,"Target");
                String path=target.startsWith("/")?target.substring(1):"xl/"+target;
                if(!safePath(path)||!path.startsWith("xl/worksheets/")||!path.endsWith(".xml"))throw bad();
                return new Workbook(path,date1904);
            }
        }finally{xml.close();}
        throw bad();
    }
    private static List<String> shared(byte[] bytes)throws XMLStreamException{
        if(bytes==null)return List.of();
        var xml=xml(bytes);var values=new ArrayList<String>();
        try{
            while(xml.hasNext())if(xml.next()==XMLStreamConstants.START_ELEMENT&&xml.getLocalName().equals("si"))values.add(text(xml,"si"));
            return values;
        }finally{xml.close();}
    }
    private static String text(XMLStreamReader xml,String end)throws XMLStreamException{
        var out=new StringBuilder();
        while(xml.hasNext()){
            int event=xml.next();
            if(event==XMLStreamConstants.END_ELEMENT&&xml.getLocalName().equals(end))return out.toString();
            if(event==XMLStreamConstants.START_ELEMENT&&xml.getLocalName().equals("t"))out.append(xml.getElementText());
        }
        throw bad();
    }
    private static Set<Integer> dates(byte[] bytes)throws XMLStreamException{
        if(bytes==null)return Set.of();
        var xml=xml(bytes);var custom=new HashSet<Integer>();var date=new HashSet<Integer>();
        boolean styles=false;int index=0;
        try{
            while(xml.hasNext()){
                int event=xml.next();
                if(event==XMLStreamConstants.END_ELEMENT&&xml.getLocalName().equals("cellXfs"))styles=false;
                if(event!=XMLStreamConstants.START_ELEMENT)continue;
                switch(xml.getLocalName()){
                    case "numFmt"->{
                        String pattern=Objects.toString(attr(xml,"formatCode"),"").toLowerCase(Locale.ROOT).replaceAll("\"[^\"]*\"|\\[[^]]*]","");
                        if(pattern.matches(".*[dy].*")&&pattern.contains("m")&&!pattern.matches(".*[hs].*"))custom.add(Integer.parseInt(attr(xml,"numFmtId")));
                    }
                    case "cellXfs"->styles=true;
                    case "xf"->{if(styles){int n=Integer.parseInt(Objects.toString(attr(xml,"numFmtId"),"0"));if(n>=14&&n<=17||n==22||custom.contains(n))date.add(index);index++;}}
                    default->{ }
                }
            }
            return date;
        }finally{xml.close();}
    }

    private static List<List<String>> rows(byte[] bytes,List<String> shared,Set<Integer> dates,boolean date1904)throws XMLStreamException{
        var out=new ArrayList<List<String>>();
        var xml=xml(bytes);
        try{
            while(xml.hasNext())if(xml.next()==XMLStreamConstants.START_ELEMENT&&xml.getLocalName().equals("row"))out.add(row(xml,shared,dates,date1904));
            if(out.isEmpty())throw bad();
            return out;
        }finally{xml.close();}
    }
    private static List<String> row(XMLStreamReader xml,List<String> shared,Set<Integer> dates,boolean date1904)throws XMLStreamException{
        var cells=new ArrayList<String>();
        while(xml.hasNext()){
            int next=xml.next();
            if(next==XMLStreamConstants.END_ELEMENT&&xml.getLocalName().equals("row"))return cells;
            if(next!=XMLStreamConstants.START_ELEMENT||!xml.getLocalName().equals("c"))continue;
            String ref=attr(xml,"r"),type=attr(xml,"t"),style=attr(xml,"s");
            int col=ref==null?cells.size():column(ref);
            if(col<cells.size()||col>=256)throw bad();
            while(cells.size()<col)cells.add("");
            String value=cell(xml);
            if("s".equals(type)){
                int ix=Integer.parseInt(value);if(ix<0||ix>=shared.size())throw bad();value=shared.get(ix);
            }else if("e".equals(type))throw bad();
            else if(!value.isEmpty()&&(type==null||type.equals("n"))&&style!=null&&dates.contains(Integer.parseInt(style))){
                int days=new BigDecimal(value).intValueExact();
                if(days<0||days>100_000||!date1904&&days==60)throw bad();
                var origin=date1904?LocalDate.of(1904,1,1):LocalDate.of(1899,12,31);
                value=origin.plusDays(!date1904&&days>60?days-1:days).toString();
            }
            cells.add(value);
        }
        throw bad();
    }
    private static int column(String ref){
        int value=0,i=0;
        while(i<ref.length()&&ref.charAt(i)>='A'&&ref.charAt(i)<='Z'){
            value=26*value+ref.charAt(i++)-'A'+1;if(value>Xlsx.MAX_COLUMNS)throw bad();
        }
        if(value==0||i==ref.length()||!ref.substring(i).matches("[1-9][0-9]{0,6}"))throw bad();
        return value-1;
    }
    private static String cell(XMLStreamReader xml)throws XMLStreamException{
        String value="";
        while(xml.hasNext()){
            int next=xml.next();
            if(next==XMLStreamConstants.END_ELEMENT&&xml.getLocalName().equals("c"))return value;
            if(next==XMLStreamConstants.START_ELEMENT){
                if(xml.getLocalName().equals("is"))value=text(xml,"is");
                else if(xml.getLocalName().equals("v"))value=xml.getElementText();
            }
        }
        throw bad();
    }
    private static Problem bad(){return new Problem(400,"El archivo XLSX contiene elementos no admitidos o supera los límites de importación.");}
}
