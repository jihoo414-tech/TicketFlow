package com.ticketflow.domain.booking.entity;

import com.ticketflow.domain.performance.entity.Seat;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "hold_seat")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class HoldSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hold_id")
    private Hold hold;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id")
    private Seat seat;

    public HoldSeat(Hold hold, Seat seat) {
        this.hold = hold;
        this.seat = seat;
    }

    public boolean isOccupying(LocalDateTime now) {
        return hold.isOccupying(now);
    }
}
