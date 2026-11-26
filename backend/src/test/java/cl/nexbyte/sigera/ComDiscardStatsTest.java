package cl.nexbyte.sigera;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static cl.nexbyte.sigera.Db.*;
import static cl.nexbyte.sigera.StatClassificationTest.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_descarte_cifras;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.seed-demo=true","siger.data=./target/test-descarte-cifras-${random.uuid}","siger.scheduling=false"})
@ActiveProfiles("test")
class ComDiscardStatsTest extends StatFixture {
    static final Map<String,Object> PERIOD=period("CUENTA_PUBLICA","2025-07-01");
    static final String MANAGER="gestion.descarte",REGIONAL="coordinacion.regional";
    static final String INITIAL="ADSCRIPCION_INICIAL";
    static final String REASON="Fundamento libre de ensayo E12b";
    @Autowired StatExport export;

    @Test void figuraUnoYEstadosAntesDespuesYEnCortes()throws Exception{
        staff(MANAGER,"REPORTES","GESTION_ESTADISTICA","EXPORTACION");
        db.update("UPDATE staff SET must_change=FALSE");
        catalogs("tipo-solicitud-v1.json","tipo-requirente-v1.json","via-ingreso-v1.json");

        // A: nueva central -> inválida. B: nueva regional -> duplicada.
        // C: SOL devuelta central -> duplicada. D: nueva central, sin cambios.
        long a=com(ADMIN,"UPR-REQ","PRESENCIAL","2025-07-01T15:00:00Z",Map.of(),null);
        long b=com("profesional.regional","REG-DEMO","CORREO","2025-07-02T15:00:00Z",Map.of(),null);
        long c=com(ADMIN,"UPR-REQ","TELEFONO","2025-07-03T15:00:00Z",Map.of(),null);
        long d=com(ADMIN,"UPR-REQ","CORREO","2025-07-04T15:00:00Z",Map.of(),
            stat("CONSULTA_ORIENTACION","PERSONA_NATURAL_ADULTA"));
        route(DIST,c,"UPR-REQ",null);
        on(HEAD,"SOL_ROUTE","solicitation",sol(c),Map.of("scope_id",scope("UPD")));
        on("direccion.upd","SOL_RETURN","solicitation",sol(c),Map.of("reason","Devolución sintética"));
        for(long id:List.of(a,b,c,d))registered(id,"2025-07-04T16:00:00Z");

        var beforeVolume=reports.report(access.actor(MANAGER),new Reports.Request("VOLUMEN","2025-07-01","2026-06-30",
            null,null,null,null,null,null,null,null,null,null,null,null,null));
        assertEquals(4L,obj(beforeVolume.get("com")).get("total"));
        assertEquals(4,grand(figure("INGRESOS","ESTADO_COM")));
        assertEquals(of("NUEVA",3,"ENVIADA",1),cells(figure("INGRESOS","ESTADO_COM")));
        assertEquals(0,grand(figure("EXCLUIDOS","ESTADO_COM")));
        long beforeCut=cut(MANAGER,null,PERIOD);

        on(DIST,"COM_DISCARD","communication",a,Map.of("disposition","INVALIDA","reason",REASON));
        on(REGIONAL,"COM_DISCARD","communication",b,Map.of("disposition","DUPLICADA","reason",REASON,
            "stat",stat("CONSULTA_ORIENTACION","PERSONA_NATURAL_ADULTA")));
        on(DIST,"COM_DISCARD","communication",c,Map.of("disposition","DUPLICADA","reason",REASON,
            "stat",stat("PROMOCION","PERSONA_JURIDICA_ORGANISMO")));

        assertEquals(3,grand(figure("INGRESOS","ESTADO_COM")));
        assertEquals(of("DUPLICADA",2,"NUEVA",1),cells(figure("INGRESOS","ESTADO_COM")));
        assertEquals(1,grand(figure("EXCLUIDOS","ESTADO_COM")));
        assertEquals(of("INVALIDA",1),cells(figure("EXCLUIDOS","ESTADO_COM")));
        assertEquals(of("OFICINA_NACIONAL",1),cells(figure("EXCLUIDOS",INITIAL)));
        assertEquals(of("UPR-REQ",1),cells(figure("EXCLUIDOS","SEDE_INICIAL")));

        long afterCut=cut(MANAGER,null,PERIOD);
        var expected=Map.of(
            "TIPO_SOLICITUD",of("CONSULTA_ORIENTACION",2,"PROMOCION",1),
            "TIPO_REQUIRENTE",of("PERSONA_NATURAL_ADULTA",2,"PERSONA_JURIDICA_ORGANISMO",1),
            "VIA_INGRESO",of("CORREO",2,"TELEFONO",1),
            INITIAL,of("REG-DEMO",1,"OFICINA_NACIONAL",1,"PENDIENTE",1));
        for(var breakdown:expected.entrySet()){
            var live=figure("INGRESOS",breakdown.getKey());
            var frozen=figure("INGRESOS",breakdown.getKey(),"cut_id",afterCut);
            assertEquals(breakdown.getValue(),cells(live));
            assertEquals(3,grand(live));
            assertEquals(3,cells(live).values().stream().mapToLong(Long::longValue).sum());
            assertEquals(cells(live),cells(frozen));
            assertEquals(3,grand(frozen));
            assertFalse(live.toString().contains(REASON));
            assertFalse(frozen.toString().contains(REASON));
        }
        assertEquals(of("OFICINA_NACIONAL",1,"PENDIENTE",3),cells(figure("INGRESOS",INITIAL,"cut_id",beforeCut)));
        assertEquals(of("NUEVA",3,"ENVIADA",1),cells(figure("INGRESOS","ESTADO_COM","cut_id",beforeCut)));
        verifyHistory(a,b,c);
        verifyExports(afterCut);

        var afterVolume=reports.report(access.actor(MANAGER),new Reports.Request("VOLUMEN","2025-07-01","2026-06-30",
            null,null,null,null,null,null,null,null,null,null,null,null,null));
        for(String family:List.of("com","req","cases"))assertEquals(beforeVolume.get(family),afterVolume.get(family),family);
    }

