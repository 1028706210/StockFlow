package io.github.stockflow.catalog;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public final class CatalogModels {
    private CatalogModels(){}
    public record CreateSku(
        @NotBlank @Pattern(regexp="[A-Z0-9_-]{1,40}")String code,
        @NotBlank @Size(max=120)String name,
        @NotNull @DecimalMin("0.00") @DecimalMax("999999.99") @Digits(integer=6,fraction=2)BigDecimal price){}
    public record Sku(long id,String code,String name,BigDecimal price,boolean active){}
    public record UpdateSku(
        @NotBlank @Size(max=120)String name,
        @NotNull @DecimalMin("0.00") @DecimalMax("999999.99") @Digits(integer=6,fraction=2)BigDecimal price,
        @NotNull Boolean active){}
}
