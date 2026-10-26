package io.github.stockflow.inventory;
import io.github.stockflow.auth.Actor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static io.github.stockflow.inventory.InventoryModels.*;
@RestController
@RequestMapping("/api/inventory")
@PreAuthorize("hasRole('WAREHOUSE')")
@Validated
public class InventoryController {
    private final InventoryService service;
    public InventoryController(InventoryService service){this.service=service;}
    @GetMapping public List<Stock> list(){return service.list();}
    @PostMapping(value="/{skuId}/receipts",consumes=MediaType.APPLICATION_JSON_VALUE)
    public Stock receive(Authentication auth,@PathVariable @Positive long skuId,@Valid @RequestBody Receipt request){return service.receive(Actor.id(auth),skuId,request);}
    @PutMapping(value="/{skuId}/policy",consumes=MediaType.APPLICATION_JSON_VALUE)
    public Stock policy(Authentication auth,@PathVariable @Positive long skuId,@Valid @RequestBody StockPolicy policy,jakarta.servlet.http.HttpServletRequest http){return service.policy(Actor.id(auth),skuId,policy,(String)http.getAttribute("requestId"));}
    @GetMapping("/ledger")
    public LedgerPage ledger(@RequestParam(defaultValue="") @jakarta.validation.constraints.Size(max=120) String q,
        @RequestParam(defaultValue="") @jakarta.validation.constraints.Pattern(regexp="|INBOUND|RESERVE|RELEASE|SHIP|ADJUST_IN|ADJUST_OUT") String type,
        @RequestParam(required=false) @Positive Long skuId,
        @RequestParam(required=false) @Positive Long beforeId,
        @RequestParam(defaultValue="20") @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(100) int limit){
        return service.ledger(q,type,skuId,beforeId,limit);
    }
    @GetMapping("/alerts")
    public AlertPage alerts(@RequestParam(defaultValue="") @jakarta.validation.constraints.Size(max=120) String q,
        @RequestParam(required=false) @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(1000000) Integer threshold,
        @RequestParam(required=false) @Positive Long beforeId,
        @RequestParam(defaultValue="20") @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(100) int limit){
        return service.alerts(q,threshold,beforeId,limit);
    }
}
