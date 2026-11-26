package com.curtaincall.hold.domain;

/**
 * 선점의 저장 상태(spec 2.1, 2.3). 만료는 저장하지 않는다(R3 지연 만료): 만료된 선점도 저장 상태는 ACTIVE이고,
 * 유효한지는 현재 시각과 만료 시각으로 판정한다({@link Hold#isValidAt}).
 */
public enum HoldStatus {
    ACTIVE,
    RELEASED,
    PAID
}
