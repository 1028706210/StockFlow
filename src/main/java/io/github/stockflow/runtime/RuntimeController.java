package io.github.stockflow.runtime;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;

@RestController
public class RuntimeController {
    private final DatabaseReadiness database;
    private final RuntimeMetrics metrics;
    private final boolean expiryEnabled;
    private final long expiryDelayMillis;
    public RuntimeController(DatabaseReadiness database,RuntimeMetrics metrics,@Value("${stockflow.expiration.enabled:true}")boolean expiryEnabled,@Value("${stockflow.expiration.delay-ms:30000}")long expiryDelayMillis){this.database=database;this.metrics=metrics;this.expiryEnabled=expiryEnabled;this.expiryDelayMillis=expiryDelayMillis;}
    @GetMapping("/api/health/readiness")
    public ResponseEntity<Map<String,String>> readiness(){
        boolean ready=database.ready();return ResponseEntity.status(ready?200:503).body(Map.of("status",ready?"UP":"DOWN"));
    }
    @GetMapping("/api/runtime/metrics")
    @PreAuthorize("hasAnyRole('WAREHOUSE','ADMIN')")
    public Map<String,Object> metrics(){return metrics.snapshot();}
    @GetMapping("/api/admin/runtime")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String,Object> status(){return Map.of("observedAt",java.time.Instant.now().toString(),"databaseStatus",database.ready()?"UP":"DOWN","expiryEnabled",expiryEnabled,"expiryDelayMillis",expiryDelayMillis,"metrics",metrics.snapshot());}
}
