package com.example.paymentvolumestreamingdemo;

import com.example.paymentvolumestreamingdemo.config.PaymentsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(PaymentsProperties.class)
public class PaymentVolumeStreamingDemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(PaymentVolumeStreamingDemoApplication.class, args);
	}
}
