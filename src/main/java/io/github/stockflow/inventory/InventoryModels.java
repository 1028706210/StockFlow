package io.github.stockflow.inventory;
import jakarta.validation.constraints.*;
public final class InventoryModels {
    private InventoryModels(){}
    public record Stock(long skuId,String code,long onHand,long reserved,long available,int safetyStock){}
    public record StockPolicy(@NotNull @Min(1) @Max(1000000) Integer safetyStock){}
    public record Receipt(
        @NotBlank @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9._:-]{0,63}")String operationKey,
        @Min(1) @Max(1000000)int quantity){}
    public record LedgerEntry(long id,long skuId,String code,String name,Long orderId,Long stocktakeId,Long receiptId,Long returnId,long actorId,
        String operationType,int quantity,java.time.LocalDateTime createdAt,long onHandDelta,long reservedDelta){}
    public record LedgerPage(java.util.List<LedgerEntry> items,Long nextCursor){}
    public record Alert(long skuId,String code,String name,long onHand,long reserved,long available,int safetyStock,int threshold,long shortage){}
    public record AlertPage(java.util.List<Alert> items,Long nextCursor,long total,Integer threshold){}
}
