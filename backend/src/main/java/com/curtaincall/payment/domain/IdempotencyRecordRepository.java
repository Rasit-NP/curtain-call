package com.curtaincall.payment.domain;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    /**
     * 같은 사용자·같은 키의 멱등 기록을 잡는다(결제의 잠금 순서에서 사용자 행 뒤, 선점 행 앞, spec 2.4, 결정 0151). 응답이 빈 기록을 넣고,
     * 넣었으면 1, 이미 있으면 0을 돌려준다.
     *
     * <p>같은 (사용자, 키)를 다른 트랜잭션이 넣고 아직 커밋하지 않았으면, 이 INSERT는 유니크 제약에서 그 트랜잭션이 끝날 때까지 기다린다.
     * 그쪽이 커밋하면 0이고(커밋된 기록을 읽어 그 응답을 돌려준다), 되돌려지면(500) 이 요청이 넣어 1이다(처음 온 요청처럼 처리한다).
     */
    @Modifying
    @Query(value = """
            INSERT INTO idempotency_records (user_id, idempotency_key, hold_id, created_at)
            VALUES (:userId, :idempotencyKey, :holdId, :createdAt)
            ON CONFLICT (user_id, idempotency_key) DO NOTHING""", nativeQuery = true)
    int claim(long userId, String idempotencyKey, long holdId, Instant createdAt);

    Optional<IdempotencyRecord> findByUserIdAndIdempotencyKey(long userId, String idempotencyKey);
}
