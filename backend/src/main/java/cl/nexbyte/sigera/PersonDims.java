package cl.nexbyte.sigera;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import static cl.nexbyte.sigera.Db.*;

/** Proyección sin identidad de las versiones declaradas y lectura demográfica del Formulario. */
@Service
public class PersonDims {
    private static final ZoneId CHILE=ZoneId.of("America/Santiago");
    private static final String COUNTRIES_SQL="'|"+String.join("|",new TreeSet<>(AdultIdentity.COUNTRIES))
        .replace("'","''")+"|'";
    static final String MARKER="DIMENSIONES_V21";
    private static final int BATCH=500;
    private final Db db;
    private final TransactionTemplate tx;
    private final JsonMapper json=JsonMapper.builder().build();

    public PersonDims(Db db,PlatformTransactionManager manager){
        this.db=db;
        this.tx=new TransactionTemplate(manager);
    }

    record Age(Integer years,String origin){}
    record Person(String role,String region,String commune,Age age,String nationality){}
    record Projection(String region,String commune,String factsRegion,String factsCommune,List<Person> people){}

    static Age age(String birth,String declared,long received,int max){
        LocalDate day=Instant.ofEpochMilli(received).atZone(CHILE).toLocalDate();
        if(!birth.isEmpty()){
            try{
                LocalDate born=LocalDate.parse(birth);
                int years=Period.between(born,day).getYears();
                if(!born.isAfter(day)&&years<=max)return new Age(years,"NACIMIENTO");
            }catch(DateTimeException ignored){/* Solo se admite una fecha estructurada válida. */}
        }
        if(declared.matches("[0-9]{1,3}")){
            int years=Integer.parseInt(declared);
            if(years<=max)return new Age(years,"DECLARADA");
        }
        return new Age(null,null);
    }

    private static Person person(String role,Map<String,Object> data,String regionKey,String birthKey,
                                 boolean structuredNationality,long received){
        String region=OriginRegion.of(data.get(regionKey));
        String commune=commune(region,s(data,"commune"));
        String nation=s(data,"nationality");
        return new Person(role,region,commune,age(s(data,birthKey),s(data,"age_years"),received,
            role.equals("NNA")?17:130),structuredNationality&&AdultIdentity.COUNTRIES.contains(nation)?nation:null);
    }

    private static String commune(String region,String commune){
        return region!=null&&AdultIdentity.validCommune(region,commune)?commune:null;
    }

    @SuppressWarnings("unchecked")
    static Projection project(String source,Map<String,Object> form,long received){
        var people=new ArrayList<Person>();
        boolean intake=source.equals("INGRESO");
        boolean structured=Set.of("1.1","1.2").contains(s(form,"schema_version"))||intake;
        if(intake){
            Object presenter=form.get("presenter");
            if(presenter instanceof Map<?,?> p&&!p.isEmpty())
                people.add(person("REQUIRENTE",(Map<String,Object>)p,"region_code","birth_date",true,received));
        }else{
            people.add(person("REQUIRENTE",form,"region","presenter_birth_date",structured,received));
        }
        for(var p:DeclaredPerson.canonicalList(form.get(intake?"persons":"people"))){
            if(s(p,"role").equals("NNA"))people.add(person("NNA",p,"region_code","birth_date",true,received));
        }
        Person requester=people.isEmpty()||!people.getFirst().role().equals("REQUIRENTE")?null:people.getFirst();
        String facts=OriginRegion.of(form.get("facts_region_code"));
        return new Projection(requester==null?null:requester.region(),requester==null?null:requester.commune(),
            facts,commune(facts,s(form,"facts_commune")),List.copyOf(people));
    }

    void save(String source,long version,long com,long at,long received,Map<String,Object> form){
        Projection p=project(source,form,received);
        long id=db.insert("INSERT INTO stat_declared_version(communication_id,source,source_id,registered_ms,requester_region,requester_commune,facts_region,facts_commune) VALUES(?,?,?,?,?,?,?,?)",
            com,source,version,at,p.region(),p.commune(),p.factsRegion(),p.factsCommune());
        int order=0;
        for(Person person:p.people()){
            db.update("INSERT INTO stat_declared_person(version_id,person_order,person_role,region_code,commune,age_years,age_origin,nationality) VALUES(?,?,?,?,?,?,?,?)",
                id,order++,person.role(),person.region(),person.commune(),person.age().years(),person.age().origin(),person.nationality());
        }
    }

