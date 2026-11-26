package com.curtaincall.performance.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.curtaincall.common.api.ApiException;
import com.curtaincall.common.api.ErrorCode;
import com.curtaincall.performance.domain.Performance;
import com.curtaincall.performance.domain.PerformanceRepository;
import com.curtaincall.performance.domain.SeatRepository;

/**
 * 공연 조회(spec 6.2, 6.3). 돌려주는 엔티티는 응답에 필요한 값(공연장 포함)을 모두 읽어 둔 상태다(open-in-view를 끔).
 *
 * <p>좌석 status는 저장 상태를 그대로 쓴다. 선점이 생기면 만료된 선점의 좌석을 AVAILABLE로 보는 API 상태(spec 2.2)로
 * 바꿔야 한다(선점 생성 Issue).
 */
@Service
@Transactional(readOnly = true)
public class PerformanceQueryService {

    private final PerformanceRepository performanceRepository;
    private final SeatRepository seatRepository;

    public PerformanceQueryService(PerformanceRepository performanceRepository, SeatRepository seatRepository) {
        this.performanceRepository = performanceRepository;
        this.seatRepository = seatRepository;
    }

    /** 시작 시각 오름차순, 같으면 id 오름차순. 없으면 빈 목록. */
    public List<Performance> findAll() {
        return performanceRepository.findAllWithVenueOrderByStartsAt();
    }

    /** 공연이 없으면 404 NOT_FOUND. */
    public PerformanceDetail getDetail(long performanceId) {
        Performance performance = performanceRepository.findWithVenueById(performanceId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "공연을 찾을 수 없습니다."));
        return new PerformanceDetail(performance, seatRepository.findByPerformanceIdOrderByRowAndNumber(performanceId));
    }
}
