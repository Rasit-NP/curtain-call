package com.curtaincall.hold.domain;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.curtaincall.performance.domain.Seat;

public interface HoldRepository extends JpaRepository<Hold, Long> {

    /**
     * 같은 사용자·같은 공연에서 저장 상태가 status인 선점과 그 좌석 목록. 보유 좌석 수(R2)는 ACTIVE 선점을 읽어
     * {@link HoldLimit#heldSeatCount}가 현재 시각으로 유효한 것만 센다(만료는 저장하지 않으므로 만료된 선점도 여기 들어온다).
     */
    @EntityGraph(attributePaths = "seatIds")
    List<Hold> findByUserIdAndPerformanceIdAndStatus(long userId, long performanceId, HoldStatus status);

    /** 좌석들을 지금 잡은 선점(좌석의 holdId, 결정 0009)을 id로 찾는다. HELD가 아닌 좌석은 holdId가 없어 건너뛴다. */
    default Map<Long, Hold> findHoldersOf(Collection<Seat> seats) {
        Set<Long> holdIds = seats.stream().map(Seat::getHoldId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (holdIds.isEmpty()) {
            return Map.of();
        }
        return findAllById(holdIds).stream().collect(Collectors.toMap(Hold::getId, Function.identity()));
    }
}
