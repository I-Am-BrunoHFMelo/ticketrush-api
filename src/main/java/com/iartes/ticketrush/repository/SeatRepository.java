package com.iartes.ticketrush.repository;

import com.iartes.ticketrush.domain.Seat;
import com.iartes.ticketrush.domain.SeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    Optional<Seat> findBySeatNumber(String seatNumber);

    List<Seat> findByStatusOrderBySeatNumberAsc(SeatStatus status);

    @Query("SELECT s FROM Seat s WHERE s.status = 'RESERVED' AND s.reservedUntil < :now")
    List<Seat> findExpiredReservations(@Param("now") LocalDateTime now);
}
