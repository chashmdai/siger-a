package cl.nexbyte.sigera;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
public class SigerApplication {
    public static void main(String[] args) { SpringApplication.run(SigerApplication.class, args); }

    // Recurso normal bajo BOOT-INF/classes/siger; no depende del META-INF raiz del JAR.
    @org.springframework.context.annotation.Bean
    org.springframework.boot.info.BuildProperties buildProperties() throws java.io.IOException {
        var resource = new org.springframework.core.io.ClassPathResource("siger/build-info.properties");
        var loaded = org.springframework.core.io.support.PropertiesLoaderUtils.loadProperties(resource);
        // El plugin Maven escribe build.version, pero BuildProperties espera version sin prefijo.
        var entries = new java.util.Properties();
        for (var key : loaded.stringPropertyNames()) {
            if (key.startsWith("build.")) {
                entries.setProperty(key.substring("build.".length()), loaded.getProperty(key));
            }
        }
        var version = entries.getProperty("version");
        if (version == null || version.isBlank()) {
            throw new IllegalStateException("Falta build.version en los metadatos del JAR de SIGER.");
        }
        return new org.springframework.boot.info.BuildProperties(entries);
    }

    /** Tareas programadas del servicio (recordatorios). Un proceso de ciclo manual las desactiva con siger.scheduling=false. */
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name="siger.scheduling",havingValue="true",matchIfMissing=true)
    static class Scheduling { }
}
