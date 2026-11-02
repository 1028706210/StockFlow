package io.github.stockflow.auth;
import io.github.stockflow.api.ApiErrors;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

final class SessionValidityFilter extends OncePerRequestFilter {
    private final AccountMapper accounts;
    SessionValidityFilter(AccountMapper accounts){this.accounts=accounts;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        String path=request.getServletPath();
        // 页面壳和静态文件保持公开，过期身份由后续业务API拒绝并清理。
        if(SecurityConfiguration.PRESENTATION_PATHS.contains(path)||path.startsWith("/assets/ui/")){chain.doFilter(request,response);return;}
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        if(authentication!=null&&authentication.getPrincipal() instanceof AccountPrincipal principal){
            AccountMapper.Stamp stamp;
            try{stamp=accounts.stamp(principal.id());}catch(DataAccessException exception){ApiErrors.write(request,response,503,"ACCOUNT_CHECK_UNAVAILABLE","账号状态暂时无法核验，请稍后重试");return;}
            if(stamp==null||!stamp.enabled()||stamp.authVersion()!=principal.authVersion()){
                var session=request.getSession(false);if(session!=null)session.invalidate();SecurityContextHolder.clearContext();ApiErrors.writeSecurity(request,response,401);return;
            }
        }
        chain.doFilter(request,response);
    }
}
