package com.example.booking.showtime;

import com.example.booking.movie.Movie;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.time.LocalDateTime;

@Entity
public class Showtime {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne
	@JoinColumn(name = "movie_id", nullable = false)
	private Movie movie;

	private LocalDateTime startTime;

	private String screenLabel;

	protected Showtime() {
	}

	public Showtime(Movie movie, LocalDateTime startTime, String screenLabel) {
		this.movie = movie;
		this.startTime = startTime;
		this.screenLabel = screenLabel;
	}

	public Long getId() {
		return id;
	}

	public Movie getMovie() {
		return movie;
	}

	public LocalDateTime getStartTime() {
		return startTime;
	}

	public String getScreenLabel() {
		return screenLabel;
	}

}
