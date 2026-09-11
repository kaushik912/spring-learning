package com.example.consumerlagdemo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;

@SpringBootTest
@EmbeddedKafka(partitions = KafkaTopicsConfig.PARTITIONS)
class ConsumerLagDemoApplicationTests {

	@Test
	void contextLoads() {
	}

}
