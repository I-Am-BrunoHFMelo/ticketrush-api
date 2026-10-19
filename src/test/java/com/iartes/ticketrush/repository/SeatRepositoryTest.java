package com.iartes.ticketrush.repository;

import com.iartes.ticketrush.domain.Seat;
import com.iartes.ticketrush.domain.SeatStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class SeatRepositoryTest {

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("Deve buscar apenas reservas cujo tempo limite já expirou")
    void shouldFindOnlyExpiredReservations() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 19, 14, 0);

        Seat expiredSeat = Seat.builder()
                .seatNumber("A01")
                .price(new BigDecimal("100.00"))
                .status(SeatStatus.RESERVED)
                .reservedByCpf("11111111111")
                .reservedUntil(now.minusMinutes(1))
                .build();

        Seat activeReservation = Seat.builder()
                .seatNumber("A02")
                .price(new BigDecimal("100.00"))
                .status(SeatStatus.RESERVED)
                .reservedByCpf("22222222222")
                .reservedUntil(now.plusMinutes(5))
                .build();

        seatRepository.saveAllAndFlush(List.of(expiredSeat, activeReservation));
        entityManager.clear();

        List<Seat> expired = seatRepository.findExpiredReservations(now);

        assertThat(expired)
                .hasSize(1)
                .extracting(Seat::getSeatNumber)
                .containsExactly("A01");
    }

    @Test
    @DisplayName("Deve lançar OptimisticLockingFailureException quando dois acessos atualizarem a mesma versão do assento")
    void shouldThrowOptimisticLockingExceptionOnConcurrentUpdate() {
        Seat initial = seatRepository.saveAndFlush(Seat.builder()
                .seatNumber("B10")
                .price(new BigDecimal("200.00"))
                .status(SeatStatus.AVAILABLE)
                .build());
        entityManager.clear();

        Seat user1View = seatRepository.findById(initial.getId()).orElseThrow();
        entityManager.detach(user1View);

        Seat user2View = seatRepository.findById(initial.getId()).orElseThrow();
        entityManager.detach(user2View);

        user1View.setStatus(SeatStatus.RESERVED);
        user1View.setReservedByCpf("11111111111");
        seatRepository.saveAndFlush(user1View);

        user2View.setStatus(SeatStatus.RESERVED);
        user2View.setReservedByCpf("22222222222");

        assertThatThrownBy(() -> seatRepository.saveAndFlush(user2View))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }
}
