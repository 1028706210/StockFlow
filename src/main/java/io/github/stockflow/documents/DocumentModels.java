package io.github.stockflow.documents;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class DocumentModels {
    private DocumentModels(){}
    public record Line(@Positive long skuId,@Min(1) @Max(1000000) int quantity){}
    public record CreateReceipt(
        @NotBlank @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9._:-]{0,63}") String requestKey,
        @NotBlank @Size(max=240) String source,
        @NotNull @Size(min=1,max=10) List<@NotNull @Valid Line> items){}
    public record CreateReturn(
        @NotBlank @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9._:-]{0,63}") String requestKey,
        @Positive long orderId,@NotBlank @Size(max=240) String reason){}
    public record ReceiptItem(long id,long skuId,String skuCode,String skuName,int quantity){}
    public record ReturnItem(long id,long skuId,String skuCode,String skuName,int quantity,BigDecimal unitPrice){}
    public record Receipt(long id,String source,String status,long actorId,LocalDateTime createdAt,LocalDateTime confirmedAt,List<ReceiptItem> items){}
    public record Return(long id,long orderId,long buyerId,String reason,String status,LocalDateTime createdAt,LocalDateTime confirmedAt,List<ReturnItem> items){}
    public record ReceiptRow(long id,long actorId,String requestKey,String payloadHash,String source,String status,LocalDateTime createdAt,LocalDateTime confirmedAt){}
    public record ReturnRow(long id,long orderId,long buyerId,String requestKey,String payloadHash,String reason,String status,LocalDateTime createdAt,LocalDateTime confirmedAt){}
    // 批量查询专用行，父单据 ID 只供内部归组，不加入接口明细结构。
    public record ReceiptItemRow(long receiptId, long id, long skuId, String skuCode, String skuName, int quantity){}
    public record ReturnItemRow(long returnId, long id, long skuId, String skuCode, String skuName, int quantity, BigDecimal unitPrice){}
    public record Creation<T>(T document,boolean replayed){}
}