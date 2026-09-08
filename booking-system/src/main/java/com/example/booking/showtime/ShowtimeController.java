package com.example.booking.showtime;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Showtimes", description = "Browse showtimes and seat maps")
@RestController
public class ShowtimeController {

	private final ShowtimeService showtimeService;

	public ShowtimeController(ShowtimeService showtimeService) {
		this.showtimeService = showtimeService;
	}

	@Operation(summary = "List showtimes for a movie")
	@GetMapping("/movies/{movieId}/showtimes")
	public List<ShowtimeResponse> getShowtimesForMovie(@PathVariable Long movieId) {
		return showtimeService.listShowtimesForMovie(movieId).stream().map(ShowtimeResponse::from).toList();
	}

	@Operation(summary = "Get a showtime's seat map")
	@GetMapping("/showtimes/{showtimeId}")
	public ShowtimeDetailResponse getShowtime(@PathVariable Long showtimeId) {
		return showtimeService.getShowtimeDetail(showtimeId);
	}

}
