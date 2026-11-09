package cl.nexbyte.sigera;

import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;
import static org.junit.jupiter.api.Assertions.*;
import static cl.nexbyte.sigera.FormularioDocs.*;

/** Normalizacion de originales externos y validacion semantica del contrato, sin Spring ni base. */
class ExternalProjectionTest {
    static ExternalOriginal parse(ObjectNode com){return ExternalOriginal.parse(sign(com),trust());}
    static ExternalProjection.Person find(ExternalOriginal o,String key){return o.projection().persons().stream().filter(p->p.key().equals(key)).findFirst().orElseThrow();}
    static void invalid(Consumer<ObjectNode> change){
        var c=cuVulneration();change.accept(c);
        var p=assertThrows(Problem.class,()->parse(c));
        assertEquals(400,p.status);assertTrue(p.getMessage().contains("contrato"),p.getMessage());
    }

    @Test void claveUnicaSinParentesisNiDivisionYRolesSinConfundir(){
        var o=parse(cuVulneration());var c=o.projection().com();
        assertEquals("María José De los Ángeles Del Río González",c.presenter());
        assertEquals("12345678-5",c.identityDocument());
        assertEquals("maria.jose@example.cl",c.contactEmail());assertEquals("+56912345678",c.contactPhone());
        assertEquals("Avenida Irarrázaval 1234, depto. 5, Ñuñoa, Metropolitana de Santiago",c.presenterDomicile());
        assertEquals("Formulario web adulto · Vulneración de derechos",c.subject());
        assertEquals("Relato sintético con tildes: niño, señal, acción.",c.body());
        assertNull(c.contactRestrictions());
        var p=find(o,"presentador-adulto");
        assertTrue(p.authenticated());assertEquals(CU,p.givenSource());assertEquals(CU,p.runSource());assertEquals(Boolean.TRUE,p.runValid());
        assertEquals(List.of("PRESENTADOR","AFECTADO_NNA","ADULTO_RELACIONADO","PERSONA_SENALADA"),o.projection().persons().stream().map(ExternalProjection.Person::role).toList());
        var nna=find(o,"afectado-1");
        assertFalse(nna.authenticated());assertEquals("Tomás",nna.givenNames());assertEquals(9,nna.declaredAge());assertNull(nna.ageAtIntake());assertNull(nna.runNumber());
        // Respuestas, detalles de personas e instituciones integros.
        var f=o.projection().fields();
        assertTrue(f.contains(new ExternalProjection.Field("PERSONA","senalado-1",2,"alias","El vecino")));
        assertTrue(f.contains(new ExternalProjection.Field("INSTITUCION",null,0,"nombre","Escuela Básica Sintética")));
        assertTrue(f.contains(new ExternalProjection.Field("CAMPO",null,0,"riesgo","si")));
    }

