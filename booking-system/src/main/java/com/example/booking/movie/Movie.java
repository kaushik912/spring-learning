package com.example.booking.movie;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Movie {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String title;

	private String description;

	private int durationMinutes;

	private String genre;

	protected Movie() {
	}

	public Movie(String title, String description, int durationMinutes, String genre) {
		this.title = title;
		this.description = description;
		this.durationMinutes = durationMinutes;
		this.genre = genre;
	}

	public Long getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public int getDurationMinutes() {
		return durationMinutes;
	}

	public String getGenre() {
		return genre;
	}

}
