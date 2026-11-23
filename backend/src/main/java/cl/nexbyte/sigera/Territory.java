package cl.nexbyte.sigera;

import java.util.*;
import static cl.nexbyte.sigera.Db.*;
import static cl.nexbyte.sigera.Problem.require;

/**
 * Cobertura territorial (0.7.0, D18-D19, gate T01). Una region del catalogo del Formulario esta atendida hoy por cada
 * sede ACTIVA cuya region fisica es esa o que la cubre con un periodo abierto de cobertura adicional, y por el nivel
 * central si su cobertura (periodo abierto con scope_id nulo) la incluye. Las sedes PROYECTADA se informan solo como
 * dato (su cobertura rige al activarse) y una sede CERRADA no atiende. Regla unica para los comandos (SCOPE_COVERAGE,
 * CENTRAL_COVERAGE, SCOPE_CLOSE, SCOPE_UPDATE), la vista previa de TI, el panel Estructura y el aviso REGION_SIN_SEDE.
 * Solo codigos de region y de sede: ningun dato de personas.
 */
final class Territory {
    private Territory(){}
    static final String CENTRAL="NIVEL_CENTRAL",WARNING="REGION_SIN_SEDE";

    /** Sede regional con su estado, region fisica y cobertura adicional abierta (en el orden del catalogo). */
    record Site(long id,String code,String state,String region,List<String> coverage){
        boolean active(){return state.equals(Access.ACTIVE);}
        boolean covers(String r){return r.equals(region)||coverage.contains(r);}
    }
    /** Estado territorial completo: sedes regionales (por id) y cobertura abierta del nivel central. */
    record Snapshot(Map<Long,Site> sites,List<String> central){
        Snapshot with(Site s){var m=new LinkedHashMap<>(sites);m.put(s.id(),s);return new Snapshot(m,central);}
        Site site(long id){var s=sites.get(id);if(s==null)throw new Problem(404,"Sede no encontrada.");return s;}
        Snapshot withCoverage(long id,List<String> regions){var s=site(id);return with(new Site(id,s.code(),s.state(),s.region(),ordered(regions)));}
        Snapshot withRegion(long id,String region){var s=site(id);return with(new Site(id,s.code(),s.state(),region,s.coverage()));}
        Snapshot closing(long id){var s=site(id);return with(new Site(id,s.code(),Access.CLOSED,s.region(),List.of()));}
        Snapshot withCentral(List<String> regions){return new Snapshot(sites,ordered(regions));}
        /** Cobertura adicional abierta de la sede (vacia si no es sede). */
        List<String> coverage(long id){var s=sites.get(id);return s==null?List.of():s.coverage();}
        /** Codigos de las sedes ACTIVA que atienden la region. */
        List<String> activeAt(String r){return sites.values().stream().filter(s->s.active()&&s.covers(r)).map(Site::code).toList();}
        /** Codigos de las sedes PROYECTADA que la atenderan al activarse (solo dato). */
        List<String> projectedAt(String r){return sites.values().stream().filter(s->s.state().equals(Access.PROJECTED)&&s.covers(r)).map(Site::code).toList();}
        boolean attended(String r){return central.contains(r)||!activeAt(r).isEmpty();}
        /** Regiones que hoy atiende la sede (fisica primero, luego la adicional) o, con scope nulo, el nivel central. */
        List<String> attendedBy(Long scope){
            if(scope==null)return central;
            var s=site(scope);if(!s.active())return List.of();
            var out=new ArrayList<String>();out.add(s.region());for(var r:s.coverage())if(!r.equals(s.region()))out.add(r);return out;
        }
    }

    static Snapshot load(Db db){
        Map<Long,List<String>> open=new HashMap<>();List<String> central=new ArrayList<>();
        for(var c:db.rows("SELECT scope_id,region_code FROM territorial_coverage WHERE until_ms IS NULL ORDER BY id")){
            if(c.get("scope_id")==null)central.add(s(c,"region_code"));else open.computeIfAbsent(n(c,"scope_id"),k->new ArrayList<>()).add(s(c,"region_code"));
        }
        var sites=new LinkedHashMap<Long,Site>();
        for(var r:db.rows("SELECT id,code,state,region_code FROM org_scope WHERE regional=TRUE ORDER BY id"))
            sites.put(n(r,"id"),new Site(n(r,"id"),s(r,"code"),s(r,"state"),s(r,"region_code"),ordered(open.getOrDefault(n(r,"id"),List.of()))));
        return new Snapshot(sites,ordered(central));
    }

    /** Codigos en el orden del catalogo del Formulario (de norte a sur), sin repetir. */
    static List<String> ordered(Collection<String> regions){
        var order=new ArrayList<>(AdultIdentity.REGIONS.keySet());
        return regions.stream().distinct().sorted(Comparator.comparingInt(r->order.indexOf(r)<0?Integer.MAX_VALUE:order.indexOf(r))).toList();
    }
    static String name(String region){return AdultIdentity.REGIONS.getOrDefault(region,region)+" ("+region+")";}

    /**
     * Lista de regiones pedida por TI (cobertura adicional o del nivel central): obligatoria (puede ser vacia), codigos
     * del catalogo y sin repetir. Devuelve los codigos en el orden del catalogo.
     */
    static List<String> regions(Input in,String key){
        var list=in.strings(key,AdultIdentity.REGIONS.size());
        if(list==null)throw new Problem(400,"Falta "+key);
        for(var r:list)require(AdultIdentity.validRegion(r),"Region invalida: "+r+" (codigo del Formulario, por ejemplo 05).");
        require(new HashSet<>(list).size()==list.size(),"Una region figura dos veces en la cobertura.");
        return ordered(list);
    }

