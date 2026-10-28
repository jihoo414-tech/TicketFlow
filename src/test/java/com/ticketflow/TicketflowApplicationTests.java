package com.ticketflow;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

// MockMvc를 쓰지 않지만 기능 테스트와 애너테이션 조합을 맞춰야 같은 컨텍스트를 재사용한다. 빼면 컨텍스트가 하나 더 뜬다.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class TicketflowApplicationTests {

	@Test
	void MYSQL_과_함께_테스트가_실행된다() {
	}

}
