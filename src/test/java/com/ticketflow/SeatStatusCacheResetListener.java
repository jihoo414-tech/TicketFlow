package com.ticketflow;

import com.ticketflow.domain.booking.service.SeatStatusCache;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;

public class SeatStatusCacheResetListener implements TestExecutionListener {

    @Override
    public void beforeTestMethod(TestContext testContext) {
        testContext.getApplicationContext()
                .getBeanProvider(SeatStatusCache.class)
                .ifAvailable(SeatStatusCache::clear);
    }
}
