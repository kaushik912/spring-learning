package com.example.booking.showtime;

import java.time.LocalDateTime;
import java.util.List;

public record ShowtimeDetailResponse(Long id, Long movieId, LocalDateTime startTime, String screenLabel,
		List<SeatResponse> seats) {

	static ShowtimeDetailResponse from(Showtime showtime, List<Seat> seats) {
		return new ShowtimeDetailResponse(showtime.getId(), showtime.getMovie().getId(), showtime.getStartTime(),
				showtime.getScreenLabel(), seats.stream().map(SeatResponse::from).toList());
	}

}
