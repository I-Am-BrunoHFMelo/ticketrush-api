package com.iartes.ticketrush.service;

import com.iartes.ticketrush.domain.Seat;
import com.iartes.ticketrush.domain.SeatStatus;
import com.iartes.ticketrush.exception.BusinessRuleException;
import com.iartes.ticketrush.exception.SeatConflictException;
import com.iartes.ticketrush.exception.SeatNotFoundException;
import com.iartes.ticketrush.gateway.NotificationService;
import com.iartes.ticketrush.gateway.PaymentGateway;
import com.iartes.ticketrush.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TicketBookingService {

    private static final int RESERVATION_MINUTES = 10;

    private final SeatRepository seatRepository;
    private final PaymentGateway paymentGateway;
    private final NotificationService notificationService;
    private final Clock clock;

    public List<Seat> listAvailableSeats() {
        return seatRepository.findByStatusOrderBySeatNumberAsc(SeatStatus.AVAILABLE);
    }

    @Transactional
    public Seat reserveSeat(String seatNumber, String cpf) {
        Seat seat = seatRepository.findBySeatNumber(seatNumber)
                .orElseThrow(() -> new SeatNotFoundException(seatNumber));

        if (seat.getStatus() != SeatStatus.AVAILABLE) {
            throw new SeatConflictException("Assento " + seatNumber + " já está indisponível.");
        }

        seat.setStatus(SeatStatus.RESERVED);
        seat.setReservedByCpf(cpf);
        seat.setReservedUntil(LocalDateTime.now(clock).plusMinutes(RESERVATION_MINUTES));

        try {
            return seatRepository.save(seat);
        } catch (OptimisticLockingFailureException ex) {
            throw new SeatConflictException("Conflito de concorrência: outro usuário reservou o assento " + seatNumber + " simultaneamente.");
        }
    }

    @Transactional
    public Seat confirmPurchase(String seatNumber, String cpf, String cardToken) {
        Seat seat = seatRepository.findBySeatNumber(seatNumber)
                .orElseThrow(() -> new SeatNotFoundException(seatNumber));

        if (seat.getStatus() != SeatStatus.RESERVED || !cpf.equals(seat.getReservedByCpf())) {
            throw new SeatConflictException("Assento não está reservado para o CPF informado.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (seat.getReservedUntil() != null && now.isAfter(seat.getReservedUntil())) {
            releaseSeat(seat);
            seatRepository.save(seat);
            throw new BusinessRuleException("O tempo da reserva expirou.");
        }

        boolean authorized = paymentGateway.authorizePayment(cpf, seat.getPrice(), cardToken);
        if (!authorized) {
            releaseSeat(seat);
            seatRepository.save(seat);
            throw new BusinessRuleException("Pagamento recusado pela operadora.");
        }

        seat.setStatus(SeatStatus.SOLD);
        seat.setReservedUntil(null);

        Seat savedSeat = seatRepository.save(seat);
        notificationService.sendConfirmation(cpf, seatNumber);
        return savedSeat;
    }

    private void releaseSeat(Seat seat) {
        seat.setStatus(SeatStatus.AVAILABLE);
        seat.setReservedByCpf(null);
        seat.setReservedUntil(null);
    }
}
