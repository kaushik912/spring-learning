package com.example.booking.showtime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Seat {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "showtime_id", nullable = false)
	private Long showtimeId;

	private String seatLabel;

	@Enumerated(EnumType.STRING)
	private SeatStatus status;

	@Column(name = "booking_id")
	private Long bookingId;

	protected Seat() {
	}

	public Seat(Long showtimeId, String seatLabel) {
		this.showtimeId = showtimeId;
		this.seatLabel = seatLabel;
		this.status = SeatStatus.AVAILABLE;
	}

	public Long getId() {
		return id;
	}

	public Long getShowtimeId() {
		return showtimeId;
	}

	public String getSeatLabel() {
		return seatLabel;
	}

	public SeatStatus getStatus() {
		return status;
	}

	public Long getBookingId() {
		return bookingId;
	}

}
