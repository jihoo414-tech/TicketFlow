package com.ticketflow;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class TicketflowApplicationTests {

	@Test
	void MYSQL_과_함께_테스트가_실행된다() {
	}

}
