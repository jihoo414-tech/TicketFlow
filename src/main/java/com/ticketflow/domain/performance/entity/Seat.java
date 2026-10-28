package com.ticketflow.domain.performance.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "seat")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Seat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "showtime_id")
    private Showtime showtime;

    @Column(name = "seat_row")
    private String seatRow;

    @Column(name = "seat_no")
    private int seatNo;

    public Seat(Showtime showtime, String seatRow, int seatNo) {
        this.showtime = showtime;
        this.seatRow = seatRow;
        this.seatNo = seatNo;
    }
}
