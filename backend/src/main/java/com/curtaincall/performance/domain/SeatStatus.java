package com.curtaincall.performance.domain;

/**
 * 좌석의 저장 상태(spec 2.1)이자 API 상태(spec 2.2)의 값. 응답에는 API 상태를 쓴다: 저장 상태가 HELD라도 좌석을 잡은 선점이 만료됐으면
 * AVAILABLE이다(hold.domain.SeatAvailability).
 */
public enum SeatStatus {
    AVAILABLE,
    HELD,
    SOLD
}
