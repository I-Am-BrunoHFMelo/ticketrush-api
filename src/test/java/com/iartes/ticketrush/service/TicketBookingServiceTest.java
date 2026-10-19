package com.iartes.ticketrush.service;

import com.iartes.ticketrush.domain.Seat;
import com.iartes.ticketrush.domain.SeatStatus;
import com.iartes.ticketrush.exception.BusinessRuleException;
import com.iartes.ticketrush.exception.SeatConflictException;
import com.iartes.ticketrush.gateway.NotificationService;
import com.iartes.ticketrush.gateway.PaymentGateway;
import com.iartes.ticketrush.repository.SeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketBookingServiceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-10-19T14:00:00Z");
    private static final ZoneOffset ZONE = ZoneOffset.UTC;

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private NotificationService notificationService;

    @Mock
    private Clock clock;

    @InjectMocks
    private TicketBookingService ticketBookingService;

    @Captor
    private ArgumentCaptor<Seat> seatCaptor;

    @BeforeEach
    void setUpClock() {
        lenient().when(clock.instant()).thenReturn(FIXED_INSTANT);
        lenient().when(clock.getZone()).thenReturn(ZONE);
    }

    @Test
    @DisplayName("Deve reservar assento disponível com expiração exata de 10 minutos")
    void shouldReserveAvailableSeatWithTenMinutesExpiration() {
        Seat seat = Seat.builder()
                .id(1L)
                .seatNumber("A10")
                .price(new BigDecimal("150.00"))
                .status(SeatStatus.AVAILABLE)
                .build();

        when(seatRepository.findBySeatNumber("A10")).thenReturn(Optional.of(seat));
        when(seatRepository.save(any(Seat.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ticketBookingService.reserveSeat("A10", "12345678901");

        verify(seatRepository).save(seatCaptor.capture());
        Seat savedSeat = seatCaptor.getValue();

        assertThat(savedSeat.getStatus()).isEqualTo(SeatStatus.RESERVED);
        assertThat(savedSeat.getReservedByCpf()).isEqualTo("12345678901");
        assertThat(savedSeat.getReservedUntil()).isEqualTo(LocalDateTime.of(2026, 10, 19, 14, 10, 0));
    }

    @Test
    @DisplayName("Deve converter falha de lock otimista do banco em SeatConflictException")
    void shouldTranslateOptimisticLockingFailureIntoSeatConflictException() {
        Seat seat = Seat.builder()
                .id(1L)
                .seatNumber("A10")
                .price(new BigDecimal("150.00"))
                .status(SeatStatus.AVAILABLE)
                .build();

        when(seatRepository.findBySeatNumber("A10")).thenReturn(Optional.of(seat));
        when(seatRepository.save(any(Seat.class)))
                .thenThrow(new OptimisticLockingFailureException("Concurrent update"));

        assertThatThrownBy(() -> ticketBookingService.reserveSeat("A10", "12345678901"))
                .isInstanceOf(SeatConflictException.class)
                .hasMessageContaining("Conflito de concorrência");
    }

    @Test
    @DisplayName("Deve liberar assento e bloquear cobrança quando a reserva estiver expirada")
    void shouldReleaseSeatAndSkipPaymentWhenReservationIsExpired() {
        Seat seat = Seat.builder()
                .id(1L)
                .seatNumber("A10")
                .price(new BigDecimal("150.00"))
                .status(SeatStatus.RESERVED)
                .reservedByCpf("12345678901")
                .reservedUntil(LocalDateTime.of(2026, 10, 19, 13, 59, 59))
                .build();

        when(seatRepository.findBySeatNumber("A10")).thenReturn(Optional.of(seat));

        assertThatThrownBy(() -> ticketBookingService.confirmPurchase("A10", "12345678901", "tok_visa"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("expirou");

        verify(seatRepository).save(seatCaptor.capture());
        assertThat(seatCaptor.getValue().getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(seatCaptor.getValue().getReservedByCpf()).isNull();

        verifyNoInteractions(paymentGateway, notificationService);
    }

    @Test
    @DisplayName("Deve liberar assento e não enviar notificação quando o pagamento for recusado")
    void shouldReleaseSeatWhenPaymentIsDeclined() {
        Seat seat = Seat.builder()
                .id(1L)
                .seatNumber("A10")
                .price(new BigDecimal("150.00"))
                .status(SeatStatus.RESERVED)
                .reservedByCpf("12345678901")
                .reservedUntil(LocalDateTime.of(2026, 10, 19, 14, 5, 0))
                .build();

        when(seatRepository.findBySeatNumber("A10")).thenReturn(Optional.of(seat));
        when(paymentGateway.authorizePayment("12345678901", new BigDecimal("150.00"), "tok_declined"))
                .thenReturn(false);

        assertThatThrownBy(() -> ticketBookingService.confirmPurchase("A10", "12345678901", "tok_declined"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("recusado");

        verify(seatRepository).save(seatCaptor.capture());
        assertThat(seatCaptor.getValue().getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        verify(notificationService, never()).sendConfirmation(any(), any());
    }

    @Test
    @DisplayName("Deve autorizar pagamento, salvar venda e notificar na ordem correta")
    void shouldConfirmPurchaseAndNotifyInOrder() {
        Seat seat = Seat.builder()
                .id(1L)
                .seatNumber("A10")
                .price(new BigDecimal("150.00"))
                .status(SeatStatus.RESERVED)
                .reservedByCpf("12345678901")
                .reservedUntil(LocalDateTime.of(2026, 10, 19, 14, 5, 0))
                .build();

        when(seatRepository.findBySeatNumber("A10")).thenReturn(Optional.of(seat));
        when(paymentGateway.authorizePayment("12345678901", new BigDecimal("150.00"), "tok_ok"))
                .thenReturn(true);
        when(seatRepository.save(any(Seat.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Seat result = ticketBookingService.confirmPurchase("A10", "12345678901", "tok_ok");

        assertThat(result.getStatus()).isEqualTo(SeatStatus.SOLD);

        InOrder inOrder = inOrder(paymentGateway, seatRepository, notificationService);
        inOrder.verify(paymentGateway).authorizePayment("12345678901", new BigDecimal("150.00"), "tok_ok");
        inOrder.verify(seatRepository).save(any(Seat.class));
        inOrder.verify(notificationService).sendConfirmation("12345678901", "A10");
    }
}
