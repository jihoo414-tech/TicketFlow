package com.ticketflow;

import com.ticketflow.domain.admission.client.QueueClient;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class FakeQueueClient implements QueueClient {

    private final List<String> releasedPassIds = new CopyOnWriteArrayList<>();
    private volatile boolean failing;

    @Override
    public void release(String passId) {
        if (failing) {
            throw new IllegalStateException("대기열 서버에 연결할 수 없다");
        }
        releasedPassIds.add(passId);
    }

    public List<String> releasedPassIds() {
        return List.copyOf(releasedPassIds);
    }

    public void failNextReleases() {
        failing = true;
    }

    public void reset() {
        releasedPassIds.clear();
        failing = false;
    }
}
