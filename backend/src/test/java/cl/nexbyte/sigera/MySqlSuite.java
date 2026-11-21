package cl.nexbyte.sigera;

import java.sql.DriverManager;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;

/**
 * Modo "suite sobre MySQL" (G13): con -Dsiger.mysql.suite=true y un MySQL 8.4 AISLADO y desechable
 * (-Dsiger.mysql.base=jdbc:mysql://127.0.0.1:puerto, -Dsiger.mysql.user, -Dsiger.mysql.password), cada contexto de prueba
 * de Spring que usa una H2 en memoria ({@code jdbc:h2:mem:<nombre>}) pasa a un esquema {@code siger_a_mysql_<nombre>}, que
 * se recrea vacio la primera vez que se usa en la JVM (la misma vida que una H2 con DB_CLOSE_DELAY=-1). Tiene la misma
 * precedencia que {@code @DynamicPropertySource}: supera a las propiedades en linea de {@code @SpringBootTest}.
 * Sin esas propiedades no hace nada y la suite sigue en H2. Las pruebas que arman su propia aplicacion
 * (FormularioServiceTest, FormularioRunOnceTest) no son contextos de prueba y siguen en H2.
 */
public class MySqlSuite implements ContextCustomizerFactory {
    static final Pattern H2=Pattern.compile("jdbc:h2:mem:([A-Za-z0-9_]+)(;.*)?");
    static final Set<String> CREATED=Collections.synchronizedSet(new HashSet<>());

    static boolean enabled(){
        String base=System.getProperty("siger.mysql.base");
        return Boolean.getBoolean("siger.mysql.suite")&&base!=null&&base.startsWith("jdbc:mysql://127.0.0.1:");
    }

    @Override public ContextCustomizer createContextCustomizer(Class<?> testClass,List<ContextConfigurationAttributes> attributes){
        return enabled()?new Customizer():null;
    }

    static String schema(String h2){
        String name=("siger_a_mysql_"+h2.toLowerCase(Locale.ROOT)).replaceAll("[^a-z0-9_]","_");
        if(name.length()>48)throw new IllegalStateException("Nombre de esquema demasiado largo: "+name);
        return name;
    }

    static void recreate(String schema)throws Exception{
        if(!CREATED.add(schema))return;
        String base=System.getProperty("siger.mysql.base");
        try(var c=DriverManager.getConnection(base+"/?sslMode=REQUIRED",System.getProperty("siger.mysql.user"),System.getProperty("siger.mysql.password"));
            var st=c.createStatement()){
            st.execute("DROP DATABASE IF EXISTS `"+schema+"`");
            st.execute("CREATE DATABASE `"+schema+"` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }
    }

    /** Sin estado: todos los contextos comparten el mismo personalizador y la cache de contextos no cambia. */
    static final class Customizer implements ContextCustomizer {
        @Override public void customizeContext(ConfigurableApplicationContext context,MergedContextConfiguration config){
            var env=context.getEnvironment();
            var m=H2.matcher(env.getProperty("spring.datasource.url",""));
            if(!m.matches())return;
            String schema=schema(m.group(1));
            try{recreate(schema);}catch(Exception e){throw new IllegalStateException("No se pudo preparar el esquema "+schema,e);}
            env.getPropertySources().addFirst(new MapPropertySource("siger-mysql-suite",Map.of(
                "spring.datasource.url",System.getProperty("siger.mysql.base")+"/"+schema+"?sslMode=REQUIRED&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true",
                "spring.datasource.username",System.getProperty("siger.mysql.user"),
                "spring.datasource.password",System.getProperty("siger.mysql.password"))));
        }
        @Override public boolean equals(Object o){return o instanceof Customizer;}
        @Override public int hashCode(){return Customizer.class.hashCode();}
    }
}
