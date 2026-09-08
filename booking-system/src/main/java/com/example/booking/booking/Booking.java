package com.example.booking.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.time.LocalDateTime;

@Entity
public class Booking {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "showtime_id", nullable = false)
	private Long showtimeId;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	protected Booking() {
	}

	public Booking(Long showtimeId) {
		this.showtimeId = showtimeId;
		this.createdAt = LocalDateTime.now();
	}

	public Long getId() {
		return id;
	}

	public Long getShowtimeId() {
		return showtimeId;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

}
