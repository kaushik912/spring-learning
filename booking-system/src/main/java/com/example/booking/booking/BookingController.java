package com.example.booking.booking;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Bookings", description = "Book seats on a showtime")
@RestController
public class BookingController {

	private final BookingService bookingService;

	public BookingController(BookingService bookingService) {
		this.bookingService = bookingService;
	}

	@Operation(summary = "Book one or more seats on a showtime (all-or-nothing)")
	@PostMapping("/showtimes/{showtimeId}/bookings")
	@ResponseStatus(HttpStatus.CREATED)
	public BookingResponse bookShowtime(@PathVariable Long showtimeId, @RequestBody BookingRequest request) {
		return bookingService.bookSeats(showtimeId, request.seatIds());
	}

}
