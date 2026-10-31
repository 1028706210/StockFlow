package io.github.stockflow;
import io.github.stockflow.runtime.*;
import org.junit.jupiter.api.Test;
import javax.sql.DataSource;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class RuntimeProbeTest {
    @Test void unavailableDatabaseDoesNotReportReady()throws Exception{
        var source=mock(DataSource.class);when(source.getConnection()).thenThrow(new SQLException("offline"));assertFalse(new DatabaseReadiness(source).ready());
    }
    @Test void requestMetricsKeepFixedStatusBucketsAndAccumulateDuration(){
        var metrics=new RuntimeMetrics();metrics.request(200,20);metrics.request(503,30);var snapshot=metrics.snapshot();var requests=(java.util.Map<?,?>)snapshot.get("requests");assertEquals(2L,requests.get("count"));assertEquals(50L,requests.get("durationNanos"));var buckets=(java.util.Map<?,?>)requests.get("statusClasses");assertEquals(5,buckets.size());assertEquals(1L,buckets.get("2xx"));assertEquals(1L,buckets.get("5xx"));
    }
    @Test void wraparoundExpiryQueryFailureIsCountedAsBatchFailure(){
        var mapper=mock(io.github.stockflow.order.OrderMapper.class);var service=mock(io.github.stockflow.order.OrderService.class);var metrics=new RuntimeMetrics();var expiry=new io.github.stockflow.fulfillment.OrderExpiration(mapper,service,false);org.springframework.test.util.ReflectionTestUtils.setField(expiry,"metrics",metrics);
        when(mapper.expiredIds(0)).thenReturn(java.util.List.of(1L)).thenThrow(new IllegalStateException("database unavailable"));when(mapper.expiredIds(1)).thenReturn(java.util.List.of());expiry.expireBatch();assertThrows(IllegalStateException.class,expiry::expireBatch);var counts=(java.util.Map<?,?>)metrics.snapshot().get("expiry");assertEquals(2L,counts.get("batches"));assertEquals(1L,counts.get("batchFailures"));
    }
}
