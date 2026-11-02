package io.github.stockflow.auth;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
class AuthRateLimitsTest {
    static class TestClock extends Clock {long seconds;public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return Instant.ofEpochSecond(seconds);}}
    @Test void failureWindowExpiresAndSuccessfulLoginClearsOnlyItsOwnKey(){
        var clock=new TestClock();var limits=new AuthRateLimits(clock,3,2,4);
        for(int i=0;i<3;i++)limits.failure("127.0.0.1","buyer");
        assertTrue(limits.loginWait("127.0.0.1","buyer")>0);
        limits.success("127.0.0.1","buyer");assertEquals(0,limits.loginWait("127.0.0.1","buyer"));
        limits.failure("127.0.0.1","buyer");clock.seconds=601;assertEquals(0,limits.loginWait("127.0.0.1","buyer"));
    }
    @Test void registrationIsBoundedAndUntrustedKeysCannotGrowMemoryIndefinitely(){
        var clock=new TestClock();var limits=new AuthRateLimits(clock,3,2,4);
        assertEquals(0,limits.registerWait("ip1"));assertEquals(0,limits.registerWait("ip1"));assertTrue(limits.registerWait("ip1")>0);
        for(int i=0;i<20;i++)limits.failure("ip"+i,"buyer");assertTrue(limits.trackedKeys()<=4);
        clock.seconds=601;assertEquals(0,limits.registerWait("ip1"));
    }
    @Test void passwordVerificationAttemptsHaveAnIndependentBudget(){
        var clock=new TestClock();var limits=new AuthRateLimits(clock,3,2,20);
        for(int i=0;i<3;i++)assertEquals(0,limits.passwordWait("ip1","buyer"));
        assertTrue(limits.passwordWait("ip1","buyer")>0);
        assertEquals(0,limits.passwordWait("ip1","buyer2"));
        assertEquals(0,limits.loginWait("ip1","buyer"));
    }
    @Test void concurrentRegistrationAttemptsCannotExceedTheBudget()throws Exception{
        var limits=new AuthRateLimits(new TestClock(),3,2,20);var pool=java.util.concurrent.Executors.newFixedThreadPool(4);
        try{var tasks=new java.util.ArrayList<java.util.concurrent.Callable<Boolean>>();for(int i=0;i<20;i++)tasks.add(()->limits.registerWait("ip1")==0);int allowed=0;for(var result:pool.invokeAll(tasks))if(result.get())allowed++;assertEquals(2,allowed);}finally{pool.shutdownNow();}
    }
    @Test void simultaneousAuthenticationReservationsAreBoundedUntilTheyComplete(){
        var limits=new AuthRateLimits(new TestClock(),3,20,30);
        var permits=new java.util.ArrayList<AuthRateLimits.LoginPermit>();
        for(int i=0;i<3;i++){var permit=limits.beginLogin("ip","buyer");assertEquals(0,permit.retryAfter());permits.add(permit);}
        assertTrue(limits.beginLogin("ip","buyer").retryAfter()>0);
        for(var permit:permits)limits.finishLogin(permit,true,false);
        assertTrue(limits.beginLogin("ip","buyer").retryAfter()>0);
    }
    @Test void successfulReservationIsReleasedAndDoubleCompletionCannotEraseOtherFailures(){
        var limits=new AuthRateLimits(new TestClock(),1,20,30);
        var permit=limits.beginLogin("ip","buyer");limits.finishLogin(permit,false,true);limits.finishLogin(permit,false,true);
        var next=limits.beginLogin("ip","buyer");assertEquals(0,next.retryAfter());limits.finishLogin(next,true,false);
        assertTrue(limits.beginLogin("ip","buyer").retryAfter()>0);
    }
}
