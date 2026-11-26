package com.curtaincall.performance.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** 공연(spec 2.1). 날짜와 시간이 정해진 한 회차다. {@code price}는 1석 가격(원)이고 전석이 같다. */
@Entity
@Table(name = "performances")
public class Performance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(nullable = false)
    private int price;

    protected Performance() {
    }

    public Performance(String title, Venue venue, Instant startsAt, int price) {
        this.title = title;
        this.venue = venue;
        this.startsAt = startsAt;
        this.price = price;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public Venue getVenue() {
        return venue;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public int getPrice() {
        return price;
    }
}
