package io.github.stockflow.workbench;

import io.github.stockflow.api.ApiException;
import io.github.stockflow.catalog.CatalogModels.Sku;
import io.github.stockflow.order.OrderModels.Summary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.List;
import java.util.function.LongFunction;
import static io.github.stockflow.workbench.WorkbenchModels.*;

@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class WorkbenchService {
    private final WorkbenchMapper mapper;
    public WorkbenchService(WorkbenchMapper mapper){this.mapper=mapper;}
    public Page<Sku> skus(String q,String status,int page,int size){
        var pattern=searchPattern(q);
        return page(mapper.skuCount(pattern,status),page,size,offset->mapper.skus(pattern,status,offset,size));
    }
    public Page<StockRow> stocks(String q,String status,int page,int size){
        var pattern=searchPattern(q);
        return page(mapper.stockCount(pattern,status),page,size,offset->mapper.stocks(pattern,status,offset,size));
    }
    public Page<Summary> orders(Long buyer,String q,String status,int page,int size){return orders(buyer,q,status,page,size,QueryRange.of(null,null,null,null));}
    public Page<Summary> orders(Long buyer,String q,String status,int page,int size,QueryRange range){
        var pattern=searchPattern(q);
        return page(mapper.orderCount(buyer,pattern,status,range),page,size,offset->mapper.orders(buyer,pattern,status,offset,size,range));
    }
    public List<Summary> exportOrders(Long buyer,String q,String status,QueryRange range){
        var pattern=searchPattern(q);if(mapper.orderCount(buyer,pattern,status,range)>10000)throw new ApiException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,"EXPORT_LIMIT","Narrow filters to at most10000 records");
        return mapper.orders(buyer,pattern,status,0,10000,range);
    }
    public List<StockRow> exportStocks(String q,String status){var pattern=searchPattern(q);if(mapper.stockCount(pattern,status)>10000)throw new ApiException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,"EXPORT_LIMIT","Narrow filters to at most10000 records");return mapper.stocks(pattern,status,0,10000);}
    public StockRow stock(long skuId){var row=mapper.stock(skuId);if(row==null)throw ApiException.notFound();return row;}
    public Dashboard dashboard(long buyer,boolean warehouse){return warehouse?mapper.warehouseDashboard():mapper.buyerDashboard(buyer);}
    private static String searchPattern(String q){return "%"+q.trim().replace("!","!!").replace("%","!%").replace("_","!_")+"%";}
    private static <T> Page<T> page(long total,int requested,int size,LongFunction<List<T>> query){
        long totalPages=Math.max(1,(total+size-1)/size);
        int current=(int)Math.min(requested,totalPages);
        return new Page<>(query.apply((long)(current-1)*size),total,current,size,totalPages);
    }
}
