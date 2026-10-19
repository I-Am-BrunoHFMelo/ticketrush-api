package com.iartes.ticketrush.gateway;

public interface NotificationService {
    void sendConfirmation(String cpf, String seatNumber);
}
