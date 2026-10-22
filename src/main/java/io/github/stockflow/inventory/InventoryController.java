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
}
