package com.curtaincall.hold.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.curtaincall.auth.api.CurrentUserId;
import com.curtaincall.hold.application.HoldService;

import jakarta.validation.Valid;

/** 선점(spec 6.4). 로그인이 필요한 경로다(보안 설정은 SecurityConfig, R6). */
@RestController
@RequestMapping("/api/holds")
public class HoldController {

    private final HoldService holdService;

    public HoldController(HoldService holdService) {
        this.holdService = holdService;
    }

    /**
     * spec 6.4 좌석 묶음 선점. 오류 순서: 401 AUTH_REQUIRED(보안 설정) → 400 INVALID_INPUT(요청 검증) → 404 NOT_FOUND
     * → 409 HOLD_LIMIT_EXCEEDED → 409 SEAT_ALREADY_HELD(서비스). 이 요청은 멱등하지 않다.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public HoldResponse create(@CurrentUserId long userId, @Valid @RequestBody CreateHoldRequest request) {
        return HoldResponse.from(holdService.create(userId, request.performanceId(), request.seatIds()));
    }
}
