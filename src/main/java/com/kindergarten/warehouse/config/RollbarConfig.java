package com.kindergarten.warehouse.config;

import com.rollbar.notifier.Rollbar;
import com.rollbar.notifier.config.Config;
import com.rollbar.spring.webmvc.RollbarSpringConfigBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Configuration;

@Configuration()
@ComponentScan({
        "com.rollbar.spring"
})
public class RollbarConfig {

    @Value("${rollbar.access-token}")
    private String accessToken;

    @Value("${rollbar.environment}")
    private String environment;

    @Value("${rollbar.enabled:true}")
    private boolean enabled;

    @Bean
    public Rollbar rollbar() {
        return new Rollbar(getRollbarConfigs(accessToken));
    }

    private Config getRollbarConfigs(String accessToken) {

        // Reference: https://docs.rollbar.com/docs/spring-boot

        return RollbarSpringConfigBuilder.withAccessToken(accessToken)
                .environment(environment)
                .enabled(enabled)
                .build();
    }
}
