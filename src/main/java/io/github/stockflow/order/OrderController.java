package io.github.stockflow.order;
import io.github.stockflow.auth.Actor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static io.github.stockflow.order.OrderModels.*;
@RestController
@RequestMapping("/api/orders")
@PreAuthorize("hasRole('BUYER')")
@Validated
public class OrderController {
    private final OrderService service;
    public OrderController(OrderService service){this.service=service;}
    @PostMapping(consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<View> create(Authentication auth,@Valid @RequestBody CreateOrder request){
        var result=service.create(Actor.id(auth),request);
        return ResponseEntity.status(result.replayed()?200:201).body(result.order());
    }
    @GetMapping public List<Summary> list(Authentication auth){return service.list(Actor.id(auth));}
    @GetMapping("/{id}") public View get(Authentication auth,@PathVariable @Positive long id){return service.get(Actor.id(auth),id);}
}
