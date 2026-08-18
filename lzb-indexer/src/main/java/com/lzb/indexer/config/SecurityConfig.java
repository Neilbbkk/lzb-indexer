package com.lzb.indexer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration:
 * - /actuator/health stays open for container health checks
 * - all other /actuator/** endpoints require HTTP Basic auth
 * - everything else (REST API, WebSocket) stays open
 * Credentials come from spring.security.user.* (env: ACTUATOR_USERNAME / ACTUATOR_PASSWORD).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf().disable()
            .authorizeRequests()
                .antMatchers("/actuator/health").permitAll()
                .antMatchers("/actuator/**").authenticated()
                .anyRequest().permitAll()
            .and()
            .httpBasic();
        return http.build();
    }
}
