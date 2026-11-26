package com.curtaincall.performance.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.curtaincall.performance.application.PerformanceQueryService;

/** 공연 목록·상세(spec 6.2, 6.3). 공개 경로다(GET /api/performances/**, 보안 설정은 SecurityConfig). */
@RestController
@RequestMapping("/api/performances")
public class PerformanceController {

    private final PerformanceQueryService queryService;

    public PerformanceController(PerformanceQueryService queryService) {
        this.queryService = queryService;
    }

    /** spec 6.2. 공연이 없으면 []. */
    @GetMapping
    public List<PerformanceSummaryResponse> list() {
        return queryService.findAll().stream().map(PerformanceSummaryResponse::from).toList();
    }

    /**
     * spec 6.3. 오류 순서: 400 INVALID_INPUT(performanceId가 정수가 아님, long으로 해석할 수 없음) → 404 NOT_FOUND(공연 없음).
     * 400은 경로 값 변환 실패를 공통 처리기가 바꾼다(spec 3.1, 결정 0012).
     */
    @GetMapping("/{performanceId}")
    public PerformanceDetailResponse detail(@PathVariable long performanceId) {
        return PerformanceDetailResponse.from(queryService.getDetail(performanceId));
    }
}
