package io.github.stockflow.operations;

import io.github.stockflow.auth.Actor;
import io.github.stockflow.workbench.WorkbenchModels.Page;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static io.github.stockflow.operations.OperationsModels.*;

@RestController @Validated @RequestMapping("/api")
public class OperationsController {
    private final AuditService audit;private final StocktakeService stocktakes;
    public OperationsController(AuditService audit,StocktakeService stocktakes){this.audit=audit;this.stocktakes=stocktakes;}
    @GetMapping("/audit") @PreAuthorize("hasRole('WAREHOUSE')")
    public Page<AuditView> audit(@RequestParam(defaultValue="") @Size(max=120)String q,@RequestParam(defaultValue="") @Pattern(regexp="|SKU_CREATE|SKU_UPDATE|STOCK_POLICY|STOCKTAKE_CREATE|STOCKTAKE_CONFIRM|STOCKTAKE_CANCEL|RECEIPT_CREATE|RECEIPT_CONFIRM|RECEIPT_CANCEL|RETURN_CREATE|RETURN_CONFIRM|RETURN_CANCEL")String action,@RequestParam(defaultValue="1") @Min(1) @Max(1000000)int page,@RequestParam(defaultValue="8") @Min(1) @Max(100)int size,@RequestParam(required=false) @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE)java.time.LocalDate from,@RequestParam(required=false) @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE)java.time.LocalDate to){return audit.list(q,action,page,size,io.github.stockflow.workbench.QueryRange.of(from,to,null,null));}
    @GetMapping("/inventory/reconciliation") @PreAuthorize("hasRole('WAREHOUSE')")
    public ReconciliationPage reconciliation(@RequestParam(defaultValue="") @Size(max=120)String q,@RequestParam(defaultValue="true")boolean mismatchOnly,@RequestParam(defaultValue="1") @Min(1) @Max(1000000)int page,@RequestParam(defaultValue="8") @Min(1) @Max(100)int size){return audit.reconcile(q,mismatchOnly,page,size);}
    @GetMapping("/dashboard/trends")
    public List<TrendDay> trends(Authentication auth){boolean warehouse=auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_WAREHOUSE")||a.getAuthority().equals("ROLE_ADMIN"));return audit.trends(warehouse?null:Actor.id(auth));}
    @GetMapping("/stocktakes") @PreAuthorize("hasRole('WAREHOUSE')")
    public Page<Stocktake> stocktakes(@RequestParam(defaultValue="") @Size(max=120)String q,@RequestParam(defaultValue="all") @Pattern(regexp="all|DRAFT|CONFIRMED|CANCELLED")String status,@RequestParam(defaultValue="1") @Min(1) @Max(1000000)int page,@RequestParam(defaultValue="8") @Min(1) @Max(100)int size,@RequestParam(required=false) @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE)java.time.LocalDate from,@RequestParam(required=false) @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE)java.time.LocalDate to){return stocktakes.list(q,status,page,size,io.github.stockflow.workbench.QueryRange.of(from,to,null,null));}
    @GetMapping("/stocktakes/{id}") @PreAuthorize("hasRole('WAREHOUSE')")
    public Stocktake stocktake(@PathVariable @Positive long id){return stocktakes.get(id);}
    @PostMapping(value="/stocktakes",consumes=MediaType.APPLICATION_JSON_VALUE) @PreAuthorize("hasRole('WAREHOUSE')")
    public ResponseEntity<Stocktake> create(Authentication auth,@Valid @RequestBody CreateStocktake request,HttpServletRequest http){var created=stocktakes.create(Actor.id(auth),request,(String)http.getAttribute("requestId"));return ResponseEntity.status(created.replayed()?200:201).body(created.stocktake());}
    @PostMapping("/stocktakes/{id}/confirm") @PreAuthorize("hasRole('WAREHOUSE')")
    public Stocktake confirm(Authentication auth,@PathVariable @Positive long id,HttpServletRequest http){return stocktakes.confirm(Actor.id(auth),id,(String)http.getAttribute("requestId"));}
    @PostMapping("/stocktakes/{id}/cancel") @PreAuthorize("hasRole('WAREHOUSE')")
    public Stocktake cancel(Authentication auth,@PathVariable @Positive long id,HttpServletRequest http){return stocktakes.cancel(Actor.id(auth),id,(String)http.getAttribute("requestId"));}
}
