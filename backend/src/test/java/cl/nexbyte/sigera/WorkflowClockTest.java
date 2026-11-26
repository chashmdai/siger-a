package cl.nexbyte.sigera;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.lang.management.ManagementFactory;
import java.util.List;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Espera entre comandos (Workflow.afterPreviousCommand, condicion del GO de E08 cumplida en E10): sin reloj atrasado no
 * espera ni escribe en el log; con el reloj atrasado termina en el tope de 200 ms y deja una sola linea sin datos (codigo
 * fijo y milisegundos esperados) por episodio, a lo mas una por minuto; mientras falta mas de 1 ms duerme sin consumir CPU.
 */
class WorkflowClockTest {
    ListAppender<ILoggingEvent> log;
    Logger logger;

    @BeforeEach void capture(){
        Workflow.lastWarning=0;
        logger=(Logger)LoggerFactory.getLogger(Workflow.class);
        log=new ListAppender<>();log.start();logger.addAppender(log);
    }
    @AfterEach void restore(){
        logger.detachAppender(log);
        Workflow.lastEnd=Lifecycle.now()-1;
        Workflow.afterPreviousCommand(); // termina el episodio de reloj atrasado de esta prueba
    }
    List<String> lines(){return log.list.stream().map(ILoggingEvent::getFormattedMessage).toList();}

    @Test void relojAtrasadoDejaUnaLineaPorEpisodio(){
        // Reloj al dia: no espera ni escribe.
        Workflow.lastEnd=Lifecycle.now()-1;
        long t0=System.nanoTime();
        Workflow.afterPreviousCommand();
        assertTrue((System.nanoTime()-t0)/1_000_000<50);
        assertEquals(List.of(),lines());
        // Fin del comando anterior 10 s en el futuro (reloj atrasado): espera el tope y escribe una linea.
        Workflow.lastEnd=Lifecycle.now()+10_000;
        var cpu=ManagementFactory.getThreadMXBean();
        long cpu0=cpu.getCurrentThreadCpuTime();
        t0=System.nanoTime();
        Workflow.afterPreviousCommand();
        long waited=(System.nanoTime()-t0)/1_000_000,used=(cpu.getCurrentThreadCpuTime()-cpu0)/1_000_000;
        assertTrue(waited>=Workflow.MAX_WAIT_MS&&waited<Workflow.MAX_WAIT_MS+400,"espera hasta el tope: "+waited+" ms");
        if(cpu.isCurrentThreadCpuTimeSupported())assertTrue(used<waited/2,"duerme mientras falta mas de 1 ms: "+used+" ms de CPU en "+waited+" ms");
        assertEquals(1,lines().size());
        assertTrue(lines().getFirst().matches(Workflow.CLOCK_BEHIND+" \\d+"),lines().getFirst());
        long reported=Long.parseLong(lines().getFirst().split(" ")[1]);
        assertTrue(reported>=Workflow.MAX_WAIT_MS&&reported<=waited,"milisegundos esperados: "+reported);
        // Mismo episodio: el segundo comando no vuelve a escribir (a lo mas una por minuto).
        Workflow.afterPreviousCommand();
        assertEquals(1,lines().size());
        // El reloj se pone al dia y termina el episodio; otro dentro del mismo minuto no escribe (a lo mas una por minuto)...
        Workflow.lastEnd=Lifecycle.now()-1;
        Workflow.afterPreviousCommand();
        Workflow.lastEnd=Lifecycle.now()+10_000;
        Workflow.afterPreviousCommand();
        assertEquals(1,lines().size(),"a lo mas una por minuto");
        // ...y pasado el minuto, un episodio nuevo deja su linea.
        Workflow.lastEnd=Lifecycle.now()-1;
        Workflow.afterPreviousCommand();
        Workflow.lastWarning-=61_000_000_000L;
        Workflow.lastEnd=Lifecycle.now()+10_000;
        Workflow.afterPreviousCommand();
        assertEquals(2,lines().size(),"un episodio nuevo deja su linea");
        assertEquals(ch.qos.logback.classic.Level.WARN,log.list.getLast().getLevel());
    }
    @Test void esperaCortaHastaSuperarElFinAnterior(){
        Workflow.lastEnd=Lifecycle.now()+5;
        Workflow.afterPreviousCommand();
        assertTrue(Lifecycle.now()>Workflow.lastEnd,"el reloj supera el fin del comando anterior");
        assertEquals(List.of(),lines());
    }
}