    @Test void runDeclaradoSrceiDiscrepanciasYDiccionario(){
        var c=cuVulneration();c.put("tipo","ADULTO_DECLARADO");
        var p=person("presentador-adulto","PRESENTADOR",false,"12.345.678","5",
            "runNumero",attr("12.345.678",DECL,NOCONTR),"runDv",attr("5",DECL,NOCONTR),
            "nombres",attr("ANA MARÍA",SRCEI,CONTR),"primerApellido",attr("PÉREZ",SRCEI,CONTR),"segundoApellido",attr("Soto",DECL,NOCONTR),
            "sexo",attr("Femenino","F",SRCEI,CONTR),"estadoCivil",attr("Conviviente civil","P",SRCEI,CONTR),"nacionalidad",attr("Desconocido","X",SRCEI,CONTR),
            "fechaNacimiento",attr("1990-05-10",SRCEI,CONTR),"fechaDefuncionTruncada",attr("2020-00-00","TRUNCADA",SRCEI,CONTR),
            "|","segundoApellido",attr("SOTTO",SRCEI,CONTR));
        c.withArrayProperty("personas").set(0,p);
        var o=parse(c);var x=find(o,"presentador-adulto");
        assertEquals("ANA MARÍA PÉREZ Soto",o.projection().com().presenter());
        assertEquals(SRCEI,x.givenSource());assertEquals("MIXTA",x.surnameSource());
        assertEquals("12345678",x.runNumber());assertEquals(DECL,x.runSource());assertTrue(x.runValid());
        assertEquals("12345678-5",o.projection().com().identityDocument());
        assertEquals(36,x.ageAtIntake());assertEquals(SRCEI,x.birthSource());
        var disc=x.attributes().stream().filter(ExternalProjection.Attribute::discrepancy).toList();
        assertEquals(1,disc.size());assertEquals("SOTTO",disc.getFirst().value());assertEquals(SRCEI,disc.getFirst().source());
        var meanings=new HashMap<String,String>();x.attributes().forEach(a->{if(!a.discrepancy())meanings.put(a.key(),a.meaning());});
        assertEquals("Femenino",meanings.get("sexo"));assertEquals("Conviviente civil",meanings.get("estadoCivil"));
        assertEquals("Código no reconocido",meanings.get("nacionalidad"));assertEquals("Fecha incompleta",meanings.get("fechaDefuncionTruncada"));
        // El original (valor "Desconocido", codigo "X") se conserva sin cambios junto a su lectura.
        var nationality=x.attributes().stream().filter(a->a.key().equals("nacionalidad")).findFirst().orElseThrow();
        assertEquals("Desconocido",nationality.value());assertEquals("X",nationality.code());
        // Diccionario: U es sin informacion y distinto de un codigo no reconocido; sexo declarado codificado; nacionalidad declarada textual.
        assertEquals("Sin información",ExternalProjection.meaning("sexo","Desconocido","U",SRCEI));
        assertEquals("Sin información",ExternalProjection.meaning("estadoCivil",null,"U",SRCEI));
        assertEquals("Sin información",ExternalProjection.meaning("sexo","U",null,DECL));
        assertEquals("Código no reconocido",ExternalProjection.meaning("estadoCivil","Desconocido","Z",SRCEI));
        assertEquals("Código no reconocido",ExternalProjection.meaning("nacionalidad","Desconocido","Q",SRCEI));
        assertEquals("Separado judicialmente",ExternalProjection.meaning("estadoCivil","x","J",SRCEI));
        assertEquals("Nacionalizado",ExternalProjection.meaning("nacionalidad","x","N",SRCEI));
        assertEquals("Masculino",ExternalProjection.meaning("sexo","M",null,DECL));
        assertNull(ExternalProjection.meaning("nacionalidad","Peruana",null,DECL));
        // RUN que no cumple modulo 11: se conserva marcado y no se proyecta como identificacion.
        var bad=cuVulneration();bad.put("tipo","ADULTO_DECLARADO");
        bad.withArrayProperty("personas").set(0,person("presentador-adulto","PRESENTADOR",false,"12345678","K","runNumero",attr("12345678",DECL,NOCONTR),"runDv",attr("K",DECL,NOCONTR),"primerNombre",attr("Ana",DECL,NOCONTR)));
        var b=parse(bad);
        assertEquals(Boolean.FALSE,find(b,"presentador-adulto").runValid());assertNull(b.projection().com().identityDocument());
    }

