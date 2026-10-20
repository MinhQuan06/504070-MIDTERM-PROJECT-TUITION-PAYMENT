package vn.edu.tdtu.ibanking.paymentservice;
import org.junit.jupiter.api.Test;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.web.client.RestClient;
import vn.edu.tdtu.ibanking.paymentservice.client.StudentServiceClient;
import static org.junit.jupiter.api.Assertions.*;
class StudentPaymentResultTest {
    @Test void onlyExplicitBusinessRejectionAllowsRefund() throws Exception {
        var status=new AtomicInteger(200);
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/Tuition/1/pay",ex->{
            ex.getRequestBody().readAllBytes();
            ex.sendResponseHeaders(status.get(),-1);ex.close();
        });
        server.start();
        try {
            var client=new StudentServiceClient(RestClient.builder(),"http://127.0.0.1:"+server.getAddress().getPort(),false,"test-key");
            assertEquals(StudentServiceClient.PaymentResult.APPLIED,client.markTuitionAsPaid(1L,1L,"SV001",1000L,0));
            for(int s:new int[]{400,404,409,422}){
                status.set(s);assertEquals(StudentServiceClient.PaymentResult.REJECTED,client.markTuitionAsPaid(1L,1L,"SV001",1000L,0));
            }
            for(int s:new int[]{302,401,403,429,500,503}){
                status.set(s);assertEquals(StudentServiceClient.PaymentResult.UNKNOWN,client.markTuitionAsPaid(1L,1L,"SV001",1000L,0));
            }
        } finally {server.stop(0);}
    }
}
