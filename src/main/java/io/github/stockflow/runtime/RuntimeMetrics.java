package io.github.stockflow.runtime;

import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.atomic.LongAdder;
import java.time.Clock;
import java.time.Instant;

@Component
public class RuntimeMetrics {
    private final LongAdder requests=new LongAdder(),duration=new LongAdder();
    private final LongAdder[] status={new LongAdder(),new LongAdder(),new LongAdder(),new LongAdder(),new LongAdder()};
    private final LongAdder batches=new LongAdder(),succeeded=new LongAdder(),failed=new LongAdder(),batchFailures=new LongAdder();
    private final Clock clock;
    private final Instant startedAt;
    private final long startedNanos=System.nanoTime();
    private Instant batchStarted,batchCompleted,lastSuccessful,lastFailure;
    private boolean running,queryFailed;
    private long processedInBatch,failedInBatch;
    public RuntimeMetrics(){this(Clock.systemUTC());}
    RuntimeMetrics(Clock clock){this.clock=clock;this.startedAt=clock.instant();}
    public void request(int code,long nanos){requests.increment();duration.add(Math.max(0,nanos));status[Math.max(0,Math.min(4,code/100-1))].increment();}
    public synchronized void expiryBatch(){batches.increment();running=true;batchStarted=clock.instant();batchCompleted=null;processedInBatch=0;failedInBatch=0;queryFailed=false;}
    public synchronized void expirySucceeded(){succeeded.increment();processedInBatch++;}
    public synchronized void expiryFailed(){failed.increment();failedInBatch++;lastFailure=clock.instant();}
    public synchronized void expiryBatchFailed(){batchFailures.increment();queryFailed=true;lastFailure=clock.instant();}
    public synchronized void expiryCompleted(){running=false;batchCompleted=clock.instant();if(!queryFailed&&failedInBatch==0)lastSuccessful=batchCompleted;}
    private static String timestamp(Instant value){return value==null?null:value.toString();}
    private synchronized Map<String,Object> expirySnapshot(){
        var batch=new LinkedHashMap<String,Object>();
        batch.put("status",running?"RUNNING":batchCompleted==null?"NEVER":queryFailed?"FAILED":failedInBatch>0?"PARTIAL_FAILED":"SUCCEEDED");
        batch.put("startedAt",timestamp(batchStarted));batch.put("completedAt",timestamp(batchCompleted));
        batch.put("processed",processedInBatch);batch.put("failed",failedInBatch);batch.put("queryFailed",queryFailed);
        var result=new LinkedHashMap<String,Object>();
        result.put("batches",batches.sum());result.put("succeeded",succeeded.sum());result.put("failed",failed.sum());result.put("batchFailures",batchFailures.sum());
        result.put("lastBatch",batch);result.put("lastSuccessfulAt",timestamp(lastSuccessful));result.put("lastFailureAt",timestamp(lastFailure));return result;
    }
    public Map<String,Object> snapshot(){
        var codes=new LinkedHashMap<String,Long>();for(int i=0;i<5;i++)codes.put((i+1)+"xx",status[i].sum());
        return Map.of("process",Map.of("startedAt",startedAt.toString(),"uptimeSeconds",Math.max(0,(System.nanoTime()-startedNanos)/1_000_000_000L)),
            "requests",Map.of("count",requests.sum(),"durationNanos",duration.sum(),"statusClasses",codes),"expiry",expirySnapshot());
    }
}