    @Test void documentoExtranjeroSinRunYNnaSinRun(){
        var c=cuVulneration();c.put("tipo","ADULTO_DECLARADO");
        c.withArrayProperty("personas").set(0,person("presentador-adulto","PRESENTADOR",false,null,null,
            "tipoIdentificacion",attr("DOCUMENTO_EXTRANJERO",DECL,NOCONTR),"tipoDocumento",attr("Pasaporte",DECL,NOCONTR),
            "numeroDocumento",attr("AB/123-xyz.45",DECL,NOCONTR),"nacionalidad",attr("Peruana",DECL,NOCONTR),
            "primerNombre",attr("Lucía",DECL,NOCONTR),"primerApellido",attr("Quispe",DECL,NOCONTR)));
        var o=parse(c);var p=find(o,"presentador-adulto");
        assertEquals("Pasaporte AB/123-xyz.45",o.projection().com().identityDocument());
        assertEquals("DOCUMENTO_EXTRANJERO",p.identificationType());assertNull(p.runNumber());assertEquals("Lucía Quispe",o.projection().com().presenter());
        // NNA, principal inseguro y alternativo valido (unica combinacion con "unsafe" que admite el Formulario):
        // aceptaCanalDigital=false del recorrido NNA no se lee como rechazo, y el alternativo no queda anulado.
        var unsafe=new LinkedHashMap<String,String>();
        unsafe.put("nna-region","05");unsafe.put("nna-commune","Viña del Mar");unsafe.put("nna-story","Relato NNA");unsafe.put("nna-primary-method","unsafe");
        unsafe.put("nna-extra-contact","true");unsafe.put("nna-secondary-method","email");unsafe.put("nna-secondary-email","camila@example.cl");
        unsafe.put("nna-contact-instructions","Solo después de clases");
        var n=com("NNA",RECEIVED,unsafe,List.of(person("presentador-nna","PRESENTADOR",false,null,null,"primerNombre",attr("Camila",DECL,NOCONTR),"edad",attr("14",DECL,NOCONTR))),List.of(),List.of());
        assertFalse(n.path("aceptaCanalDigital").asBoolean());
        var nc=parse(n).projection().com();
        assertEquals("Camila",nc.presenter());assertNull(nc.identityDocument());
        assertEquals("camila@example.cl",nc.contactEmail());assertNull(nc.contactPhone());
        assertEquals("Formulario web NNA",nc.subject());assertEquals("Relato NNA",nc.body());assertEquals("Viña del Mar, Valparaíso",nc.presenterDomicile());
        assertEquals("Contacto principal: No tiene una forma segura de contacto; Contacto alternativo: Correo electrónico camila@example.cl",nc.contact());
        assertEquals("Contacto principal: sin forma segura de contacto; existe un contacto alternativo informado.\nPrecauciones para el contacto: Solo después de clases",nc.contactRestrictions());
        assertFalse(nc.contactRestrictions().contains("canal digital"));
        // NNA con celular principal (calificado por nna-safe) y red social alternativa: celular del medio elegido, sin restriccion de inseguridad.
        var safe=new LinkedHashMap<String,String>();
        safe.put("nna-region","13");safe.put("nna-story","Relato");safe.put("nna-primary-method","celular");safe.put("nna-primary-celular","+56987654321");safe.put("nna-safe","unsure");
        safe.put("nna-extra-contact","true");safe.put("nna-secondary-method","social");safe.put("nna-secondary-network","otra");safe.put("nna-secondary-other-network","Mastodon");safe.put("nna-secondary-handle","@nna");
        var sc2=parse(com("NNA",RECEIVED,safe,List.of(person("presentador-nna","PRESENTADOR",false,null,null,"primerNombre",attr("Leo",DECL,NOCONTR))),List.of(),List.of())).projection().com();
        assertEquals("+56987654321",sc2.contactPhone());assertNull(sc2.contactEmail());
        assertEquals("Contacto principal: Celular +56987654321; Contacto alternativo: Red social Mastodon @nna",sc2.contact());
        assertEquals("Seguridad del contacto principal: No está seguro o segura.",sc2.contactRestrictions());
        // Solo principal inseguro, sin alternativo.
        var none=new LinkedHashMap<String,String>();none.put("nna-region","13");none.put("nna-story","Relato");none.put("nna-primary-method","unsafe");
        assertEquals("No tiene una forma segura de contacto.",parse(com("NNA",RECEIVED,none,List.of(person("presentador-nna","PRESENTADOR",false,null,null,"primerNombre",attr("Leo",DECL,NOCONTR))),List.of(),List.of())).projection().com().contactRestrictions());

        // Solicitud estadistica, consulta general y entrevista: su texto es una solicitud, no un relato de hechos.
        for(var request:List.of(Map.of("tema","estadisticas","stats-confirm","true","stats-descripcion","Ingresos por región 2024","stats-periodo","2024"),
                                Map.of("tema","general","consulta-confirmada","true","consulta-general-detalle","¿Cómo presento una denuncia?"),
                                Map.of("tema","entrevista","entrevista-motivo","Tesis de magíster","entrevista-rol","estudiante"))){
            var stats=cuVulneration();var f=stats.withObjectProperty("datos").withObjectProperty("campos");f.removeAll();
            f.put("motivo","consulta");f.put("adult-region","13");request.forEach(f::put);
            stats.withObjectProperty("datos").withArrayProperty("personas").removeAll();stats.withObjectProperty("datos").withArrayProperty("instituciones").removeAll();
            stats.withArrayProperty("personas").removeAll();stats.withArrayProperty("personas").add(cuVulneration().path("personas").get(0));
            var o2=parse(stats);var sc=o2.projection().com();
            assertEquals("",sc.body(),request.get("tema"));
            var text=request.entrySet().stream().filter(e->e.getKey().endsWith("descripcion")||e.getKey().endsWith("detalle")||e.getKey().endsWith("motivo")).findFirst().orElseThrow();
            assertTrue(o2.projection().fields().contains(new ExternalProjection.Field("CAMPO",null,0,text.getKey(),text.getValue())));
        }
        // Vulneracion y promocion si llevan relato de hechos ("relato").
        assertEquals("Relato sintético con tildes: niño, señal, acción.",parse(cuVulneration()).projection().com().body());
    }

