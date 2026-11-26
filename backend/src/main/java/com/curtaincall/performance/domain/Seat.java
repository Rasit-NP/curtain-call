package com.curtaincall.performance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 공연 좌석(spec 2.1). 공연마다 있고, 공연을 만들 때 공연장 배치({@link VenueSeat})의 값을 복사해 가진다(결정 0048).
 *
 * <ul>
 *   <li>{@code row}·{@code number}: 표시 이름({@code A-3}). 같은 공연에서 고유하다.</li>
 *   <li>{@code gridRow}·{@code gridColumn}: 배치 격자의 줄·칸(1부터). 같은 공연에서 겹치지 않는다. 좌석이 없는 칸이 통로나 빈자리다.</li>
 *   <li>{@code status}: 저장 상태. 선점(holdId)은 선점 Issue에서 더한다.</li>
 * </ul>
 */
@Entity
@Table(name = "seats")
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "performance_id", nullable = false)
    private Performance performance;

    @Column(name = "seat_row", nullable = false, length = 10)
    private String row;

    @Column(name = "seat_number", nullable = false)
    private int number;

    @Column(name = "grid_row", nullable = false)
    private int gridRow;

    @Column(name = "grid_column", nullable = false)
    private int gridColumn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeatStatus status;

    protected Seat() {
    }

    public Long getId() {
        return id;
    }

    public Performance getPerformance() {
        return performance;
    }

    public String getRow() {
        return row;
    }

    public int getNumber() {
        return number;
    }

    public int getGridRow() {
        return gridRow;
    }

    public int getGridColumn() {
        return gridColumn;
    }

    public SeatStatus getStatus() {
        return status;
    }
}
