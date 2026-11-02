package com.ticketflow.domain.performance.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "zone")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Zone {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "showtime_id")
    private Showtime showtime;

    @Column(name = "name")
    private String name;

    @Column(name = "display_order")
    private int displayOrder;

    public Zone(Showtime showtime, String name, int displayOrder) {
        this.showtime = showtime;
        this.name = name;
        this.displayOrder = displayOrder;
    }
}
