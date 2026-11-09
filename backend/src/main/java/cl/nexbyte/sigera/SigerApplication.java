package cl.nexbyte.sigera;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SigerApplication {
    public static void main(String[] args) { SpringApplication.run(SigerApplication.class, args); }
}
