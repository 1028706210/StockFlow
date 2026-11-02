package io.github.stockflow.auth;
import java.time.Clock;
import java.util.*;

/** 单实例有界计数；容量耗尽时暂时拒绝新尝试，不淘汰有效计数以绕过限制。 */
public final class AuthRateLimits {
    private static final long WINDOW=600;
    private static final class Window {long expires;int count,inFlight;Window(long expires){this.expires=expires;}}
    public static final class LoginPermit {
        private final Window user,ip;private final long retryAfter;private boolean completed;
        private LoginPermit(long wait,Window user,Window ip){this.retryAfter=wait;this.user=user;this.ip=ip;}
        public long retryAfter(){return retryAfter;}
    }
    private final Clock clock;private final int failures,registrations,capacity;
    private final Map<String,Window> windows=new HashMap<>();private long overflowUntil;
    public AuthRateLimits(Clock clock,int failures,int registrations,int capacity){
        if(failures<1||registrations<1||capacity<4)throw new IllegalArgumentException("限流配置无效");
        this.clock=clock;this.failures=failures;this.registrations=registrations;this.capacity=capacity;
    }
    private long now(){long now=clock.instant().getEpochSecond();windows.values().removeIf(value->value.expires<=now&&value.inFlight==0);return now;}
    private String userKey(String ip,String username){return "user|"+ip+"|"+(username!=null&&username.matches("[a-z][a-z0-9_]{2,31}")?username:"_invalid_");}
    private long waitFor(String key,int maximum,long now){var window=windows.get(key);if(window==null)return 0;if(window.count>=maximum)return Math.max(1,window.expires-now);return window.count+window.inFlight>=maximum?1:0;}
    private void increment(String key,long now){
        var value=windows.get(key);
        if(value==null){if(windows.size()>=capacity){overflowUntil=Math.max(overflowUntil,now+WINDOW);return;}value=new Window(now+WINDOW);windows.put(key,value);}
        if(value.count<Integer.MAX_VALUE)value.count++;
    }
    public synchronized long loginWait(String ip,String username){long now=now();return Math.max(Math.max(0,overflowUntil-now),Math.max(waitFor(userKey(ip,username),failures,now),waitFor("ip|"+ip,Math.max(60,failures*10),now)));}
    public synchronized void failure(String ip,String username){long now=now();increment("ip|"+ip,now);increment(userKey(ip,username),now);}
    public synchronized void success(String ip,String username){now();var window=windows.get(userKey(ip,username));if(window!=null)window.count=0;}
    public synchronized LoginPermit beginLogin(String ip,String username){
        long now=now(),wait=loginWait(ip,username);if(wait>0)return new LoginPermit(wait,null,null);
        String userKey=userKey(ip,username),ipKey="ip|"+ip;
        int needed=(windows.containsKey(userKey)?0:1)+(windows.containsKey(ipKey)?0:1);
        if(windows.size()+needed>capacity){overflowUntil=Math.max(overflowUntil,now+WINDOW);return new LoginPermit(WINDOW,null,null);}
        var user=windows.computeIfAbsent(userKey,key->new Window(now+WINDOW));var source=windows.computeIfAbsent(ipKey,key->new Window(now+WINDOW));
        user.inFlight++;source.inFlight++;return new LoginPermit(0,user,source);
    }
    public synchronized void finishLogin(LoginPermit permit,boolean failed,boolean authenticated){
        if(permit==null||permit.retryAfter>0||permit.completed)return;permit.completed=true;
        permit.user.inFlight--;permit.ip.inFlight--;
        if(failed){permit.user.count++;permit.ip.count++;}else if(authenticated)permit.user.count=0;
    }
    private long consume(String key,int limit){long now=now(),wait=Math.max(Math.max(0,overflowUntil-now),waitFor(key,limit,now));if(wait==0){increment(key,now);wait=Math.max(0,overflowUntil-now);}return wait;}
    public synchronized long registerWait(String ip){return consume("register|"+ip,registrations);}
    public synchronized long passwordWait(String ip,String username){return consume("password|"+userKey(ip,username),failures);}
    synchronized int trackedKeys(){return windows.size();}
}
