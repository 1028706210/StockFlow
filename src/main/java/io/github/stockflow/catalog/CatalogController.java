package io.github.stockflow.catalog;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static io.github.stockflow.catalog.CatalogModels.*;
@RestController
@RequestMapping("/api/skus")
public class CatalogController {
    private final CatalogService service;
    public CatalogController(CatalogService service){this.service=service;}
    @GetMapping public List<Sku> list(){return service.list();}
    @PostMapping(consumes=MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('WAREHOUSE')")
    public ResponseEntity<Sku> create(@Valid @RequestBody CreateSku request){return ResponseEntity.status(201).body(service.create(request));}
}
