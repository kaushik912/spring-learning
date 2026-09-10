package com.example.booking.booking;

import java.util.List;

public class SeatUnavailableException extends RuntimeException {

	private final List<Long> unavailableSeatIds;

	public SeatUnavailableException(List<Long> unavailableSeatIds) {
		super("Seats not available: " + unavailableSeatIds);
		this.unavailableSeatIds = unavailableSeatIds;
	}

	public List<Long> getUnavailableSeatIds() {
		return unavailableSeatIds;
	}

}
