package com.ticketflow.domain.booking.entity;

public enum HoldStatus {
    //선점됨
    HELD,
    //선점 취소
    CANCELLED,
    //예매됨
    RESERVED,
    //예매 취소
    RESERVATION_CANCELLED
}
