package com.iartes.ticketrush.exception;

public class SeatNotFoundException extends RuntimeException {
    public SeatNotFoundException(String seatNumber) {
        super("Assento não encontrado: " + seatNumber);
    }
}
