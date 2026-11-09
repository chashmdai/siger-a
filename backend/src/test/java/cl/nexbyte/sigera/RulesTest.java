package cl.nexbyte.sigera;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RulesTest {
    @Test void bajaPermiteResponsablePeroNoMedia(){
        assertEquals("PROFESIONAL_BAJA",Rules.closure("BAJA",true,false,false,"EN_GESTION",0,false));
        assertThrows(Problem.class,()->Rules.closure("MEDIANA",true,false,false,"EN_GESTION",0,false));
        assertThrows(Problem.class,()->Rules.closure("",true,false,false,"EN_GESTION",0,false));
    }
    @Test void jefaturaPropiaYSubroganteNoSonLoMismo(){
        assertEquals("JEFATURA_PROPIA",Rules.closure("ALTA",true,true,false,"EN_GESTION",0,false));
        assertThrows(Problem.class,()->Rules.closure("BAJA",true,true,true,"EN_GESTION",0,false));
        assertEquals("JEFATURA",Rules.closure("ALTA",false,true,false,"EN_REVISION",0,false));
    }
    @Test void obligatoriasNoSeEvadenConCierresEspeciales(){
        assertThrows(Problem.class,()->Rules.closure("BAJA",true,false,false,"EN_GESTION",1,false));
        assertThrows(Problem.class,()->Rules.derivable(true,1));
        assertDoesNotThrow(()->Rules.derivable(true,0));
    }
    @Test void solicitarTerminoEsFacultadDistintaDeConfirmar(){
        // El responsable sin jefatura no confirma MEDIANA, ALTA ni sin clasificar, pero si puede solicitar el termino.
        for(String level:new String[]{"MEDIANA","ALTA",""})assertThrows(Problem.class,()->Rules.closure(level,true,false,false,"EN_GESTION",0,false),level);
        assertDoesNotThrow(()->Rules.closeRequest(true,"EN_GESTION",0,false));
        assertDoesNotThrow(()->Rules.closeRequest(true,"EN_ESPERA",0,false));
        assertThrows(Problem.class,()->Rules.closure("BAJA",true,false,true,"EN_GESTION",0,false),"el subrogante no autoconfirma");
        assertEquals(403,assertThrows(Problem.class,()->Rules.closeRequest(false,"EN_GESTION",0,false)).status);
        assertThrows(Problem.class,()->Rules.closeRequest(true,"EN_REVISION",0,false));
        assertThrows(Problem.class,()->Rules.closeRequest(true,"CERRADO",0,false));
        assertThrows(Problem.class,()->Rules.closeRequest(true,"EN_GESTION",1,false));
        assertThrows(Problem.class,()->Rules.closeRequest(true,"EN_GESTION",0,true));
    }
    @Test void reqNecesitaCierreHumanoSeparado(){
        assertThrows(Problem.class,()->Rules.requirementClosable(1,0,true));
        assertThrows(Problem.class,()->Rules.requirementClosable(0,1,true));
        assertThrows(Problem.class,()->Rules.requirementClosable(0,0,false));
        assertDoesNotThrow(()->Rules.requirementClosable(0,0,true));
    }
}
