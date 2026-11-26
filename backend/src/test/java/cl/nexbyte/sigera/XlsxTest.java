package cl.nexbyte.sigera;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class XlsxTest {
    @Test void idaYVueltaDeterministaConTextoProtegido(){
        var sheets=List.of(new Xlsx.Sheet("Datos",List.of(List.of("cabecera","valor"),List.of("=1+1",3L),List.of("+X","Ñ & <"),List.of("-X","@X"))));
        byte[] workbook=Xlsx.write(sheets);
        assertArrayEquals(workbook,Xlsx.write(sheets));
        assertEquals(List.of(List.of("cabecera","valor"),List.of("'=1+1","3"),List.of("'+X","Ñ & <"),List.of("'-X","'@X")),XlsxReader.standard().read(workbook));
    }

    @Test void rechazaArchivosFueraDeLimitesYContenidoActivo() throws Exception {
        byte[] workbook=Xlsx.write(List.of(new Xlsx.Sheet("Datos",List.of(List.of("A"),List.of("B")))));
        assertEquals(400,assertThrows(Problem.class,()->new XlsxReader(1,1_000_000,1_000,100).read(workbook)).status);
        assertEquals(400,assertThrows(Problem.class,()->new XlsxReader(50,64,1_000,100).read(workbook)).status);
        assertEquals(400,assertThrows(Problem.class,()->new XlsxReader(50,1_000_000,1_000,1).read(workbook)).status);
        assertEquals(400,assertThrows(Problem.class,()->new XlsxReader(50,1_000_000,1,100).read(workbook)).status);
        var entries=StatExportTest.unzip(workbook);
        String sheet=new String(entries.get("xl/worksheets/sheet1.xml"),StandardCharsets.UTF_8);
        for(String invalid:List.of(sheet.replace("<is>","<f>1+1</f><is>"),sheet.replace("?><worksheet","?><!DOCTYPE worksheet []><worksheet"),
                sheet.replace("?><worksheet","?><!DOCTYPE worksheet [<!ENTITY datos 'activo'>]><worksheet").replace(">A<",">&datos;<"))){
            entries.put("xl/worksheets/sheet1.xml",invalid.getBytes(StandardCharsets.UTF_8));
            assertEquals(400,assertThrows(Problem.class,()->XlsxReader.standard().read(Xlsx.zip(entries))).status);
        }
        var external=StatExportTest.unzip(workbook);
        external.put("xl/worksheets/_rels/sheet1.xml.rels",("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            +"<Relationship Id=\"rId1\" Target=\"externo.xlsx\" TargetMode=\"External\"/></Relationships>").getBytes(StandardCharsets.UTF_8));
        assertEquals(400,assertThrows(Problem.class,()->XlsxReader.standard().read(Xlsx.zip(external))).status);
    }

    @Test void importaHojaConCadenasCompartidasYFechaNumerica() throws Exception {
        // Archivo OOXML de prueba con la representación que usan las hojas que guardan cadenas y formatos en tablas.
        var workbook=Xlsx.write(List.of(new Xlsx.Sheet("Datos",List.of(List.of("A")))));
        var parts=StatExportTest.unzip(workbook);
        put(parts,"xl/sharedStrings.xml","<sst xmlns=\""+Xlsx.MAIN+"\"><si><r><t>Per</t></r><r><t>iodo</t></r></si><si><t>Valor</t></si></sst>");
        put(parts,"xl/styles.xml","<styleSheet xmlns=\""+Xlsx.MAIN+"\"><cellXfs count=\"2\"><xf numFmtId=\"0\"/><xf numFmtId=\"14\"/></cellXfs></styleSheet>");
        put(parts,"xl/worksheets/sheet1.xml","<worksheet xmlns=\""+Xlsx.MAIN+"\"><sheetData><row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"C1\" t=\"s\"><v>1</v></c></row>"
            +"<row r=\"2\"><c r=\"A2\" s=\"1\"><v>45292</v></c><c r=\"C2\"><v>25</v></c></row></sheetData></worksheet>");
        assertEquals(List.of(List.of("Periodo","","Valor"),List.of("2024-01-01","","25")),XlsxReader.standard().read(Xlsx.zip(parts)));
    }

    @Test void relacionDeCompresionPorEntradaAunqueElZipCompletoEstePorDebajo() throws Exception {
        var workbook=Xlsx.write(List.of(new Xlsx.Sheet("Datos",List.of(List.of("A")))));
        var parts=StatExportTest.unzip(workbook);
        put(parts,"xl/worksheets/sheet1.xml","<worksheet xmlns=\""+Xlsx.MAIN+"\"><sheetData><row><c t=\"inlineStr\"><is><t>"+"a".repeat(20_000)+"</t></is></c></row></sheetData></worksheet>");
        byte[] padding=new byte[20_000];new Random(7).nextBytes(padding);parts.put("docProps/padding.bin",padding);
        var hostile=Xlsx.zip(parts);
        assertTrue(hostile.length>20_000);
        assertEquals(400,assertThrows(Problem.class,()->new XlsxReader(50,1_000_000,40,100).read(hostile)).status);
    }
    private static void put(Map<String,byte[]> parts,String name,String xml){parts.put(name,xml.getBytes(StandardCharsets.UTF_8));}
}
