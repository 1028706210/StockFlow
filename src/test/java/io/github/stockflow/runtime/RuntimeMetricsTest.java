package io.github.stockflow.runtime;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeMetricsTest {
    static final class TestClock extends Clock {
        Instant now=Instant.parse("2026-11-02T06:00:00Z");
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return now;}
    }
    Map<?,?> expiry(RuntimeMetrics metrics){return (Map<?,?>)metrics.snapshot().get("expiry");}
    Map<?,?> batch(RuntimeMetrics metrics){return (Map<?,?>)expiry(metrics).get("lastBatch");}
    @Test void emptyBatchCanSucceedAndLaterRecoveryPreservesHistoricalFailure(){
        var clock=new TestClock();var metrics=new RuntimeMetrics(clock);
        assertEquals("NEVER",batch(metrics).get("status"));assertNull(batch(metrics).get("completedAt"));
        metrics.expiryBatch();assertEquals("RUNNING",batch(metrics).get("status"));
        metrics.expiryBatchFailed();clock.now=clock.now.plusSeconds(1);metrics.expiryCompleted();
        assertEquals("FAILED",batch(metrics).get("status"));assertNull(expiry(metrics).get("lastSuccessfulAt"));
        var historicalFailure=expiry(metrics).get("lastFailureAt");assertNotNull(historicalFailure);
        clock.now=clock.now.plusSeconds(30);metrics.expiryBatch();metrics.expiryCompleted();
        assertEquals("SUCCEEDED",batch(metrics).get("status"));assertEquals(0L,batch(metrics).get("processed"));assertEquals(0L,batch(metrics).get("failed"));
        assertEquals(clock.now.toString(),expiry(metrics).get("lastSuccessfulAt"));assertEquals(historicalFailure,expiry(metrics).get("lastFailureAt"));
        assertEquals(2L,expiry(metrics).get("batches"));assertEquals(1L,expiry(metrics).get("batchFailures"));
    }
    @Test void partialFailureAndRunningReplacementDoNotExposePreviousBatchAsCurrentSuccess(){
        var metrics=new RuntimeMetrics(new TestClock());metrics.expiryBatch();metrics.expirySucceeded();metrics.expiryFailed();metrics.expiryCompleted();
        assertEquals("PARTIAL_FAILED",batch(metrics).get("status"));assertEquals(1L,batch(metrics).get("processed"));assertEquals(1L,batch(metrics).get("failed"));
        metrics.expiryBatch();assertEquals("RUNNING",batch(metrics).get("status"));assertNull(batch(metrics).get("completedAt"));assertEquals(0L,batch(metrics).get("failed"));
        var process=(Map<?,?>)metrics.snapshot().get("process");assertNotNull(process.get("startedAt"));assertTrue((long)process.get("uptimeSeconds")>=0);
    }
    @Test void databaseFailureSnapshotDoesNotIncludeConnectionDetails(){
        var source=org.mockito.Mockito.mock(javax.sql.DataSource.class);
        try{org.mockito.Mockito.when(source.getConnection()).thenThrow(new java.sql.SQLException("jdbc:private password=private"));}catch(java.sql.SQLException impossible){throw new AssertionError(impossible);}
        var controller=new RuntimeController(new DatabaseReadiness(source),new RuntimeMetrics(),false,30000);
        var status=controller.status();assertEquals("DOWN",status.get("databaseStatus"));assertEquals(false,status.get("expiryEnabled"));
        assertFalse(status.toString().contains("private"));assertFalse(status.toString().contains("jdbc:"));
    }
}
