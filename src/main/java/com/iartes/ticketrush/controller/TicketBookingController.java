package com.iartes.ticketrush.controller;

import com.iartes.ticketrush.domain.Seat;
import com.iartes.ticketrush.dto.ConfirmPurchaseRequest;
import com.iartes.ticketrush.dto.ReserveSeatRequest;
import com.iartes.ticketrush.dto.SeatResponse;
import com.iartes.ticketrush.service.TicketBookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/seats")
@RequiredArgsConstructor
public class TicketBookingController {

    private final TicketBookingService ticketBookingService;

    @GetMapping("/available")
    public ResponseEntity<List<SeatResponse>> listAvailable() {
        List<SeatResponse> response = ticketBookingService.listAvailableSeats()
                .stream()
                .map(SeatResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{seatNumber}/reserve")
    public ResponseEntity<SeatResponse> reserveSeat(
            @PathVariable String seatNumber,
            @Valid @RequestBody ReserveSeatRequest request) {
        Seat reserved = ticketBookingService.reserveSeat(seatNumber, request.getCpf());
        return ResponseEntity.ok(SeatResponse.fromEntity(reserved));
    }

    @PostMapping("/{seatNumber}/confirm")
    public ResponseEntity<SeatResponse> confirmPurchase(
            @PathVariable String seatNumber,
            @Valid @RequestBody ConfirmPurchaseRequest request) {
        Seat confirmed = ticketBookingService.confirmPurchase(
                seatNumber, request.getCpf(), request.getCardToken());
        return ResponseEntity.ok(SeatResponse.fromEntity(confirmed));
    }
}
