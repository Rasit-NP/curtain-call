package com.curtaincall.performance.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PerformanceRepository extends JpaRepository<Performance, Long> {

    /** 공연 목록(spec 6.2): 시작 시각 오름차순, 같으면 id 오름차순. 응답에 공연장 이름이 필요해 공연장을 함께 읽는다. */
    @Query("select p from Performance p join fetch p.venue order by p.startsAt asc, p.id asc")
    List<Performance> findAllWithVenueOrderByStartsAt();

    @Query("select p from Performance p join fetch p.venue where p.id = :id")
    Optional<Performance> findWithVenueById(long id);

    /** 여러 공연을 공연장과 함께 읽는다(내 예매, spec 6.8). 순서는 정하지 않는다. */
    @Query("select p from Performance p join fetch p.venue where p.id in :ids")
    List<Performance> findAllWithVenueByIdIn(Collection<Long> ids);
}
