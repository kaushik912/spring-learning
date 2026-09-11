package com.example.requestlatencytracingdemo.config;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registering this aspect is what makes @Observed on a method actually do
 * something: each annotated call becomes one Observation, which - because
 * both a MeterRegistry (Prometheus) and a Tracer (Zipkin) are on the
 * classpath - turns into both a Micrometer Timer AND a trace span,
 * automatically correlated by trace id. One annotation, two views of the
 * same call.
 */
@Configuration
public class ObservationConfig {

    @Bean
    public ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
        return new ObservedAspect(observationRegistry);
    }
}
