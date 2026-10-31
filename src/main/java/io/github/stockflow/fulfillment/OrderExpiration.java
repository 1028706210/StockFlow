package io.github.stockflow.fulfillment;
import io.github.stockflow.order.OrderMapper;
import io.github.stockflow.order.OrderService;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class OrderExpiration {
    private final OrderMapper orders;
    private final OrderService service;
    private final boolean enabled;
    private long afterId;
    @org.springframework.beans.factory.annotation.Autowired private io.github.stockflow.runtime.RuntimeMetrics metrics;
    public OrderExpiration(OrderMapper orders,OrderService service,@Value("${stockflow.expiration.enabled:true}")boolean enabled){
        this.orders=orders;this.service=service;this.enabled=enabled;
    }
    @Scheduled(fixedDelayString="${stockflow.expiration.delay-ms:30000}",initialDelayString="${stockflow.expiration.delay-ms:30000}")
    public void scheduled(){if(enabled)expireBatch();}

    public synchronized void expireBatch(){
        if(metrics!=null)metrics.expiryBatch();
        java.util.List<Long> ids;
        try{
            ids=orders.expiredIds(afterId);
            if(ids.isEmpty()&&afterId!=0){afterId=0;ids=orders.expiredIds(afterId);}
        }catch(RuntimeException error){if(metrics!=null)metrics.expiryBatchFailed();throw error;}
        for(long id:ids){
            // Advance even on failure so damaged reservations cannot block later orders.
            afterId=id;
            try{service.expire(id);if(metrics!=null)metrics.expirySucceeded();}
            catch(RuntimeException error){
                if(metrics!=null)metrics.expiryFailed();
                LoggerFactory.getLogger(OrderExpiration.class).warn("Could not expire order {}; will retry in a later batch",id,error);
            }
        }
    }
}
