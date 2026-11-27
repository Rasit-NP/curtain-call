package com.curtaincall.payment.domain;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 멱등 기록(spec 2.1, 6.7.1, R5). 같은 사용자·같은 키는 하나뿐이다(DB 유니크 제약). 검증을 통과한 결제 요청이 키를 잡으며 만들고
 * ({@link IdempotencyRecordRepository#claim}), 같은 트랜잭션에서 결과의 응답을 채운다({@link #complete}).
 *
 * <p>응답은 그 트랜잭션 안에서만 비어 있다. 커밋된 기록에는 언제나 응답이 있고, 다른 요청은 커밋된 기록만 읽는다.
 */
@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "hold_id", nullable = false)
    private long holdId;

    @Column(name = "response_status")
    private @Nullable Integer responseStatus;

    @Column(name = "response_body")
    private @Nullable String responseBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdempotencyRecord() {
    }

    /** 이 키를 처음 쓴 요청의 선점인지. 같은 키로 다른 선점을 결제하면 400 INVALID_INPUT이다(spec 6.7.1). */
    public boolean isFor(long holdId) {
        return this.holdId == holdId;
    }

    /** 저장한 응답. 응답을 채우기 전이면 예외다(커밋된 기록에는 없는 일이다). */
    public StoredResponse response() {
        if (responseStatus == null || responseBody == null) {
            throw new IllegalStateException("응답을 저장하지 않은 멱등 기록이다: " + id);
        }
        return new StoredResponse(responseStatus, responseBody);
    }

    /** 키를 잡은 요청의 결과 응답을 채운다. 한 번만 채운다. */
    public void complete(StoredResponse response) {
        if (responseStatus != null) {
            throw new IllegalStateException("이미 응답을 저장한 멱등 기록이다: " + id);
        }
        this.responseStatus = response.status();
        this.responseBody = response.body();
    }
}