    @SuppressWarnings("unchecked")
    void rebuild(){
        if(done())return;
        for(String source:List.of("FICHA","INGRESO")){
            String from=source.equals("FICHA")?"attention_revision v JOIN requirement r ON r.id=v.requirement_id JOIN communication c ON c.id=r.communication_id":
                "intake_revision v JOIN communication c ON c.id=v.communication_id";
            long cursor=0;
            while(true){
                long after=cursor;
                Long last=tx.execute(status->{
                    db.lock();
                    if(done())return null;
                    var batch=db.rows("SELECT v.id,v.registered_ms,v.data_json,c.id AS com,c.received_ms FROM "+from
                        +" WHERE v.id>? AND NOT EXISTS(SELECT 1 FROM stat_declared_version d WHERE d.source=? AND d.source_id=v.id)"
                        +" ORDER BY v.id LIMIT "+BATCH,after,source);
                    for(var row:batch)save(source,n(row,"id"),n(row,"com"),n(row,"registered_ms"),n(row,"received_ms"),
                        (Map<String,Object>)json.readValue(s(row,"data_json"),Map.class));
                    return batch.isEmpty()?null:n(batch.getLast(),"id");
                });
                if(last==null)break;
                cursor=last;
            }
        }
        tx.executeWithoutResult(status->{
            db.lock();
            if(!done())db.update("INSERT INTO application_marker(code,applied_ms) VALUES(?,?)",MARKER,Lifecycle.now());
        });
    }

    private boolean done(){return db.count("SELECT COUNT(*) FROM application_marker WHERE code=?",MARKER)>0;}

    static String externalAgeSql(String person,int max){
        return "COALESCE(CASE WHEN "+person+".age_at_intake BETWEEN 0 AND "+max+" THEN "+person+".age_at_intake END,"
            +"CASE WHEN "+person+".declared_age BETWEEN 0 AND "+max+" THEN "+person+".declared_age END)";
    }

    static String externalNationalitySql(String person){
        // REPLACE distingue mayúsculas y tildes también en la intercalación de MySQL de la instancia.
        String country="CONCAT('|',a.value_text,'|')";
        return "(SELECT a.value_text FROM external_attribute a WHERE a.person_id="+person+".id AND a.attr_key='nacionalidad'"
            +" AND a.discrepancy=FALSE AND a.source<>'SIN_DATO' AND (a.code_text IS NULL OR a.code_text<>'U')"
            +" AND LOCATE('|',a.value_text)=0 AND CHAR_LENGTH(REPLACE("+COUNTRIES_SQL+","+country+",''))<CHAR_LENGTH("
            +COUNTRIES_SQL+") ORDER BY a.id LIMIT 1)";
    }

    static String bracketSql(String age){
        return "(SELECT i.code FROM stat_catalog_item i JOIN stat_catalog_version v ON v.id=i.version_id"
            +" WHERE v.`current_catalog`='TRAMO_EDAD' AND "+age+" BETWEEN i.range_from AND i.range_to)";
    }

    static String nationalityGroupSql(String nation){
        return "CASE WHEN "+nation+" IS NULL THEN 'SIN_INFORMACION' WHEN "+nation+"='Chile' THEN 'CHILE' ELSE 'EXTRANJERA' END";
    }

    private static String requesterTypeSql(String com,String req,TerritorialDims.Mode mode,Long at){
        if(mode==TerritorialDims.Mode.HISTORICO)return StatValues.codeSql(com,"TIPO_REQUIRENTE",Objects.requireNonNull(at));
        if(mode==TerritorialDims.Mode.VIGENTE)return StatValues.codeSql(com,"TIPO_REQUIRENTE",null);
        return "(SELECT e.code FROM stat_event e WHERE e.communication_id="+com+".id AND e.record_type='COM' AND e.record_id="
            +com+".id AND e.variable='TIPO_REQUIRENTE' AND e.event IN ('SET','STATUS')"
            +(mode==TerritorialDims.Mode.REQ?" AND e.recorded_ms<="+TerritorialDims.constitutedSql(req):"")
            +" ORDER BY e.id "+(mode==TerritorialDims.Mode.REQ?"DESC":"ASC")+" LIMIT 1)";
    }

