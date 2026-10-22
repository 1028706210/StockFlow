package io.github.stockflow.auth;
import org.springframework.security.core.Authentication;
import java.util.Map;
public final class Actor {
    private static final Map<String,Long> IDS=Map.of("buyer",1L,"buyer2",2L,"warehouse",3L,"admin",4L);
    private Actor(){}
    public static long id(Authentication authentication){
        var id=IDS.get(authentication.getName());
        if(id==null)throw new IllegalStateException("Unknown local principal");
        return id;
    }
}
