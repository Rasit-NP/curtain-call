package com.curtaincall.performance.domain;

/** 좌석의 저장 상태(spec 2.1). API 상태(만료 반영, spec 2.2)는 선점 Issue에서 이 값과 좌석을 잡은 선점으로 계산한다. */
public enum SeatStatus {
    AVAILABLE,
    HELD,
    SOLD
}
