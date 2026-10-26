package io.github.stockflow.operations;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;
import tools.jackson.databind.JsonNode;

public final class OperationsModels {
    private OperationsModels(){}
    public record AuditRow(long id,long actorId,String username,String action,String entityType,long entityId,String entityLabel,String beforeJson,String afterJson,String requestId,LocalDateTime createdAt){}
    public record AuditView(long id,long actorId,String username,String action,String entityType,long entityId,String entityLabel,JsonNode before,JsonNode after,String requestId,LocalDateTime createdAt){}
    public record InventorySnapshot(long skuId,long onHand,long reserved,long version){}
    public record Reconciliation(long skuId,String code,String name,long onHand,long reserved,long orderReserved,long ledgerOnHand,long ledgerReserved,boolean consistent){}
    public record ReconciliationPage(List<Reconciliation> items,long total,int page,int size,long totalPages,long checkedCount,long mismatchCount){}
    public record CreateStocktake(
        @NotBlank @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9._:-]{0,63}") String requestKey,
        @Positive long skuId,
        @NotNull @Min(0) @Max(1000000000) Long targetOnHand,
        @NotBlank @Size(max=240) String reason){}
    public record Stocktake(long id,long skuId,String skuCode,String skuName,long actorId,String requestKey,long targetOnHand,long originalOnHand,long originalReserved,long inventoryVersion,String reason,String status,Long confirmedBy,Long adjustment,LocalDateTime createdAt,LocalDateTime confirmedAt){}
    public record Creation(Stocktake stocktake,boolean replayed){}
    public record DayCount(java.time.LocalDate day,long value){}
    public record TrendDay(java.time.LocalDate day,long inbound,long outbound,long orders){}
}