    /**
     * Efecto territorial de pasar de before a after sobre la sede (o el nivel central, scope nulo): por cada region que
     * hoy atiende, quien la atendera despues (sedes ACTIVA y nivel central) o si queda sin atencion; las sedes
     * PROYECTADA van solo como dato.
     */
    static List<Map<String,Object>> effect(Snapshot before,Snapshot after,Long scope){
        var out=new ArrayList<Map<String,Object>>();
        for(var r:before.attendedBy(scope)){
            var row=new LinkedHashMap<String,Object>();
            var active=after.activeAt(r);boolean central=after.central().contains(r);
            row.put("region",r);row.put("attended_by",active);row.put("central",central);
            row.put("projected",after.projectedAt(r));row.put("unattended",active.isEmpty()&&!central);
            out.add(row);
        }
        return out;
    }
    /** Regiones que quedan sin atencion, en el orden del efecto. */
    static List<String> unattended(List<Map<String,Object>> effect){
        return effect.stream().filter(e->Boolean.TRUE.equals(e.get("unattended"))).map(e->s(e,"region")).toList();
    }
    /**
     * Confirmacion de la orden (D19): si alguna region queda sin atencion, regiones_sin_sede debe ser exactamente la
     * lista que calcula el servidor (mismo orden); sin regiones afectadas no se envia. Cualquier diferencia es 409: la
     * situacion cambio y TI revisa el efecto antes de confirmar.
     */
    static void confirm(Input in,List<Map<String,Object>> effect){
        var expected=unattended(effect);var given=in.strings("regiones_sin_sede",AdultIdentity.REGIONS.size());
        String names=expected.isEmpty()?"ninguna":String.join(", ",expected.stream().map(Territory::name).toList());
        if(given==null)require(expected.isEmpty(),"El cambio deja sin atención: "+names+". Confirma expresamente las regiones que quedan sin atención y registra la decisión institucional en el fundamento.");
        else require(given.equals(expected),"Las regiones que quedan sin atención cambiaron (ahora: "+names+"). Revisa el efecto territorial antes de confirmar.");
    }
    /** Texto de auditoria del efecto, solo con codigos de region y de sede (vacio si no atiende regiones). */
    static String describe(List<Map<String,Object>> effect){
        if(effect.isEmpty())return "";
        var parts=new ArrayList<String>();
        for(var e:effect){
            @SuppressWarnings("unchecked") var who=new ArrayList<>((List<String>)e.get("attended_by"));if(Boolean.TRUE.equals(e.get("central")))who.add(CENTRAL);
            @SuppressWarnings("unchecked") var projected=(List<String>)e.get("projected");
            parts.add(s(e,"region")+(who.isEmpty()?" sin atencion":" sigue atendida por "+String.join(", ",who))+(projected.isEmpty()?"":" (proyectadas: "+String.join(", ",projected)+")"));
        }
        return "Efecto territorial: "+String.join("; ",parts)+".";
    }
    static String codes(List<String> regions){return regions.isEmpty()?"ninguna":String.join(",",regions);}

    /**
     * Aplica el cambio de cobertura de la sede (o del nivel central, scope nulo): termina los periodos abiertos de las
     * regiones que salen y abre uno por cada region nueva, con actor, instante y fundamento. Nunca borra.
     */
    static void apply(Db db,long actor,Long scope,List<String> current,List<String> wanted,String basis,long now){
        String owner=scope==null?"scope_id IS NULL":"scope_id=?";
        for(var r:current)if(!wanted.contains(r)){
            List<Object> args=new ArrayList<>(List.of(now,actor,basis));if(scope!=null)args.add(scope);args.add(r);
            db.update("UPDATE territorial_coverage SET until_ms=?,until_by=?,until_basis=? WHERE "+owner+" AND region_code=? AND until_ms IS NULL",args.toArray());
        }
        for(var r:wanted)if(!current.contains(r))
            db.update("INSERT INTO territorial_coverage(scope_id,region_code,from_ms,from_by,from_basis) VALUES(?,?,?,?,?)",scope,r,now,actor,basis);
    }
    /** Revision de la cobertura del nivel central: periodos abiertos mas terminos; solo crece (concurrencia optimista). */
    static long centralRevision(Db db){
        return db.count("SELECT COUNT(*)+COUNT(until_ms) FROM territorial_coverage WHERE scope_id IS NULL");
    }
    /** Periodos de cobertura (historia), del mas reciente al mas antiguo; scope nulo = nivel central. */
    static List<Map<String,Object>> history(Db db,Long scope){
        return db.rows("SELECT id,region_code,from_ms,from_by,from_basis,until_ms,until_by,until_basis FROM territorial_coverage WHERE "+(scope==null?"scope_id IS NULL":"scope_id=?")+" ORDER BY id DESC",
            scope==null?new Object[0]:new Object[]{scope});
    }
    /** Regiones del catalogo que hoy no atiende ninguna sede ACTIVA ni el nivel central (aviso REGION_SIN_SEDE). */
    static List<String> uncovered(Snapshot s){
        return AdultIdentity.REGIONS.keySet().stream().filter(r->!s.attended(r)).toList();
    }
    static Optional<Map<String,Object>> warning(Db db){
        var missing=uncovered(load(db));if(missing.isEmpty())return Optional.empty();
        long count=missing.size();
        return Optional.of(Map.of("code",WARNING,"count",count,"regions",missing,
            "message",(count==1?"Una región no tiene":"Hay "+count+" regiones sin")+" sede activa ni nivel central que la"+(count==1?"":"s")+" atienda: "
                +String.join(", ",missing.stream().map(Territory::name).toList())+". Declara su cobertura en Estructura (sede o nivel central)."));
    }
}
