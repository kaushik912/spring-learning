package com.example.booking.support;

import com.example.booking.TestcontainersConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "spring.flyway.target=1")
@AutoConfigureTestRestTemplate
public abstract class AbstractIntegrationTest {

	@Autowired
	protected TestRestTemplate restTemplate;

	@LocalServerPort
	private int port;

	protected String url(String path) {
		return "http://localhost:" + port + path;
	}

}
