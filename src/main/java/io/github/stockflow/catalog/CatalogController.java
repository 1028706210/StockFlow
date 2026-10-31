package io.github.stockflow.catalog;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import io.github.stockflow.auth.Actor;
import jakarta.validation.constraints.Positive;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import java.util.List;
import static io.github.stockflow.catalog.CatalogModels.*;
@RestController
@RequestMapping("/api/skus")
@Validated
public class CatalogController {
    private final CatalogService service;
    public CatalogController(CatalogService service){this.service=service;}
    @GetMapping public List<Sku> list(){return service.list();}
    @GetMapping("/{id}") public Sku get(@PathVariable @Positive long id){return service.get(id);}
    @PutMapping(value="/{id}",consumes=MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('WAREHOUSE')")
    public Sku update(Authentication auth,@PathVariable @Positive long id,@Valid @RequestBody UpdateSku request,HttpServletRequest http){return service.update(Actor.id(auth),id,request,(String)http.getAttribute("requestId"));}
    @PostMapping(consumes=MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('WAREHOUSE')")
    public ResponseEntity<Sku> create(Authentication auth,@Valid @RequestBody CreateSku request,HttpServletRequest http){return ResponseEntity.status(201).body(service.create(Actor.id(auth),request,(String)http.getAttribute("requestId")));}
}
