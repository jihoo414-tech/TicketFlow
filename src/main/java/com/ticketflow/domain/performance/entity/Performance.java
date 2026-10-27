package com.ticketflow.domain.performance.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "performance")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Performance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title")
    private String title;

    public Performance(String title) {
        this.title = title;
    }
}
