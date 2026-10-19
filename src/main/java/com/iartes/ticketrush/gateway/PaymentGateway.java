package com.iartes.ticketrush.gateway;

import java.math.BigDecimal;

public interface PaymentGateway {
    boolean authorizePayment(String cpf, BigDecimal amount, String cardToken);
}
