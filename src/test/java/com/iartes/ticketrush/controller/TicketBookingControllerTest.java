package com.iartes.ticketrush.controller;

import com.iartes.ticketrush.domain.Seat;
import com.iartes.ticketrush.domain.SeatStatus;
import com.iartes.ticketrush.exception.BusinessRuleException;
import com.iartes.ticketrush.exception.SeatConflictException;
import com.iartes.ticketrush.service.TicketBookingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TicketBookingController.class)
class TicketBookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TicketBookingService ticketBookingService;

    @Test
    @DisplayName("POST /reserve deve retornar 200 OK com dados do assento reservado")
    void shouldReturn200WhenReservationSucceeds() throws Exception {
        Seat reservedSeat = Seat.builder()
                .seatNumber("A10")
                .price(new BigDecimal("150.00"))
                .status(SeatStatus.RESERVED)
                .reservedByCpf("12345678901")
                .reservedUntil(LocalDateTime.of(2026, 10, 19, 14, 10))
                .build();

        when(ticketBookingService.reserveSeat("A10", "12345678901")).thenReturn(reservedSeat);

        mockMvc.perform(post("/api/seats/A10/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cpf": "12345678901"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seatNumber").value("A10"))
                .andExpect(jsonPath("$.status").value("RESERVED"))
                .andExpect(jsonPath("$.reservedByCpf").value("12345678901"));
    }

    @Test
    @DisplayName("POST /reserve deve retornar 400 Bad Request para CPF inválido sem acionar o Service")
    void shouldReturn400WhenCpfIsInvalidAndNotCallService() throws Exception {
        mockMvc.perform(post("/api/seats/A10/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cpf": "123"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Erro de validação"))
                .andExpect(jsonPath("$.errors.cpf").exists());

        verifyNoInteractions(ticketBookingService);
    }

    @Test
    @DisplayName("POST /reserve deve retornar 409 Conflict quando houver colisão de concorrência")
    void shouldReturn409WhenSeatConflictOccurs() throws Exception {
        when(ticketBookingService.reserveSeat("A10", "12345678901"))
                .thenThrow(new SeatConflictException("Assento A10 já está indisponível."));

        mockMvc.perform(post("/api/seats/A10/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cpf": "12345678901"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflito de reserva"))
                .andExpect(jsonPath("$.detail").value("Assento A10 já está indisponível."));
    }

    @Test
    @DisplayName("POST /confirm deve retornar 422 Unprocessable Entity quando a reserva estiver expirada")
    void shouldReturn422WhenReservationIsExpired() throws Exception {
        when(ticketBookingService.confirmPurchase("A10", "12345678901", "tok_visa"))
                .thenThrow(new BusinessRuleException("O tempo da reserva expirou."));

        mockMvc.perform(post("/api/seats/A10/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cpf": "12345678901",
                                  "cardToken": "tok_visa"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Regra de negócio violada"))
                .andExpect(jsonPath("$.detail").value("O tempo da reserva expirou."));
    }
}
