package com.example.booking.movie;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Movies", description = "Browse movies")
@RestController
public class MovieController {

	private final MovieService movieService;

	public MovieController(MovieService movieService) {
		this.movieService = movieService;
	}

	@Operation(summary = "List all movies")
	@GetMapping("/movies")
	public List<MovieResponse> getMovies() {
		return movieService.listMovies().stream().map(MovieResponse::from).toList();
	}

}
