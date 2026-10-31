package io.github.stockflow.workbench;
import io.github.stockflow.api.ApiException;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Inclusive calendar days and exact decimal bounds, shared by lists and exports. */
public record QueryRange(LocalDate from,LocalDate toExclusive,BigDecimal minTotal,BigDecimal maxTotal){
    public static QueryRange of(LocalDate from,LocalDate to,BigDecimal min,BigDecimal max){
        if((from!=null&&to!=null&&from.isAfter(to))||(min!=null&&max!=null&&min.compareTo(max)>0))throw invalid();
        for(var amount:new BigDecimal[]{min,max})if(amount!=null&&(amount.signum()<0||amount.scale()>2||amount.compareTo(new BigDecimal("999999999999.99"))>0))throw invalid();
        if((from!=null&&(from.getYear()<1000||from.getYear()>9998))||(to!=null&&(to.getYear()<1000||to.getYear()>9998)))throw invalid();
        return new QueryRange(from,to==null?null:to.plusDays(1),min,max);
    }
    private static ApiException invalid(){return new ApiException(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","Invalid date or amount range");}
}
