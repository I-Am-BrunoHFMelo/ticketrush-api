package com.iartes.ticketrush.gateway;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class SimulatedPaymentGateway implements PaymentGateway {

    @Override
    public boolean authorizePayment(String cpf, BigDecimal amount, String cardToken) {
        return cardToken != null && !cardToken.startsWith("INVALID");
    }
}
