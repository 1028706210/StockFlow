package io.github.stockflow.catalog;
import io.github.stockflow.api.ApiException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import static io.github.stockflow.catalog.CatalogModels.*;
@Service
public class CatalogService {
    private final CatalogMapper mapper;
    public CatalogService(CatalogMapper mapper){this.mapper=mapper;}
    public List<Sku> list(){return mapper.list();}
    @Transactional public Sku create(CreateSku request){
        try{mapper.insert(new CreateSku(request.code(),request.name().trim(),request.price()));}
        catch(DuplicateKeyException e){throw ApiException.conflict("SKU_CODE_EXISTS","SKU code already exists");}
        var sku=mapper.byCode(request.code());mapper.initializeInventory(sku.id());return sku;
    }
}
