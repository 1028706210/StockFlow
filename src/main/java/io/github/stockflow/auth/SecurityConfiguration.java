package io.github.stockflow.auth;
import io.github.stockflow.api.ApiErrors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
@Profile({"local","test"})
public class SecurityConfiguration {
    @Bean UserDetailsService demoUsers(@Value("${stockflow.demo-password}")String password){
        if(password.length()<12)throw new IllegalStateException("A generated local demo password of at least 12 characters is required");
        var hash="{bcrypt}"+new BCryptPasswordEncoder().encode(password);
        return new InMemoryUserDetailsManager(
            User.withUsername("buyer").password(hash).roles("BUYER").build(),
            User.withUsername("buyer2").password(hash).roles("BUYER").build(),
            User.withUsername("warehouse").password(hash).roles("WAREHOUSE").build(),
            User.withUsername("admin").password(hash).roles("ADMIN","WAREHOUSE").build());
    }
    @Bean SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        return http.csrf(c->c.disable())
            .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a->a.requestMatchers("/","/index.html","/app.js","/styles.css","/ui-model.mjs","/api-client.mjs","/api/health","/error").permitAll().anyRequest().authenticated())
            .httpBasic(b->b.authenticationEntryPoint((r,s,e)->ApiErrors.writeSecurity(r,s,401)))
            .exceptionHandling(e->e.accessDeniedHandler((r,s,x)->ApiErrors.writeSecurity(r,s,403)))
            .build();
    }
}
