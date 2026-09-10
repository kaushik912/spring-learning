package com.example.booking.movie;

import com.example.booking.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MovieControllerTest extends AbstractIntegrationTest {

	@Autowired
	private MovieRepository movieRepository;

	@Test
	void givenMoviesExist_whenGetMovies_thenReturnsAllMovies() {
		// Given
		movieRepository.save(new Movie("Inception", "A mind-bending heist", 148, "Sci-Fi"));
		movieRepository.save(new Movie("The Matrix", "A hacker learns the truth", 136, "Sci-Fi"));

		// When
		ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
				url("/movies"),
				org.springframework.http.HttpMethod.GET,
				null,
				new org.springframework.core.ParameterizedTypeReference<>() {
				});

		// Then
		assertThat(response.getStatusCode().value()).isEqualTo(200);
		assertThat(response.getBody())
				.extracting(m -> m.get("title"))
				.containsExactlyInAnyOrder("Inception", "The Matrix");
	}

}
