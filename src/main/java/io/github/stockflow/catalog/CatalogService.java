package io.github.stockflow.catalog;
import io.github.stockflow.api.ApiException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import static io.github.stockflow.catalog.CatalogModels.*;
@Service
public class CatalogService {
    private final CatalogMapper mapper;private final io.github.stockflow.operations.AuditService audit;
    public CatalogService(CatalogMapper mapper,io.github.stockflow.operations.AuditService audit){this.mapper=mapper;this.audit=audit;}
    public List<Sku> list(){return mapper.list();}
    public Sku get(long id){var sku=mapper.byId(id);if(sku==null)throw ApiException.notFound();return sku;}
    @Transactional public Sku update(long actor,long id,UpdateSku request,String requestId){
        var before=mapper.lock(id);if(before==null)throw ApiException.notFound();
        if(mapper.update(id,new UpdateSku(request.name().trim(),request.price(),request.active()))!=1)throw ApiException.notFound();
        var after=get(id);if(!before.equals(after))audit.record(actor,"SKU_UPDATE","SKU",id,after.code()+" · "+after.name(),before,after,requestId);return after;
    }
    @Transactional public Sku create(long actor,CreateSku request,String requestId){
        try{mapper.insert(new CreateSku(request.code(),request.name().trim(),request.price()));}
        catch(DuplicateKeyException e){throw ApiException.conflict("SKU_CODE_EXISTS","SKU code already exists");}
        var sku=mapper.byCode(request.code());mapper.initializeInventory(sku.id());audit.record(actor,"SKU_CREATE","SKU",sku.id(),sku.code()+" · "+sku.name(),null,sku,requestId);return sku;
    }
}
