package io.github.stockflow.auth;

import io.github.stockflow.api.ApiErrors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableMethodSecurity
@Profile({"local","test"})
public class SecurityConfiguration {
    @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
    @Bean ApplicationRunner seedDemoAccounts(AccountMapper accounts,PasswordEncoder passwords,@Value("${stockflow.demo-password}")String password){
        return arguments->{
            if(password.length()<12||password.getBytes(StandardCharsets.UTF_8).length>72)
                throw new IllegalStateException("A local demo password of 12 characters and at most 72 UTF-8 bytes is required");
            // Insert only missing demos; a restart must never reset an existing password.
            var hash=passwords.encode(password);
            accounts.seed(1,"buyer",hash,"BUYER");accounts.seed(2,"buyer2",hash,"BUYER");
            accounts.seed(3,"warehouse",hash,"WAREHOUSE");accounts.seed(4,"admin",hash,"ADMIN");
        };
    }
    @Bean SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        var json=JsonMapper.builder().build();
        return http.csrf(c->c.ignoringRequestMatchers(request->{
                var authorization=request.getHeader("Authorization");
                return authorization!=null&&authorization.regionMatches(true,0,"Basic ",0,6);
            }))
            .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .requestCache(c->c.disable())
            .authorizeHttpRequests(a->a.requestMatchers("/shop","/shop/","/","/index.html","/app.js","/styles.css","/ui-model.mjs","/api-client.mjs","/query-state.mjs","/business-documents.mjs","/document-state.mjs","/assets/ui/**","/api/health","/api/health/readiness","/api/auth/csrf","/api/auth/login","/api/auth/register","/error").permitAll().anyRequest().authenticated())
            .httpBasic(b->b.authenticationEntryPoint((r,s,e)->ApiErrors.writeSecurity(r,s,401)))
            .formLogin(f->f.loginProcessingUrl("/api/auth/login")
                .successHandler((request,response,auth)->{
                    response.setStatus(200);response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(json.writeValueAsString(AuthController.identity(auth)));
                })
                .failureHandler((r,s,e)->ApiErrors.writeSecurity(r,s,401)))
            .logout(l->l.logoutUrl("/api/auth/logout").invalidateHttpSession(true).clearAuthentication(true)
                .deleteCookies("JSESSIONID").logoutSuccessHandler((r,s,a)->s.setStatus(204)))
            .exceptionHandling(e->e.authenticationEntryPoint((r,s,x)->ApiErrors.writeSecurity(r,s,401))
                .accessDeniedHandler((r,s,x)->ApiErrors.writeSecurity(r,s,403)))
            .build();
    }
}
