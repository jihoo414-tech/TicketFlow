package com.ticketflow;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	MySQLContainer mysqlContainer() {
		return new MySQLContainer(DockerImageName.parse("mysql:8.4"));
	}

	@Bean
	@Primary
	TestClock testClock() {
		return new TestClock();
	}

	@Bean
	@Primary
	TestTicker testTicker() {
		return new TestTicker();
	}

	@Bean
	@Primary
	FakeQueueClient fakeQueueClient() {
		return new FakeQueueClient();
	}

	@Bean
	@Primary
	TaskExecutor syncTaskExecutor() {
		return new SyncTaskExecutor();
	}

}
