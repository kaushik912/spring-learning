package com.example.booking.showtime;

import com.example.booking.movie.Movie;
import com.example.booking.movie.MovieRepository;
import com.example.booking.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ShowtimeControllerTest extends AbstractIntegrationTest {

	@Autowired
	private MovieRepository movieRepository;

	@Autowired
	private ShowtimeRepository showtimeRepository;

	@Test
	void givenShowtimesExist_whenGetShowtimesForMovie_thenReturnsOnlyThatMoviesShowtimes() {
		// Given
		Movie inception = movieRepository.save(new Movie("Inception", "A mind-bending heist", 148, "Sci-Fi"));
		Movie matrix = movieRepository.save(new Movie("The Matrix", "A hacker learns the truth", 136, "Sci-Fi"));
		showtimeRepository.save(new Showtime(inception, LocalDateTime.of(2026, 9, 10, 18, 0), "Screen 1"));
		showtimeRepository.save(new Showtime(inception, LocalDateTime.of(2026, 9, 10, 21, 0), "Screen 2"));
		showtimeRepository.save(new Showtime(matrix, LocalDateTime.of(2026, 9, 10, 19, 0), "Screen 3"));

		// When
		ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
				url("/movies/" + inception.getId() + "/showtimes"),
				HttpMethod.GET,
				null,
				new ParameterizedTypeReference<>() {
				});

		// Then
		assertThat(response.getStatusCode().value()).isEqualTo(200);
		assertThat(response.getBody())
				.extracting(s -> s.get("screenLabel"))
				.containsExactlyInAnyOrder("Screen 1", "Screen 2");
	}

}
