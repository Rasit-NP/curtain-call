package com.curtaincall.hold.domain;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.curtaincall.performance.domain.Seat;

import jakarta.persistence.LockModeType;

public interface HoldRepository extends JpaRepository<Hold, Long> {

    /**
     * 같은 사용자·같은 공연에서 저장 상태가 status인 선점과 그 좌석 목록. 보유 좌석 수(R2)는 ACTIVE 선점을 읽어
     * {@link HoldLimit#heldSeatCount}가 현재 시각으로 유효한 것만 센다(만료는 저장하지 않으므로 만료된 선점도 여기 들어온다).
     */
    @EntityGraph(attributePaths = "seatIds")
    List<Hold> findByUserIdAndPerformanceIdAndStatus(long userId, long performanceId, HoldStatus status);

    /** 선점과 그 좌석 목록(만든 때의 묶음)을 읽는다. 선점 조회(spec 6.5)에 쓰고, 잠그지 않는다. */
    @EntityGraph(attributePaths = "seatIds")
    @Query("select h from Hold h where h.id = :id")
    Optional<Hold> findWithSeatIdsById(long id);

    /**
     * 선점 행을 트랜잭션이 끝날 때까지 잠그고 읽는다(해제, spec 2.4, 결정 0100). PostgreSQL에서는 {@code FOR NO KEY UPDATE}다.
     * 같은 선점의 해제(5단계부터는 결제도)가 이 행에서 한 줄로 서고, 나중 요청은 앞 요청이 커밋한 값(RELEASED 등)을 읽는다.
     *
     * <p>이 트랜잭션에서 같은 선점을 먼저 일반 조회로 읽지 않는다. 영속성 컨텍스트에 남은 이전 값이 그대로 쓰일 수 있다. 좌석 목록은 잠그지
     * 않는다(만든 뒤 바뀌지 않는다).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from Hold h where h.id = :id")
    Optional<Hold> lockById(long id);

    /** 좌석들을 지금 잡은 선점(좌석의 holdId, 결정 0009)을 id로 찾는다. HELD가 아닌 좌석은 holdId가 없어 건너뛴다. */
    default Map<Long, Hold> findHoldersOf(Collection<Seat> seats) {
        Set<Long> holdIds = seats.stream().map(Seat::getHoldId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (holdIds.isEmpty()) {
            return Map.of();
        }
        return findAllById(holdIds).stream().collect(Collectors.toMap(Hold::getId, Function.identity()));
    }
}
