CREATE TABLE movie (
    id                BIGSERIAL PRIMARY KEY,
    title             VARCHAR(255) NOT NULL,
    description       TEXT,
    duration_minutes  INT NOT NULL,
    genre             VARCHAR(100)
);

CREATE TABLE showtime (
    id            BIGSERIAL PRIMARY KEY,
    movie_id      BIGINT NOT NULL REFERENCES movie (id),
    start_time    TIMESTAMP NOT NULL,
    screen_label  VARCHAR(50) NOT NULL
);

CREATE TABLE booking (
    id           BIGSERIAL PRIMARY KEY,
    showtime_id  BIGINT NOT NULL REFERENCES showtime (id),
    created_at   TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE seat (
    id           BIGSERIAL PRIMARY KEY,
    showtime_id  BIGINT NOT NULL REFERENCES showtime (id),
    seat_label   VARCHAR(10) NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE', 'BOOKED')),
    booking_id   BIGINT REFERENCES booking (id),
    CONSTRAINT uq_seat_showtime_label UNIQUE (showtime_id, seat_label)
);

CREATE INDEX idx_showtime_movie_id ON showtime (movie_id);
CREATE INDEX idx_booking_showtime_id ON booking (showtime_id);
CREATE INDEX idx_seat_showtime_id ON seat (showtime_id);
CREATE INDEX idx_seat_booking_id ON seat (booking_id);
