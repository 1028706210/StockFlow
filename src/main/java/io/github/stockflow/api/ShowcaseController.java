package io.github.stockflow.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** Optional local fixture guard. No credentials or database address are exposed. */
@RestController
@ConditionalOnProperty(name="stockflow.showcase.enabled",havingValue="true")
public class ShowcaseController {
    private final JdbcTemplate jdbc;
    public ShowcaseController(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @GetMapping("/api/showcase/identity")
    @PreAuthorize("hasAnyRole('WAREHOUSE','ADMIN')")
    public Map<String,String> identity(){
        if(!"stockflow_showcase".equals(jdbc.queryForObject("SELECT DATABASE()",String.class)))
            throw ApiException.conflict("SHOWCASE_DATABASE_REQUIRED","Showcase setup requires the isolated showcase database");
        return Map.of("environment","showcase","dataset","stockflow-showcase-v1");
    }
}
