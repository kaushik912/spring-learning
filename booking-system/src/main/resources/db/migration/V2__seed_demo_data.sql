INSERT INTO movie (id, title, description, duration_minutes, genre) VALUES
    (1, 'Inception', 'A thief who steals corporate secrets through dream-sharing technology.', 148, 'Sci-Fi'),
    (2, 'The Matrix', 'A hacker learns the truth about his reality and his role in the war against its controllers.', 136, 'Sci-Fi'),
    (3, 'Interstellar', 'A team of explorers travel through a wormhole in space in an attempt to save humanity.', 169, 'Sci-Fi');

SELECT setval('movie_id_seq', (SELECT max(id) FROM movie));

INSERT INTO showtime (id, movie_id, start_time, screen_label) VALUES
    (1, 1, '2026-09-10 18:00:00', 'Screen 1'),
    (2, 1, '2026-09-10 21:00:00', 'Screen 1'),
    (3, 2, '2026-09-10 19:00:00', 'Screen 2'),
    (4, 2, '2026-09-11 19:00:00', 'Screen 2'),
    (5, 3, '2026-09-10 20:00:00', 'Screen 3'),
    (6, 3, '2026-09-11 20:00:00', 'Screen 3');

SELECT setval('showtime_id_seq', (SELECT max(id) FROM showtime));

-- Two rows (A, B) of five seats (1-5) per showtime.
INSERT INTO seat (showtime_id, seat_label, status)
SELECT s.id, chr(64 + r) || n, 'AVAILABLE'
FROM showtime s
CROSS JOIN generate_series(1, 2) AS r
CROSS JOIN generate_series(1, 5) AS n;
