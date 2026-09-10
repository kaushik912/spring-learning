package com.example.booking.booking;

import com.example.booking.showtime.Seat;
import com.example.booking.showtime.SeatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class BookingService {

	private final BookingRepository bookingRepository;

	private final SeatRepository seatRepository;

	public BookingService(BookingRepository bookingRepository, SeatRepository seatRepository) {
		this.bookingRepository = bookingRepository;
		this.seatRepository = seatRepository;
	}

	@Transactional
	public BookingResponse bookSeats(Long showtimeId, List<Long> seatIds) {
		Booking booking = bookingRepository.save(new Booking(showtimeId));

		List<Long> unavailable = new ArrayList<>();
		for (Long seatId : seatIds) {
			int updated = seatRepository.markBooked(seatId, showtimeId, booking.getId());
			if (updated == 0) {
				unavailable.add(seatId);
			}
		}
		if (!unavailable.isEmpty()) {
			throw new SeatUnavailableException(unavailable);
		}

		List<Seat> bookedSeats = seatRepository.findAllById(seatIds);
		return BookingResponse.from(booking, bookedSeats);
	}

}
