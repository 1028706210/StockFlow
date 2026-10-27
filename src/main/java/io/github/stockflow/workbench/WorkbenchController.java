package io.github.stockflow.workbench;

import io.github.stockflow.auth.Actor;
import io.github.stockflow.catalog.CatalogModels.Sku;
import io.github.stockflow.order.OrderModels.Summary;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import static io.github.stockflow.workbench.WorkbenchModels.*;

@RestController
@Validated
public class WorkbenchController {
    private final WorkbenchService service;
    public WorkbenchController(WorkbenchService service){this.service=service;}
    @GetMapping("/api/skus/page")
    public Page<Sku> skus(@RequestParam(defaultValue="") @Size(max=120)String q,
        @RequestParam(defaultValue="all") @Pattern(regexp="all|active|inactive")String status,
        @RequestParam(defaultValue="1") @Min(1) @Max(1000000)int page,
        @RequestParam(defaultValue="8") @Min(1) @Max(100)int size){return service.skus(q,status,page,size);}
    @GetMapping("/api/inventory/page")
    @PreAuthorize("hasRole('WAREHOUSE')")
    public Page<StockRow> stocks(@RequestParam(defaultValue="") @Size(max=120)String q,
        @RequestParam(defaultValue="all") @Pattern(regexp="all|healthy|low|empty")String status,
        @RequestParam(defaultValue="1") @Min(1) @Max(1000000)int page,
        @RequestParam(defaultValue="8") @Min(1) @Max(100)int size){return service.stocks(q,status,page,size);}
    @GetMapping("/api/inventory/{skuId}")
    @PreAuthorize("hasRole('WAREHOUSE')")
    public StockRow stock(@PathVariable @Positive long skuId){return service.stock(skuId);}
    @GetMapping("/api/orders/page")
    @PreAuthorize("hasRole('BUYER')")
    public Page<Summary> orders(Authentication auth,@RequestParam(defaultValue="") @Size(max=120)String q,
        @RequestParam(defaultValue="all") @Pattern(regexp="all|PENDING_CONFIRM|CONFIRMED|SHIPPED|COMPLETED|CANCELLED")String status,
        @RequestParam(defaultValue="1") @Min(1) @Max(1000000)int page,
        @RequestParam(defaultValue="8") @Min(1) @Max(100)int size,@RequestParam(required=false) @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE)java.time.LocalDate from,@RequestParam(required=false) @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE)java.time.LocalDate to,@RequestParam(required=false)java.math.BigDecimal minTotal,@RequestParam(required=false)java.math.BigDecimal maxTotal){return service.orders(Actor.id(auth),q,status,page,size,QueryRange.of(from,to,minTotal,maxTotal));}
    @GetMapping("/api/fulfillment/orders/page")
    @PreAuthorize("hasAnyRole('WAREHOUSE','ADMIN')")
    public Page<Summary> fulfillment(@RequestParam(defaultValue="") @Size(max=120)String q,
        @RequestParam(defaultValue="all") @Pattern(regexp="all|PENDING_CONFIRM|CONFIRMED|SHIPPED|COMPLETED|CANCELLED")String status,
        @RequestParam(defaultValue="1") @Min(1) @Max(1000000)int page,
        @RequestParam(defaultValue="8") @Min(1) @Max(100)int size,@RequestParam(required=false) @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE)java.time.LocalDate from,@RequestParam(required=false) @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE)java.time.LocalDate to,@RequestParam(required=false)java.math.BigDecimal minTotal,@RequestParam(required=false)java.math.BigDecimal maxTotal){return service.orders(null,q,status,page,size,QueryRange.of(from,to,minTotal,maxTotal));}
    @GetMapping("/api/dashboard")
    public Dashboard dashboard(Authentication auth){
        boolean warehouse=auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_WAREHOUSE"));
        return service.dashboard(Actor.id(auth),warehouse);
    }
}
