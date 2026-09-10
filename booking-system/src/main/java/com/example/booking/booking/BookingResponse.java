package com.example.booking.booking;

import com.example.booking.showtime.Seat;
import com.example.booking.showtime.SeatResponse;

import java.time.LocalDateTime;
import java.util.List;

public record BookingResponse(Long id, Long showtimeId, LocalDateTime createdAt, List<SeatResponse> seats) {

	static BookingResponse from(Booking booking, List<Seat> seats) {
		return new BookingResponse(booking.getId(), booking.getShowtimeId(), booking.getCreatedAt(),
				seats.stream().map(SeatResponse::from).toList());
	}

}