    @Test void edadCalculadaCumpleanosVeintinueveFebreroYZonaSantiago(){
        assertEquals(15,ExternalProjection.age(LocalDate.of(2010,11,5),LocalDate.of(2026,11,4)));   // cumpleanos pendiente
        assertEquals(16,ExternalProjection.age(LocalDate.of(2010,11,4),LocalDate.of(2026,11,4)));   // cumpleanos del dia
        assertEquals(8,ExternalProjection.age(LocalDate.of(2016,2,29),LocalDate.of(2025,2,28)));
        assertEquals(9,ExternalProjection.age(LocalDate.of(2016,2,29),LocalDate.of(2025,3,1)));
        assertEquals(8,ExternalProjection.age(LocalDate.of(2016,2,29),LocalDate.of(2024,2,29)));
        assertNull(ExternalProjection.date("2010-00-00"));assertNull(ExternalProjection.date("2010-02-30"));assertNull(ExternalProjection.date("10/05/2010"));
        // 2026-03-01T02:00Z es 28 de febrero en Santiago: aun no cumple 16.
        var c=com("NNA",Instant.parse("2026-03-01T02:00:00Z"),Map.of("nna-region","13"),List.of(person("presentador-nna","PRESENTADOR",false,null,null,
            "primerNombre",attr("Ana",DECL,NOCONTR),"fechaNacimiento",attr("2010-03-01",DECL,NOCONTR),"edad",attr("16",DECL,NOCONTR),
            "fechaNacimientoTruncada",attr("2010-03-00","TRUNCADA",SRCEI,CONTR))),List.of(),List.of());
        var p=parse(c).projection().persons().getFirst();
        assertEquals(15,p.ageAtIntake());assertEquals(16,p.declaredAge());assertEquals(DECL,p.birthSource());
        // Solo fecha truncada: sin edad exacta.
        var t=com("NNA",RECEIVED,Map.of("nna-region","13"),List.of(person("presentador-nna","PRESENTADOR",false,null,null,
            "primerNombre",attr("Ana",DECL,NOCONTR),"fechaNacimientoTruncada",attr("2010-03-00","TRUNCADA",SRCEI,CONTR))),List.of(),List.of());
        assertNull(parse(t).projection().persons().getFirst().ageAtIntake());
    }

    @Test void proyeccionesLargasSinPerderContenido(){
        var c=cuVulneration();
        var p=(ObjectNode)c.withArrayProperty("personas").get(0);
        p.withObjectProperty("atributos").set("nombres",attr("Á".repeat(250),CU,AUTH));
        c.withObjectProperty("datos").withObjectProperty("campos").put("relato","r".repeat(5000));
        c.withObjectProperty("datos").withObjectProperty("campos").put("email","x".repeat(196)+"@e.cl");
        var o=parse(c);var com=o.projection().com();
        assertEquals(200,com.presenter().length());assertTrue(com.presenter().endsWith("…"));
        assertEquals("Á".repeat(250),find(o,"presentador-adulto").givenNames());
        assertEquals(5000,com.body().length());
        assertNull(com.contactEmail());
        assertTrue(o.projection().fields().stream().anyMatch(f->f.key().equals("email")&&f.value().length()==201));
    }

