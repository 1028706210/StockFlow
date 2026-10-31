package io.github.stockflow.workbench;

import java.math.BigDecimal;
import java.util.List;

public final class WorkbenchModels {
    private WorkbenchModels(){}
    public record Page<T>(List<T> items,long total,int page,int size,long totalPages){}
    public record StockRow(long skuId,String code,String name,BigDecimal price,boolean active,long onHand,long reserved,long available,int safetyStock){}
    public record Dashboard(long productCount,long onHand,long reserved,long lowStockCount,long orderCount,long pendingCount,BigDecimal orderTotal,long confirmedCount,long shippedCount){}
}
