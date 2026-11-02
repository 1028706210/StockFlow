package io.github.stockflow.auth;
import io.github.stockflow.api.ApiErrors;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.security.core.context.SecurityContextHolder;

final class AuthThrottleFilter extends OncePerRequestFilter {
    private final AuthRateLimits limits;
    AuthThrottleFilter(AuthRateLimits limits){this.limits=limits;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        String path=request.getServletPath(),ip=request.getRemoteAddr(),header=request.getHeader("Authorization");
        boolean login=path.equals("/api/auth/login")&&request.getMethod().equals("POST");
        boolean registration=path.equals("/api/auth/register")&&request.getMethod().equals("POST");
        boolean password=path.equals("/api/auth/password")&&request.getMethod().equals("POST");
        boolean basic=header!=null&&header.regionMatches(true,0,"Basic ",0,6);
        String username=login?request.getParameter("username"):basic?basicUsername(header):null;
        if(login&&username!=null)username=username.trim();
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        String passwordUser=authentication==null?username:authentication.getName();
        var permit=login||basic?limits.beginLogin(ip,username):null;
        if(permit!=null&&permit.retryAfter()>0){limited(request,response,permit.retryAfter());return;}
        boolean completed=false;
        try{
            long wait=registration?limits.registerWait(ip):password?limits.passwordWait(ip,passwordUser):0;
            if(wait>0){limited(request,response,wait);return;}
            chain.doFilter(request,response);completed=true;
        }finally{
            boolean failed=completed&&response.getStatus()==401;
            limits.finishLogin(permit,failed,completed&&response.getStatus()<400);
            if(permit!=null&&failed)LoggerFactory.getLogger(AuthThrottleFilter.class).warn("认证失败 requestId={}",request.getAttribute("requestId"));
        }
    }
    private static void limited(HttpServletRequest request,HttpServletResponse response,long wait)throws IOException{response.setHeader("Retry-After",Long.toString(wait));ApiErrors.write(request,response,429,"RATE_LIMITED","操作过于频繁，请稍后重试");}
    private static String basicUsername(String value){
        if(value.length()>1024)return null;
        try{String decoded=new String(Base64.getDecoder().decode(value.substring(6)),StandardCharsets.UTF_8);int separator=decoded.indexOf(':');return separator<0?null:decoded.substring(0,separator);}catch(IllegalArgumentException exception){return null;}
    }
}
