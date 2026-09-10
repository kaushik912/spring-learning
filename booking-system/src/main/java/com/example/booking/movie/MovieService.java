package com.example.booking.movie;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MovieService {

	private final MovieRepository movieRepository;

	public MovieService(MovieRepository movieRepository) {
		this.movieRepository = movieRepository;
	}

	public List<Movie> listMovies() {
		return movieRepository.findAll();
	}

}