    @Test void validacionSemanticaRechazaIncompatiblesYAdmiteOpcionalesYExtensiones(){
        invalid(c->c.withArrayProperty("personas").add(((ObjectNode)cuVulneration().path("personas").get(0)).deepCopy().put("id","otro")));      // dos presentadores
        invalid(c->((ObjectNode)c.withArrayProperty("personas").get(0)).put("rol","OTRO"));                                       // rol no admitido
        invalid(c->((ObjectNode)c.withArrayProperty("personas").get(0)).put("autenticada",false));                                // ADULTO_CU sin autenticacion
        invalid(c->c.put("tipo","ADULTO_DECLARADO"));                                                                     // CU en ingreso declarado
        invalid(c->((ObjectNode)c.withArrayProperty("personas").get(1)).withObjectProperty("atributos").set("x",attr("v",SRCEI,AUTH)));   // nivel autenticado sin CU
        invalid(c->((ObjectNode)c.withArrayProperty("personas").get(1)).withObjectProperty("atributos").set("x",attr("v","OTRA",NOCONTR)));// fuente desconocida
        invalid(c->((ObjectNode)c.withArrayProperty("personas").get(0)).put("runNumero","11111111"));                             // RUN CU inconsistente
        invalid(c->c.withObjectProperty("datos").withArrayProperty("personas").addObject().put("personaId","fantasma").putObject("detalles"));
        invalid(c->c.withObjectProperty("datos").withArrayProperty("adjuntos").add(UUID.randomUUID().toString()));                        // referencia sin descriptor
        invalid(c->{String id=UUID.randomUUID().toString();c.withObjectProperty("datos").withArrayProperty("adjuntos").add(id);          // tipo MIME sobre el limite de columna
            var d=c.withArrayProperty("adjuntos").addObject();d.put("id",id);d.put("nombre","a.pdf");d.put("tamanio",1);d.put("sha256","a".repeat(64));d.put("tipo","application/"+"x".repeat(250));d.put("estado","VALIDADO");});
        invalid(c->c.put("aceptaCanalDigital",false));                                                                    // adulto sin canal digital
        invalid(c->c.withObjectProperty("datos").withObjectProperty("campos").put("motivo",1));
        // Fuente y nivel deben coincidir con el contrato (CU autenticada, SRCeI contrastado, declaracion no contrastada).
        invalid(c->((ObjectNode)c.withArrayProperty("personas").get(1)).withObjectProperty("atributos").set("x",attr("v",SRCEI,NOCONTR)));
        invalid(c->((ObjectNode)c.withArrayProperty("personas").get(1)).withObjectProperty("atributos").set("x",attr("v",DECL,CONTR)));
        invalid(c->((ObjectNode)c.withArrayProperty("personas").get(0)).withObjectProperty("atributos").set("nombres",attr("María",CU,NOCONTR)));
        invalid(c->((ObjectNode)c.withArrayProperty("personas").get(0)).withObjectProperty("atributos").set("sexo",attr("Femenino","F",CU,CONTR)));
        invalid(c->((ObjectNode)c.withArrayProperty("personas").get(1)).withObjectProperty("discrepancias").set("x",attr("v",SRCEI,"OTRO_NIVEL")));
        invalid(c->((ObjectNode)c.withArrayProperty("personas").get(1)).withObjectProperty("atributos").set("x",attr(null,SIN,CONTR)));
        invalid(c->((ObjectNode)c.withArrayProperty("personas").get(1)).withObjectProperty("atributos").set("x",attr(null,SIN,AUTH)));
        // RUN autenticado que no cumple modulo 11 o con formato invalido: se rechaza, no se acepta en silencio.
        invalid(c->{var p=(ObjectNode)c.withArrayProperty("personas").get(0);p.put("runDv","4");p.withObjectProperty("atributos").set("runDv",attr("4",CU,AUTH));});
        invalid(c->{var p=(ObjectNode)c.withArrayProperty("personas").get(0);p.put("runNumero","12.345.678");p.withObjectProperty("atributos").set("runNumero",attr("12.345.678",CU,AUTH));});
        invalid(c->{var p=(ObjectNode)c.withArrayProperty("personas").get(0);p.put("runNumero","012345678");p.withObjectProperty("atributos").set("runNumero",attr("012345678",CU,AUTH));});
        // SIN_DATO con nivel no verificado se admite; RUN autenticado con K minuscula valida tambien.
        var sinDato=cuVulneration();((ObjectNode)sinDato.withArrayProperty("personas").get(1)).withObjectProperty("atributos").set("fechaNacimiento",attr(null,SIN,NOCONTR));
        assertNull(find(parse(sinDato),"afectado-1").birthDate());
        var k=cuVulneration();var kp=(ObjectNode)k.withArrayProperty("personas").get(0);
        kp.put("runNumero","10000013");kp.put("runDv","k");kp.withObjectProperty("atributos").set("runNumero",attr("10000013",CU,AUTH));kp.withObjectProperty("atributos").set("runDv",attr("k",CU,AUTH));
        assertEquals("10000013-K",parse(k).projection().com().identityDocument());
        invalid(c->{for(int i=0;i<27;i++)c.withArrayProperty("personas").add(person("relacionado-x"+i,"ADULTO_RELACIONADO",false,null,null));}); // 31
        // 30 personas, campo desconocido en comunicacion y respuesta nueva: se admiten y conservan.
        var c=cuVulneration();
        for(int i=0;i<26;i++)c.withArrayProperty("personas").add(person("relacionado-"+(i+2),"ADULTO_RELACIONADO",false,null,null,"primerNombre",attr("Persona "+i,DECL,NOCONTR)));
        c.put("extensionFutura","valor");c.withObjectProperty("datos").withObjectProperty("campos").put("respuesta-nueva","sí");
        var o=parse(c);
        assertEquals(30,o.projection().persons().size());
        assertTrue(o.document().contains("extensionFutura"));
        assertTrue(o.projection().fields().contains(new ExternalProjection.Field("CAMPO",null,0,"respuesta-nueva","sí")));
    }
}
