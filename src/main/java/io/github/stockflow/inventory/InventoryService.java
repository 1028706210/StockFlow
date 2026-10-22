package io.github.stockflow.inventory;
import io.github.stockflow.api.ApiException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import static io.github.stockflow.inventory.InventoryModels.*;
@Service
public class InventoryService {
    private final InventoryMapper mapper;private final TransactionTemplate transaction;
    public InventoryService(InventoryMapper mapper,TransactionTemplate transaction){this.mapper=mapper;this.transaction=transaction;}
    public List<Stock> list(){return mapper.list();}
    public Stock receive(long actor,long sku,Receipt request){
        var key=actor+":"+request.operationKey();
        if(mapper.get(sku)==null)throw ApiException.notFound();
        try{
            return transaction.execute(status->{
                mapper.ledger(sku,null,actor,"INBOUND",key,request.quantity());
                if(mapper.receive(sku,request.quantity())!=1)throw ApiException.conflict("INVENTORY_LIMIT","Inventory limit reached");
                return mapper.get(sku);
            });
        }catch(DuplicateKeyException e){
            var original=mapper.receiptQuantity(sku,key);
            if(original!=null && original==request.quantity())return mapper.get(sku);
            throw ApiException.conflict("IDEMPOTENCY_CONFLICT","Receipt key was used for different content");
        }
    }
}
