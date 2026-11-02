package com.ticketflow.domain.admission.client;

public interface QueueClient {

    void release(String passId);
}
