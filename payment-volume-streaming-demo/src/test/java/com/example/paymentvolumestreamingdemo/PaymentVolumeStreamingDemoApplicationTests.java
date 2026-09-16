package com.example.paymentvolumestreamingdemo;

import com.example.paymentvolumestreamingdemo.kafka.PaymentTopics;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;

@SpringBootTest
@EmbeddedKafka(topics = PaymentTopics.TOPIC)
class PaymentVolumeStreamingDemoApplicationTests {

	@Test
	void contextLoads() {
	}
}
