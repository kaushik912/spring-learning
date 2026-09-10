package com.example.booking.showtime;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ShowtimeService {

	private final ShowtimeRepository showtimeRepository;

	private final SeatRepository seatRepository;

	public ShowtimeService(ShowtimeRepository showtimeRepository, SeatRepository seatRepository) {
		this.showtimeRepository = showtimeRepository;
		this.seatRepository = seatRepository;
	}

	public List<Showtime> listShowtimesForMovie(Long movieId) {
		return showtimeRepository.findByMovieId(movieId);
	}

	public ShowtimeDetailResponse getShowtimeDetail(Long showtimeId) {
		Showtime showtime = showtimeRepository.findById(showtimeId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Showtime not found"));
		List<Seat> seats = seatRepository.findByShowtimeId(showtimeId);
		return ShowtimeDetailResponse.from(showtime, seats);
	}

}
