package com.example.booking.showtime;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ShowtimeService {

	private final ShowtimeRepository showtimeRepository;

	public ShowtimeService(ShowtimeRepository showtimeRepository) {
		this.showtimeRepository = showtimeRepository;
	}

	public List<Showtime> listShowtimesForMovie(Long movieId) {
		return showtimeRepository.findByMovieId(movieId);
	}

}
