package fr.volkaert.monitoring.traffic_simulator;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Value("${spring.application.name}")
    String myAppName;

    @Value("${spring.application.instance_id}")
    String myAppInstanceId;

    @Bean
    MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config()
                .commonTags("myapp_name", myAppName)
                .commonTags("myapp_instance_id", myAppInstanceId);
    }
}
