package com.curtaincall.hold.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.curtaincall.auth.api.CurrentUserId;
import com.curtaincall.hold.application.HoldService;

import jakarta.validation.Valid;

/** 선점(spec 6.4), 내 선점 조회(6.5)·해제(6.6). 로그인이 필요한 경로다(보안 설정은 SecurityConfig, R6). */
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

    /**
     * spec 6.5 내 선점 조회. 오류 순서: 401 AUTH_REQUIRED(보안 설정) → 400 INVALID_INPUT(holdId가 정수가 아님, 결정 0090·0091의
     * 바인더) → 404 NOT_FOUND(서비스). 만료된 선점은 200이고 status가 EXPIRED다.
     */
    @GetMapping("/{holdId}")
    public HoldResponse get(@CurrentUserId long userId, @PathVariable long holdId) {
        return HoldResponse.from(holdService.get(userId, holdId));
    }

    /**
     * spec 6.6 내 선점 해제. 묶음 전체를 해제하고 204다. 만료된 내 선점도 204다. 오류 순서는 조회와 같다(401 → 400 → 404).
     */
    @DeleteMapping("/{holdId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(@CurrentUserId long userId, @PathVariable long holdId) {
        holdService.release(userId, holdId);
    }
}
