package com.martin.genAiAgent.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class MonitoringConfig {
    
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config().commonTags("application", "genAiAgent");
    }
    
    @Bean
    public Counter recommendationCounter(MeterRegistry registry) {
        return Counter.builder("recommendations.total")
                .description("Total number of recommendations generated")
                .register(registry);
    }
    
    @Bean
    public Counter userLoginCounter(MeterRegistry registry) {
        return Counter.builder("user.logins")
                .description("Total number of user logins")
                .register(registry);
    }
    
    @Bean
    public Counter apiRequestCounter(MeterRegistry registry) {
        return Counter.builder("api.requests")
                .description("Total number of API requests")
                .register(registry);
    }
    
    @Bean
    public Timer recommendationTimer(MeterRegistry registry) {
        return Timer.builder("recommendation.generation.time")
                .description("Time taken to generate recommendations")
                .register(registry);
    }
}
