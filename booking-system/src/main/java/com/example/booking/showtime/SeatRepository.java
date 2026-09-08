package com.example.booking.showtime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {

	List<Seat> findByShowtimeId(Long showtimeId);

	/**
	 * Atomic compare-and-swap: only flips a seat to BOOKED if it is still
	 * AVAILABLE. The UPDATE takes the DB row lock, so a concurrent booking
	 * of the same seat blocks until this transaction commits, then updates
	 * 0 rows. Returns the number of rows updated (0 = conflict).
	 */
	@Modifying
	@Query("UPDATE Seat s SET s.status = com.example.booking.showtime.SeatStatus.BOOKED, s.bookingId = :bookingId "
			+ "WHERE s.id = :seatId AND s.showtimeId = :showtimeId AND s.status = com.example.booking.showtime.SeatStatus.AVAILABLE")
	int markBooked(@Param("seatId") Long seatId, @Param("showtimeId") Long showtimeId, @Param("bookingId") Long bookingId);

}
