package com.example.booking.showtime;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {

	List<Seat> findByShowtimeId(Long showtimeId);

}
