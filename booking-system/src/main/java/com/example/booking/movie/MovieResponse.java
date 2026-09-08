package com.example.booking.movie;

public record MovieResponse(Long id, String title, String description, int durationMinutes, String genre) {

	static MovieResponse from(Movie movie) {
		return new MovieResponse(movie.getId(), movie.getTitle(), movie.getDescription(),
				movie.getDurationMinutes(), movie.getGenre());
	}

}
