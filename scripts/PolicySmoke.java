import cl.nexbyte.sigera.Rules;
import cl.nexbyte.sigera.Problem;

/** Comprobacion rapida sin bibliotecas externas. No sustituye Maven, MySQL ni navegador. */
public class PolicySmoke {
    private static void denied(Runnable r){try{r.run();throw new AssertionError("Debio denegar");}catch(Problem expected){}}
    private static void equal(String a,String b){if(!a.equals(b))throw new AssertionError(a+" != "+b);}
    public static void main(String[] args){
        equal(Rules.closure("BAJA",true,false,false,"EN_GESTION",0,false),"PROFESIONAL_BAJA");
        denied(()->Rules.closure("ALTA",true,false,false,"EN_GESTION",0,false));
        equal(Rules.closure("ALTA",true,true,false,"EN_GESTION",0,false),"JEFATURA_PROPIA");
        denied(()->Rules.closure("BAJA",true,true,true,"EN_GESTION",0,false));
        denied(()->Rules.derivable(true,1));Rules.derivable(true,0);
        denied(()->Rules.requirementClosable(1,0,true));Rules.requirementClosable(0,0,true);
        System.out.println("8 comprobaciones de politica: OK. Solo logica pura, no integracion.");
    }
}
