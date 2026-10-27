package io.github.stockflow.order;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
public final class OrderModels {
    private OrderModels(){}
    public record CreateOrder(
        @NotBlank @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9._:-]{0,63}")String requestKey,
        @NotNull @Size(min=1,max=990)List<OrderLines.Line> items){}
    public record Row(long id,long buyerId,String requestKey,String payloadHash,String status,BigDecimal total,LocalDateTime expiresAt,LocalDateTime createdAt,String cancelReason,LocalDateTime confirmedAt,LocalDateTime shippedAt,LocalDateTime completedAt,LocalDateTime cancelledAt){}
    public record Item(long skuId,int quantity,BigDecimal unitPrice,String skuCode,String skuName){}
    public record Summary(long id,String status,BigDecimal total,LocalDateTime expiresAt,long buyerId,String cancelReason){}
    public record View(long id,String status,BigDecimal total,LocalDateTime expiresAt,List<Item> items,long buyerId,String cancelReason,LocalDateTime createdAt,LocalDateTime confirmedAt,LocalDateTime shippedAt,LocalDateTime completedAt,LocalDateTime cancelledAt){}
    public record Creation(View order,boolean replayed){}
}
