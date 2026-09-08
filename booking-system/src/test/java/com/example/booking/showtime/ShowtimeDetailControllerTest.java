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

class ShowtimeDetailControllerTest extends AbstractIntegrationTest {

	@Autowired
	private MovieRepository movieRepository;

	@Autowired
	private ShowtimeRepository showtimeRepository;

	@Autowired
	private SeatRepository seatRepository;

	@Test
	void givenShowtimeWithSeats_whenGetShowtime_thenReturnsSeatMapWithStatus() {
		// Given
		Movie movie = movieRepository.save(new Movie("Inception", "A mind-bending heist", 148, "Sci-Fi"));
		Showtime showtime = showtimeRepository.save(new Showtime(movie, LocalDateTime.of(2026, 9, 10, 18, 0), "Screen 1"));
		seatRepository.save(new Seat(showtime.getId(), "A1"));
		seatRepository.save(new Seat(showtime.getId(), "A2"));

		// When
		ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
				url("/showtimes/" + showtime.getId()),
				HttpMethod.GET,
				null,
				new ParameterizedTypeReference<>() {
				});

		// Then
		assertThat(response.getStatusCode().value()).isEqualTo(200);
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> seats = (List<Map<String, Object>>) response.getBody().get("seats");
		assertThat(seats).extracting(s -> s.get("seatLabel")).containsExactlyInAnyOrder("A1", "A2");
		assertThat(seats).allSatisfy(s -> assertThat(s.get("status")).isEqualTo("AVAILABLE"));
	}

}
