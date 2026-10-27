package io.github.stockflow.runtime;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
public class RuntimeController {
    private final DatabaseReadiness database;
    private final RuntimeMetrics metrics;
    public RuntimeController(DatabaseReadiness database,RuntimeMetrics metrics){this.database=database;this.metrics=metrics;}
    @GetMapping("/api/health/readiness")
    public ResponseEntity<Map<String,String>> readiness(){
        boolean ready=database.ready();return ResponseEntity.status(ready?200:503).body(Map.of("status",ready?"UP":"DOWN"));
    }
    @GetMapping("/api/runtime/metrics")
    @PreAuthorize("hasAnyRole('WAREHOUSE','ADMIN')")
    public Map<String,Object> metrics(){return metrics.snapshot();}
}
