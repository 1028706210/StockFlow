package io.github.stockflow.operations;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import java.util.function.LongFunction;
import static io.github.stockflow.operations.OperationsModels.*;
import io.github.stockflow.workbench.WorkbenchModels.Page;

@Service
public class AuditService {
    private final OperationsMapper mapper;
    private final JsonMapper json=JsonMapper.builder().build();
    public AuditService(OperationsMapper mapper){this.mapper=mapper;}
    public void record(long actor,String action,String entityType,long id,String label,Object before,Object after,String requestId){
        mapper.audit(actor,action,entityType,id,label,before==null?null:json.writeValueAsString(before),after==null?null:json.writeValueAsString(after),requestId);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page<AuditView> list(String q,String action,int page,int size){return list(q,action,page,size,io.github.stockflow.workbench.QueryRange.of(null,null,null,null));}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page<AuditView> list(String q,String action,int page,int size,io.github.stockflow.workbench.QueryRange range){
        var pattern=pattern(q);long total=mapper.auditCount(pattern,action,range),pages=Math.max(1,(total+size-1)/size);int current=(int)Math.min(page,pages);
        var rows=mapper.audits(pattern,action,(long)(current-1)*size,size,range).stream().map(row->new AuditView(row.id(),row.actorId(),row.username(),row.action(),row.entityType(),row.entityId(),row.entityLabel(),row.beforeJson()==null?json.nullNode():json.readTree(row.beforeJson()),row.afterJson()==null?json.nullNode():json.readTree(row.afterJson()),row.requestId(),row.createdAt())).toList();
        return new Page<>(rows,total,current,size,pages);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public ReconciliationPage reconcile(String q,boolean mismatchOnly,int page,int size){
        var pattern=pattern(q);long checked=mapper.reconciliationCount(pattern,false),mismatches=mapper.reconciliationCount(pattern,true),total=mismatchOnly?mismatches:checked,pages=Math.max(1,(total+size-1)/size);int current=(int)Math.min(page,pages);
        return new ReconciliationPage(mapper.reconciliation(pattern,mismatchOnly,(long)(current-1)*size,size),total,current,size,pages,checked,mismatches);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public List<TrendDay> trends(Long buyer){
        var orders=new HashMap<java.time.LocalDate,Long>();for(var row:mapper.orderTrend(buyer))orders.put(row.day(),row.value());
        var inbound=new HashMap<java.time.LocalDate,Long>();var outbound=new HashMap<java.time.LocalDate,Long>();
        if(buyer==null){for(var row:mapper.stockTrend("INBOUND"))inbound.put(row.day(),row.value());for(var row:mapper.stockTrend("SHIP"))outbound.put(row.day(),row.value());}
        var today=mapper.today();var days=new ArrayList<TrendDay>();for(int offset=6;offset>=0;offset--){var day=today.minusDays(offset);days.add(new TrendDay(day,inbound.getOrDefault(day,0L),outbound.getOrDefault(day,0L),orders.getOrDefault(day,0L)));}return List.copyOf(days);
    }
    static String pattern(String q){return "%"+q.trim().replace("!","!!").replace("%","!%").replace("_","!_")+"%";}
}
