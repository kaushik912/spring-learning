package com.example.emailsimple;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderCallRepository extends JpaRepository<ProviderCall, Long> {

    long countByOutcome(ProviderCall.Outcome outcome);

    long countByOutcomeAndIdempotencyKeyStartingWith(ProviderCall.Outcome outcome, String keyPrefix);
}
