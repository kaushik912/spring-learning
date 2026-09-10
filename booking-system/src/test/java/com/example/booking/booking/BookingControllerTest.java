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

class BookingControllerTest extends AbstractIntegrationTest {

	@Autowired
	private MovieRepository movieRepository;

	@Autowired
	private ShowtimeRepository showtimeRepository;

	@Autowired
	private SeatRepository seatRepository;

	@Test
	void givenAvailableSeats_whenBookShowtime_thenReturns201AndReadBackShowsSeatsBooked() {
		// Given
		Movie movie = movieRepository.save(new Movie("Inception", "A mind-bending heist", 148, "Sci-Fi"));
		Showtime showtime = showtimeRepository.save(new Showtime(movie, LocalDateTime.of(2026, 9, 10, 18, 0), "Screen 1"));
		Seat seatA1 = seatRepository.save(new Seat(showtime.getId(), "A1"));
		Seat seatA2 = seatRepository.save(new Seat(showtime.getId(), "A2"));

		// When
		HttpEntity<Map<String, Object>> request = new HttpEntity<>(Map.of("seatIds", List.of(seatA1.getId(), seatA2.getId())));
		ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
				url("/showtimes/" + showtime.getId() + "/bookings"),
				HttpMethod.POST,
				request,
				new ParameterizedTypeReference<>() {
				});

		// Then
		assertThat(response.getStatusCode().value()).isEqualTo(201);
		assertThat(response.getBody().get("id")).isNotNull();

		ResponseEntity<Map<String, Object>> readBack = restTemplate.exchange(
				url("/showtimes/" + showtime.getId()),
				HttpMethod.GET,
				null,
				new ParameterizedTypeReference<>() {
				});
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> seats = (List<Map<String, Object>>) readBack.getBody().get("seats");
		assertThat(seats).allSatisfy(s -> assertThat(s.get("status")).isEqualTo("BOOKED"));
	}

}
