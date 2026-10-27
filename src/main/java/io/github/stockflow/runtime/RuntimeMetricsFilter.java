package io.github.stockflow.runtime;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE+1)
public class RuntimeMetricsFilter extends OncePerRequestFilter {
    private final RuntimeMetrics metrics;
    public RuntimeMetricsFilter(RuntimeMetrics metrics){this.metrics=metrics;}
    @Override protected boolean shouldNotFilter(HttpServletRequest request){return !request.getRequestURI().startsWith("/api/");}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        long started=System.nanoTime();boolean completed=false;
        try{chain.doFilter(request,response);completed=true;}
        finally{metrics.request(completed?response.getStatus():500,System.nanoTime()-started);}
    }
}
