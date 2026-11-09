package cl.nexbyte.sigera;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.web.filter.OncePerRequestFilter;
import static cl.nexbyte.sigera.Db.*;

@Configuration
public class SecurityConfig {
    public record Principal(long id,String username,String password,long version,boolean enabled) implements UserDetails {
        @Override public String getUsername(){return username;}
        @Override public String getPassword(){return password;}
        @Override public Collection<? extends GrantedAuthority> getAuthorities(){return List.of(new SimpleGrantedAuthority("ROLE_STAFF"));}
        @Override public boolean isEnabled(){return enabled;}
        @Override public boolean isAccountNonExpired(){return true;}
        @Override public boolean isAccountNonLocked(){return true;}
        @Override public boolean isCredentialsNonExpired(){return true;}
    }
    @Bean PasswordEncoder encoder(){return new BCryptPasswordEncoder(12);}

    /**
     * El ingreso solo queda abierto si se registra su auditoria. Spring guarda la sesion autenticada
     * antes de llamar a este manejador: si la auditoria falla se descarta esa sesion, para no dejar un
     * ingreso sin traza ni una respuesta de error con la sesion ya iniciada.
     */
    static void loginSucceeded(Db db,HttpServletRequest q,HttpServletResponse r,Authentication a)throws IOException{
        long id=((Principal)a.getPrincipal()).id();
        try{db.audit(id,"LOGIN","USUARIO",id,null,"");}
        catch(RuntimeException e){
            String incident=UUID.randomUUID().toString();
            org.slf4j.LoggerFactory.getLogger(SecurityConfig.class).error("Incidente {}: no se pudo auditar el ingreso ({})",incident,Db.readOnly(e)?"base de solo lectura":e.getClass().getSimpleName());
            SecurityContextHolder.clearContext();
            var session=q.getSession(false);if(session!=null)session.invalidate();
            r.setStatus(503);r.setContentType("application/json;charset=UTF-8");
            String message=Db.readOnly(e)?Db.READ_ONLY_MESSAGE:"No se pudo registrar el ingreso. Intentalo nuevamente en unos minutos.";
            r.getWriter().write("{\"message\":\""+message+"\",\"incident\":\""+incident+"\"}");
            return;
        }
        r.setStatus(204);
    }
    @Bean UserDetailsService users(Db db){return username->{
        var rows=db.rows("SELECT * FROM staff WHERE username=?",username);
        if(rows.isEmpty())throw new UsernameNotFoundException("Credenciales invalidas");
        var u=rows.getFirst();return new Principal(n(u,"id"),s(u,"username"),s(u,"password_hash"),n(u,"security_version"),b(u,"enabled"));
    };}
    @Bean @org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication(type=org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET)
    SecurityFilterChain security(HttpSecurity http,Db db) throws Exception {
        var attempts=new ConcurrentHashMap<String,long[]>();
        http.requestCache(c->c.disable())
            .authorizeHttpRequests(a->a.requestMatchers("/","/index.html","/assets/**","/favicon.ico","/api/session","/api/login","/api/health","/error").permitAll().anyRequest().authenticated())
            .csrf(c->c.csrfTokenRepository(new HttpSessionCsrfTokenRepository()))
            .sessionManagement(s->s.sessionFixation(f->f.changeSessionId()))
            .formLogin(f->f.loginProcessingUrl("/api/login").successHandler((q,r,a)->loginSucceeded(db,q,r,a)).failureHandler((q,r,e)->r.setStatus(401)))
            .logout(l->l.logoutUrl("/api/logout").invalidateHttpSession(true).clearAuthentication(true).deleteCookies("SIGERA_SESSION").logoutSuccessHandler((q,r,a)->r.setStatus(204)))
            .exceptionHandling(e->e.authenticationEntryPoint((q,r,x)->r.setStatus(401)).accessDeniedHandler((q,r,x)->r.setStatus(403)))
            .headers(h->h.contentSecurityPolicy(p->p.policyDirectives("default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; object-src 'none'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'")));
        http.addFilterBefore(new OncePerRequestFilter(){
            @Override protected void doFilterInternal(HttpServletRequest q,HttpServletResponse r,FilterChain chain)throws ServletException,IOException{
                if(q.getRequestURI().equals("/api/login")&&q.getMethod().equals("POST")){
                    long now=System.currentTimeMillis();
                    if(attempts.size()>2000)attempts.entrySet().removeIf(e->now-e.getValue()[0]>900000);
                    String key=q.getRemoteAddr()+":"+Objects.toString(q.getParameter("username"),"").substring(0,Math.min(64,Objects.toString(q.getParameter("username"),"").length()));
                    var bucket=attempts.compute(key,(k,v)->v==null||now-v[0]>900000?new long[]{now,1}:new long[]{v[0],v[1]+1});
                    if(bucket[1]>30){r.setStatus(429);r.setHeader("Retry-After","900");return;}
                }
                chain.doFilter(q,r);
            }
        },UsernamePasswordAuthenticationFilter.class);
        http.addFilterAfter(new OncePerRequestFilter(){
            @Override protected void doFilterInternal(HttpServletRequest q,HttpServletResponse r,FilterChain chain)throws ServletException,IOException{
                Authentication auth=SecurityContextHolder.getContext().getAuthentication();
                if(auth!=null&&auth.getPrincipal() instanceof Principal p){
                    var rows=db.rows("SELECT enabled,security_version,must_change FROM staff WHERE id=?",p.id());
                    if(rows.isEmpty()||!b(rows.getFirst(),"enabled")||n(rows.getFirst(),"security_version")!=p.version()){
                        SecurityContextHolder.clearContext();if(q.getSession(false)!=null)q.getSession(false).invalidate();r.setStatus(401);return;
                    }
                    if(b(rows.getFirst(),"must_change")&&q.getRequestURI().startsWith("/api/")&&!Set.of("/api/session","/api/password","/api/logout").contains(q.getRequestURI())){r.setStatus(428);return;}
                }
                chain.doFilter(q,r);
            }
        },AnonymousAuthenticationFilter.class);
        return http.build();
    }
}