    private void verifyHistory(long a,long b,long c){
        exactTransition(a,"PRESENCIAL","ESTADO_COM","NUEVA","INVALIDA","EXCLUIDOS");
        exactTransition(b,"CORREO",INITIAL,"PENDIENTE","REG-DEMO","INGRESOS");
        exactTransition(c,"TELEFONO","ESTADO_SOL","DEVUELTA","DESCARTADA","INGRESOS");
        assertEquals(of("OFICINA_NACIONAL",1),cells(figure("INGRESOS",INITIAL,"filters",Map.of("CANAL",List.of("TELEFONO")))));
        assertEquals(of("UPR-REQ",1),cells(figure("INGRESOS","SEDE_INICIAL","filters",Map.of("CANAL",List.of("TELEFONO")))));
    }

    private void verifyExports(long cut)throws Exception{
        for(String format:List.of("csv","xlsx","zip")){
            byte[] bytes=export.export(access.actor(MANAGER),format,map("measure","INGRESOS","rows",List.of(INITIAL),
                "period",PERIOD,"cut_id",cut)).bytes();
            if(format.equals("csv"))assertPrivate(bytes);
            else for(var entry:StatExportTest.unzip(bytes).entrySet()){
                if(entry.getKey().endsWith(".xlsx")){
                    for(var sheet:StatExportTest.unzip(entry.getValue()).values())assertPrivate(sheet);
                }else assertPrivate(entry.getValue());
            }
        }
    }

    private void assertPrivate(byte[] bytes){assertFalse(new String(bytes,StandardCharsets.UTF_8).contains(REASON));}

    private void exactTransition(long com,String channel,String dimension,String before,String after,String measure){
        long at=n(db.one("SELECT at_ms FROM milestone WHERE communication_id=? AND event='COM_DESCARTADA'",com),"at_ms");
        var filter=Map.of("CANAL",List.of(channel));
        var earlier=figure("INGRESOS",dimension,"filters",filter,"time_mode","FECHA","as_of",Instant.ofEpochMilli(at-1).toString());
        var exact=figure(measure,dimension,"filters",filter,"time_mode","FECHA","as_of",Instant.ofEpochMilli(at).toString());
        assertEquals(of(before,channel.equals("CORREO")?2:1),cells(earlier));
        assertEquals(channel.equals("CORREO")?of(after,1,"PENDIENTE",1):of(after,1),cells(exact));
    }

    private Map<String,Object> figure(String measure,String dimension,Object... extra){
        return query(MANAGER,measure,List.of(dimension),List.of(),PERIOD,extra);
    }
}
