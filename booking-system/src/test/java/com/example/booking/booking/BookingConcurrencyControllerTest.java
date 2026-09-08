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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class BookingConcurrencyControllerTest extends AbstractIntegrationTest {

	@Autowired
	private MovieRepository movieRepository;

	@Autowired
	private ShowtimeRepository showtimeRepository;

	@Autowired
	private SeatRepository seatRepository;

	@Test
	void givenTwoConcurrentBookings_whenSameSeatRequested_thenExactlyOneSucceeds() throws Exception {
		// Given
		Movie movie = movieRepository.save(new Movie("Inception", "A mind-bending heist", 148, "Sci-Fi"));
		Showtime showtime = showtimeRepository.save(new Showtime(movie, LocalDateTime.of(2026, 9, 10, 18, 0), "Screen 1"));
		Seat seat = seatRepository.save(new Seat(showtime.getId(), "A1"));

		CountDownLatch startLatch = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);

		// When
		List<Future<Integer>> futures = List.of(
				executor.submit(() -> bookSeat(showtime.getId(), seat.getId(), startLatch)),
				executor.submit(() -> bookSeat(showtime.getId(), seat.getId(), startLatch)));
		startLatch.countDown();

		List<Integer> statuses = futures.stream().map(f -> {
			try {
				return f.get();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}).toList();
		executor.shutdown();

		// Then
		assertThat(statuses).containsExactlyInAnyOrder(201, 409);

		ResponseEntity<Map<String, Object>> readBack = restTemplate.exchange(
				url("/showtimes/" + showtime.getId()),
				HttpMethod.GET,
				null,
				new ParameterizedTypeReference<>() {
				});
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> seats = (List<Map<String, Object>>) readBack.getBody().get("seats");
		Map<String, Object> bookedSeat = seats.get(0);
		assertThat(bookedSeat.get("status")).isEqualTo("BOOKED");
		assertThat(bookedSeat.get("bookingId")).isNotNull();
	}

	private int bookSeat(Long showtimeId, Long seatId, CountDownLatch startLatch) throws InterruptedException {
		startLatch.await();
		HttpEntity<Map<String, Object>> request = new HttpEntity<>(Map.of("seatIds", List.of(seatId)));
		ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
				url("/showtimes/" + showtimeId + "/bookings"),
				HttpMethod.POST,
				request,
				new ParameterizedTypeReference<>() {
				});
		return response.getStatusCode().value();
	}

}
