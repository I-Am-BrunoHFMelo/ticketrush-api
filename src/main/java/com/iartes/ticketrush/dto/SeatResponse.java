package com.iartes.ticketrush.dto;

import com.iartes.ticketrush.domain.Seat;
import com.iartes.ticketrush.domain.SeatStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatResponse {

    private String seatNumber;
    private BigDecimal price;
    private SeatStatus status;
    private String reservedByCpf;
    private LocalDateTime reservedUntil;

    public static SeatResponse fromEntity(Seat seat) {
        return SeatResponse.builder()
                .seatNumber(seat.getSeatNumber())
                .price(seat.getPrice())
                .status(seat.getStatus())
                .reservedByCpf(seat.getReservedByCpf())
                .reservedUntil(seat.getReservedUntil())
                .build();
    }
}
