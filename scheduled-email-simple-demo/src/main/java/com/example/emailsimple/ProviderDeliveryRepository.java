package com.example.emailsimple;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProviderDeliveryRepository extends JpaRepository<ProviderDelivery, String> {

    long countByIdempotencyKeyStartingWith(String prefix);

    @Query("select count(distinct d.userId) from ProviderDelivery d where d.idempotencyKey like concat(:prefix, '%')")
    long countDistinctUsersByKeyPrefix(@Param("prefix") String prefix);
}
