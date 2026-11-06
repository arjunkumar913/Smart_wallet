package com.vardhan.notification.config;

import com.vardhan.common.security.JwtService;
import com.vardhan.common.security.SecurityDefaults;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        SecurityDefaults.apply(http, jwtService)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(SecurityDefaults.PUBLIC_DOC_PATHS).permitAll()
                        .anyRequest().authenticated());
        return http.build();
    }
}
