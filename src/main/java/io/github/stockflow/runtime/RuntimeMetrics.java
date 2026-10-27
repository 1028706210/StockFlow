package io.github.stockflow.runtime;

import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.atomic.LongAdder;

@Component
public class RuntimeMetrics {
    private final LongAdder requests=new LongAdder(),duration=new LongAdder();
    private final LongAdder[] status={new LongAdder(),new LongAdder(),new LongAdder(),new LongAdder(),new LongAdder()};
    private final LongAdder batches=new LongAdder(),succeeded=new LongAdder(),failed=new LongAdder(),batchFailures=new LongAdder();
    public void request(int code,long nanos){requests.increment();duration.add(Math.max(0,nanos));status[Math.max(0,Math.min(4,code/100-1))].increment();}
    public void expiryBatch(){batches.increment();}
    public void expirySucceeded(){succeeded.increment();}
    public void expiryFailed(){failed.increment();}
    public void expiryBatchFailed(){batchFailures.increment();}
    public Map<String,Object> snapshot(){
        var codes=new LinkedHashMap<String,Long>();for(int i=0;i<5;i++)codes.put((i+1)+"xx",status[i].sum());
        return Map.of("requests",Map.of("count",requests.sum(),"durationNanos",duration.sum(),"statusClasses",codes),
            "expiry",Map.of("batches",batches.sum(),"succeeded",succeeded.sum(),"failed",failed.sum(),"batchFailures",batchFailures.sum()));
    }
}
