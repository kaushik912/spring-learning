package com.example.booking.showtime;

public record SeatResponse(Long id, String seatLabel, SeatStatus status) {

	public static SeatResponse from(Seat seat) {
		return new SeatResponse(seat.getId(), seat.getSeatLabel(), seat.getStatus());
	}

}
