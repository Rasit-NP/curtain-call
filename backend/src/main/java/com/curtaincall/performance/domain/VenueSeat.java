package com.curtaincall.performance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 공연장 좌석 배치의 좌석 하나(spec 2.1). 공연을 만들 때 이 배치를 공연 좌석({@link Seat})으로 복사한다(결정 0048).
 * 같은 공연장에서 (row, number)와 (gridRow, gridColumn)은 겹치지 않는다(DB 제약).
 */
@Entity
@Table(name = "venue_seats")
public class VenueSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @Column(name = "seat_row", nullable = false, length = 10)
    private String row;

    @Column(name = "seat_number", nullable = false)
    private int number;

    @Column(name = "grid_row", nullable = false)
    private int gridRow;

    @Column(name = "grid_column", nullable = false)
    private int gridColumn;

    protected VenueSeat() {
    }

    public VenueSeat(Venue venue, String row, int number, int gridRow, int gridColumn) {
        this.venue = venue;
        this.row = row;
        this.number = number;
        this.gridRow = gridRow;
        this.gridColumn = gridColumn;
    }

    public Long getId() {
        return id;
    }

    public Venue getVenue() {
        return venue;
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
}
