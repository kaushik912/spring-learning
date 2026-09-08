package com.example.booking.showtime;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ShowtimeController {

	private final ShowtimeService showtimeService;

	public ShowtimeController(ShowtimeService showtimeService) {
		this.showtimeService = showtimeService;
	}

	@GetMapping("/movies/{movieId}/showtimes")
	public List<ShowtimeResponse> getShowtimesForMovie(@PathVariable Long movieId) {
		return showtimeService.listShowtimesForMovie(movieId).stream().map(ShowtimeResponse::from).toList();
	}

}
