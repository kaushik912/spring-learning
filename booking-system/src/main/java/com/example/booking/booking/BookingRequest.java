package com.example.booking.booking;

import java.util.List;

public record BookingRequest(List<Long> seatIds) {
}
