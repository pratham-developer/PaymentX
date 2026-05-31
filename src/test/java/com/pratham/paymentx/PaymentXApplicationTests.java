package com.pratham.paymentx;

import com.cashfree.pg.ApiException;
import com.cashfree.pg.ApiResponse;
import com.cashfree.pg.Cashfree;
import com.cashfree.pg.model.OrderEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class PaymentXApplicationTests {

    @Autowired
    private Cashfree cashfree;

    @Test
    void contextLoads() {
    }

    @Test
    void testStatus() throws ApiException {
        ApiResponse<OrderEntity> response = cashfree.PGFetchOrder("44a30843-83fe-4e71-984c-b0425df92e28", null, null, null);
        System.out.println(response.getData());
        //String gatewayStatus = response.getData().getOrderStatus();
    }

}
