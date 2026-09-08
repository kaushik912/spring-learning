package com.example.booking.booking;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BookingController {

	private final BookingService bookingService;

	public BookingController(BookingService bookingService) {
		this.bookingService = bookingService;
	}

	@PostMapping("/showtimes/{showtimeId}/bookings")
	@ResponseStatus(HttpStatus.CREATED)
	public BookingResponse bookShowtime(@PathVariable Long showtimeId, @RequestBody BookingRequest request) {
		return bookingService.bookSeats(showtimeId, request.seatIds());
	}

}
