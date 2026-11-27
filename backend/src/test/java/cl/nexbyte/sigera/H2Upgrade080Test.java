package cl.nexbyte.sigera;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Actualizacion 0.8.0 -> version actual sobre H2 {@code MODE=MySQL} (A00 de 0.9.1): el mismo escenario de
 * {@link MySql84Upgrade080Test}, copiado tabla por tabla a una H2 con el esquema de 0.8.0 y guardado con {@code SCRIPT}
 * de H2 en {@code upgrade/siger-0.8.0-sintetico-h2.sql} (H2 no carga un volcado de mysqldump). Corre siempre, tambien en
 * la suite sobre MySQL (arma su propia aplicacion y no es un contexto de prueba). Mismas comprobaciones: migrate-only,
 * arranque normal, filas por tabla, originales del Formulario y external_* identicos, y el escenario presente.
 */
class H2Upgrade080Test {
    /**
     * H2 en archivo, que se cierra al soltar la ultima conexion: H2 2.4.240 deja inutilizable una restriccion CHECK
     * ("Check constraint invalid ... base de datos cerrada") cuando se cierra la sesion que creo la tabla, lo que en una
     * H2 en memoria con DB_CLOSE_DELAY=-1 rompe toda tabla que cree migrate-only (V25 en adelante). Al reabrir el
     * archivo, las restricciones se recompilan.
     */
    static final String URL="jdbc:h2:file:./target/h2-upgrade-desde080-"+UUID.randomUUID()+"/siger;MODE=MySQL;DATABASE_TO_LOWER=TRUE";
    static final Path SCRIPT=Path.of("src/test/resources/upgrade/siger-0.8.0-sintetico-h2.sql");

    @Test void actualizacionDesdeUnScriptSintetico080()throws Exception{
        MySql84Upgrade080Test.Source source=()->DriverManager.getConnection(URL,"sa","");
        List<String> before;
        try(var c=source.open();var st=c.createStatement()){
            st.execute("RUNSCRIPT FROM '"+SCRIPT.toAbsolutePath().toString().replace("'","''")+"'");
            before=MySql84Upgrade080Test.tables(c,"SELECT table_name FROM information_schema.tables WHERE table_schema=SCHEMA() AND table_type='BASE TABLE'");
            assertTrue(before.contains("communication_original")&&before.contains("stat_cut"),"esquema de 0.8.0");
            assertEquals(4,MySql84UpgradeTest.count(c,"SELECT COUNT(*) FROM communication_original"));
        }
        MySql84Upgrade080Test.upgrade(source,before,extra->{
            var args=new ArrayList<>(List.of("spring.datasource.url="+URL,"spring.datasource.username=sa","spring.datasource.password=",
                "spring.flyway.enabled=true","siger.scheduling=false","siger.data=./target/h2upgrade080-"+UUID.randomUUID()));
            args.addAll(extra);
            return new SpringApplicationBuilder(SigerApplication.class).web(WebApplicationType.NONE).profiles("test").run(args.stream().map(a->"--"+a).toArray(String[]::new));
        });
    }
}
