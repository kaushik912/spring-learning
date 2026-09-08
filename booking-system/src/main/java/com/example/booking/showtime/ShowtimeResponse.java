package com.example.booking.showtime;

import java.time.LocalDateTime;

public record ShowtimeResponse(Long id, Long movieId, LocalDateTime startTime, String screenLabel) {

	static ShowtimeResponse from(Showtime showtime) {
		return new ShowtimeResponse(showtime.getId(), showtime.getMovie().getId(), showtime.getStartTime(),
				showtime.getScreenLabel());
	}

}
