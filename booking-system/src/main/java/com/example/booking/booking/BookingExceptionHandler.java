package com.example.booking.booking;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class BookingExceptionHandler {

	@ExceptionHandler(SeatUnavailableException.class)
	public ResponseEntity<Map<String, Object>> handleSeatUnavailable(SeatUnavailableException exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(Map.of("message", exception.getMessage(), "unavailableSeatIds", exception.getUnavailableSeatIds()));
	}

}
