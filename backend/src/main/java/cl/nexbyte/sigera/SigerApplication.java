package cl.nexbyte.sigera;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
public class SigerApplication {
    public static void main(String[] args) { SpringApplication.run(SigerApplication.class, args); }

    /** Tareas programadas del servicio (recordatorios). Un proceso de ciclo manual las desactiva con siger.scheduling=false. */
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name="siger.scheduling",havingValue="true",matchIfMissing=true)
    static class Scheduling { }
}
