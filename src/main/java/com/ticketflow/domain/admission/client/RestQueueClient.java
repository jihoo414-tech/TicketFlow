package com.ticketflow.domain.admission.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RestQueueClient implements QueueClient {

    private static final String INTERNAL_SECRET = "X-Internal-Secret";

    private final RestClient restClient;
    private final String internalSecret;

    public RestQueueClient(@Value("${queue-server.url}") String queueServerUrl,
                           @Value("${queue.internal-secret}") String internalSecret) {
        this.restClient = RestClient.create(queueServerUrl);
        this.internalSecret = internalSecret;
    }

    @Override
    public void release(String passId) {
        restClient.post()
                .uri("/internal/entry-passes/{passId}/release", passId)
                .header(INTERNAL_SECRET, internalSecret)
                .retrieve()
                .toBodilessEntity();
    }
}
