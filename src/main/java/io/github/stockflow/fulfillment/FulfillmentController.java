package io.github.stockflow.fulfillment;
import io.github.stockflow.auth.Actor;
import io.github.stockflow.order.OrderService;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static io.github.stockflow.order.OrderModels.*;

@RestController
@RequestMapping("/api/fulfillment/orders")
@PreAuthorize("hasAnyRole('WAREHOUSE','ADMIN')")
@Validated
public class FulfillmentController {
    private final OrderService service;
    public FulfillmentController(OrderService service){this.service=service;}
    @GetMapping public List<Summary> list(){return service.fulfillmentList();}
    @GetMapping("/{id}") public View get(@PathVariable @Positive long id){return service.fulfillmentGet(id);}
    @PostMapping("/{id}/ship") public View ship(Authentication auth,@PathVariable @Positive long id){return service.ship(Actor.id(auth),id);}
}
