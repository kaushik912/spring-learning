package com.example.booking.booking;

import com.example.booking.movie.Movie;
import com.example.booking.movie.MovieRepository;
import com.example.booking.showtime.Seat;
import com.example.booking.showtime.SeatRepository;
import com.example.booking.showtime.Showtime;
import com.example.booking.showtime.ShowtimeRepository;
import com.example.booking.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BookingConflictControllerTest extends AbstractIntegrationTest {

	@Autowired
	private MovieRepository movieRepository;

	@Autowired
	private ShowtimeRepository showtimeRepository;

	@Autowired
	private SeatRepository seatRepository;

	@Test
	void givenOneSeatAlreadyBooked_whenBookingIncludesIt_thenReturns409AndBooksNothing() {
		// Given
		Movie movie = movieRepository.save(new Movie("Inception", "A mind-bending heist", 148, "Sci-Fi"));
		Showtime showtime = showtimeRepository.save(new Showtime(movie, LocalDateTime.of(2026, 9, 10, 18, 0), "Screen 1"));
		Seat alreadyBooked = seatRepository.save(new Seat(showtime.getId(), "A1"));
		Seat stillAvailable = seatRepository.save(new Seat(showtime.getId(), "A2"));
		restTemplate.postForEntity(url("/showtimes/" + showtime.getId() + "/bookings"),
				new HttpEntity<>(Map.of("seatIds", List.of(alreadyBooked.getId()))), Map.class);

		// When
		HttpEntity<Map<String, Object>> request = new HttpEntity<>(
				Map.of("seatIds", List.of(alreadyBooked.getId(), stillAvailable.getId())));
		ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
				url("/showtimes/" + showtime.getId() + "/bookings"),
				HttpMethod.POST,
				request,
				new ParameterizedTypeReference<>() {
				});

		// Then
		assertThat(response.getStatusCode().value()).isEqualTo(409);

		ResponseEntity<Map<String, Object>> readBack = restTemplate.exchange(
				url("/showtimes/" + showtime.getId()),
				HttpMethod.GET,
				null,
				new ParameterizedTypeReference<>() {
				});
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> seats = (List<Map<String, Object>>) readBack.getBody().get("seats");
		Map<String, Object> stillAvailableSeat = seats.stream()
				.filter(s -> String.valueOf(s.get("id")).equals(String.valueOf(stillAvailable.getId())))
				.findFirst().orElseThrow();
		assertThat(stillAvailableSeat.get("status")).isEqualTo("AVAILABLE");
	}

}
