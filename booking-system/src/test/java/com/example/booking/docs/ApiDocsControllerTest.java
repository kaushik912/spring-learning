package com.example.booking.docs;

import com.example.booking.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class ApiDocsControllerTest extends AbstractIntegrationTest {

	@Test
	void givenApiDocsWired_whenGetSwaggerUi_thenReachable() {
		ResponseEntity<String> response = restTemplate.getForEntity(url("/swagger-ui.html"), String.class);
		assertThat(response.getStatusCode().is2xxSuccessful() || response.getStatusCode().is3xxRedirection()).isTrue();
	}

	@Test
	void givenApiDocsWired_whenGetOpenApiSpec_thenReachable() {
		ResponseEntity<String> response = restTemplate.getForEntity(url("/v3/api-docs"), String.class);
		assertThat(response.getStatusCode().is2xxSuccessful() || response.getStatusCode().is3xxRedirection()).isTrue();
	}

}
