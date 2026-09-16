package com.example.duplicatedeliverydemo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;

@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {EventTopics.BUGGY_EVENTS, EventTopics.FIXED_EVENTS, EventTopics.PERSISTENT_EVENTS})
class DuplicateDeliveryDemoApplicationTests {

	@Test
	void contextLoads() {
	}

}
