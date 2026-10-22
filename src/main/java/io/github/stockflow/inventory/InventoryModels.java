package io.github.stockflow.inventory;
import jakarta.validation.constraints.*;
public final class InventoryModels {
    private InventoryModels(){}
    public record Stock(long skuId,String code,long onHand,long reserved,long available){}
    public record Receipt(
        @NotBlank @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9._:-]{0,63}")String operationKey,
        @Min(1) @Max(1000000)int quantity){}
}