    private static String requesterField(String com,String externalEvidence,String version,String formField,String declaredField){
        return "CASE WHEN "+externalEvidence+" THEN (SELECT "+formField+" FROM external_person ep"
            +" WHERE ep.communication_id="+com+".id AND ep.person_role='PRESENTADOR' ORDER BY ep.id LIMIT 1)"
            +" WHEN "+com+".external_source IS NULL THEN (SELECT p."+declaredField
            +" FROM stat_declared_person p WHERE p.version_id="+version+" AND p.person_role='REQUIRENTE') END";
    }

    static String requesterSql(String com,String req,TerritorialDims.Mode mode,Long at,String field){
        String type=requesterTypeSql(com,req,mode,at);
        String version=TerritorialDims.versionSql(com,req,mode,at);
        String external=TerritorialDims.externalEvidenceSql(com,req,mode,at);
        String value=switch(field){
            case "age_years" -> {
                String age=requesterField(com,external,version,externalAgeSql("ep",130),"age_years");
                yield "CASE WHEN "+type+"='NNA' AND "+age+">17 THEN NULL ELSE "+age+" END";
            }
            case "nationality" -> requesterField(com,external,version,externalNationalitySql("ep"),"nationality");
            case "state" -> "'INFORMADO'";
            default -> throw new IllegalArgumentException("Dimensión demográfica desconocida.");
        };
        return "CASE WHEN "+type+" IN ('PERSONA_NATURAL_ADULTA','NNA') THEN "+value
            +(field.equals("state")?" ELSE 'NO_APLICA'":"")+" END";
    }

    Map<String,Object> requester(long com,TerritorialDims.Mode mode,Long at){
        return db.one("SELECT "+requesterSql("c","r",mode,at,"state")+" AS applicability,"
            +requesterSql("c","r",mode,at,"age_years")+" AS age_years,"
            +requesterSql("c","r",mode,at,"nationality")+" AS nationality"
            +" FROM communication c LEFT JOIN requirement r ON r.communication_id=c.id WHERE c.id=?",com);
    }

    static String affectedSql(TerritorialDims.Mode mode,Long at){
        String region=TerritorialDims.fieldSql("c","PERSONA","region","ep.person_key");
        String commune=TerritorialDims.fieldSql("c","PERSONA","comuna","ep.person_key");
        String version=TerritorialDims.versionSql("c","r",mode,at);
        String external=TerritorialDims.externalEvidenceSql("c","r",mode,at);
        String chain=" FROM communication c LEFT JOIN requirement r ON r.communication_id=c.id";
        return "SELECT c.id AS com, p.region_code, p.commune, p.age_years, p.nationality"
            +chain
            +" JOIN stat_declared_person p ON p.version_id="+version+" WHERE c.external_source IS NULL AND p.person_role='NNA'"
            +" UNION ALL SELECT c.id,"+region+",COALESCE("+commune+","
            +TerritorialDims.fieldSql("c","PERSONA","commune","ep.person_key")+"),"
            +externalAgeSql("ep",17)+","+externalNationalitySql("ep")
            +chain+" JOIN external_person ep ON ep.communication_id=c.id WHERE "+external+" AND ep.person_role='AFECTADO_NNA'"
            +" UNION ALL SELECT f.communication_id,f.value_text,(SELECT g.value_text FROM external_field g"
            +" WHERE g.communication_id=f.communication_id AND g.field_group='CAMPO'"
            +" AND g.field_key=REPLACE(f.field_key,'-region','-commune') ORDER BY g.id LIMIT 1),NULL,NULL"
            +chain+" JOIN external_field f ON f.communication_id=c.id WHERE "+external
            +" AND f.field_group='CAMPO' AND f.field_key LIKE 'grupo-nna-%-region'";
    }

    List<Map<String,Object>> affected(long com,TerritorialDims.Mode mode,Long at){
        return db.rows("SELECT region_code,commune,age_years,nationality FROM ("+affectedSql(mode,at)
            +") p WHERE p.com=?",com);
    }
}
