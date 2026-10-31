package io.github.stockflow.api;
import jakarta.servlet.http.*;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import java.io.IOException;

@RestControllerAdvice
public class ApiErrors {
    public record Error(String code,String message,String requestId){}
    private static Error body(HttpServletRequest r,String code,String message){return new Error(code,message,String.valueOf(r.getAttribute("requestId")));}
    @ExceptionHandler(ApiException.class)
    ResponseEntity<Error> business(ApiException e,HttpServletRequest r){return ResponseEntity.status(e.status()).body(body(r,e.code(),e.getMessage()));}
    @ExceptionHandler({IllegalArgumentException.class,MethodArgumentNotValidException.class,ConstraintViolationException.class,HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class})
    ResponseEntity<Error> invalid(Exception e,HttpServletRequest r){return ResponseEntity.badRequest().body(body(r,"INVALID_REQUEST","Request fields are invalid"));}
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Error> denied(HttpServletRequest r){return ResponseEntity.status(403).body(body(r,"FORBIDDEN","Access denied"));}
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<Error> missing(HttpServletRequest r){return ResponseEntity.status(404).body(body(r,"NOT_FOUND","Resource not found"));}
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    ResponseEntity<Error> method(org.springframework.web.HttpRequestMethodNotSupportedException e,HttpServletRequest r){
        var headers=new HttpHeaders();if(e.getSupportedHttpMethods()!=null)headers.setAllow(e.getSupportedHttpMethods());
        return new ResponseEntity<>(body(r,"METHOD_NOT_ALLOWED","Request method is not supported"),headers,HttpStatus.METHOD_NOT_ALLOWED);
    }
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    ResponseEntity<Error> media(HttpServletRequest r){return ResponseEntity.status(415).body(body(r,"UNSUPPORTED_MEDIA_TYPE","Request content type is not supported"));}
    @ExceptionHandler(Exception.class)
    ResponseEntity<Error> unexpected(Exception e,HttpServletRequest r){
        LoggerFactory.getLogger(ApiErrors.class).error("Request {} failed",r.getAttribute("requestId"),e);
        return ResponseEntity.internalServerError().body(body(r,"INTERNAL_ERROR","Unexpected server error"));
    }
    public static void writeSecurity(HttpServletRequest r,HttpServletResponse response,int status) throws IOException {
        response.setStatus(status);response.setContentType("application/json;charset=UTF-8");
        var code=status==401?"UNAUTHENTICATED":"FORBIDDEN";
        var message=status==401?"Authentication required":"Access denied";
        response.getWriter().write("{\"code\":\""+code+"\",\"message\":\""+message+"\",\"requestId\":\""+r.getAttribute("requestId")+"\"}");
    }
}
