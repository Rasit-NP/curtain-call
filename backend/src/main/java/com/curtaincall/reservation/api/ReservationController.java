package com.curtaincall.reservation.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.curtaincall.auth.api.CurrentUserId;
import com.curtaincall.reservation.application.ReservationQueryService;

/** 내 예매(spec 6.8). 로그인이 필요한 경로다(보안 설정은 SecurityConfig, R6). */
@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationQueryService reservationQueryService;

    public ReservationController(ReservationQueryService reservationQueryService) {
        this.reservationQueryService = reservationQueryService;
    }

    /** spec 6.8 내 예매. 오류: 401 AUTH_REQUIRED(보안 설정). 없으면 {@code []}. */
    @GetMapping
    public List<ReservationResponse> findMine(@CurrentUserId long userId) {
        return reservationQueryService.findMine(userId).stream().map(ReservationResponse::from).toList();
    }
}
