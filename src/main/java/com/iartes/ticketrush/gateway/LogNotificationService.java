package com.iartes.ticketrush.gateway;

import org.springframework.stereotype.Component;

@Component
public class LogNotificationService implements NotificationService {

    @Override
    public void sendConfirmation(String cpf, String seatNumber) {
        // Implementação simples para execução local
    }
}
