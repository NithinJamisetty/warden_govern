package com.swms.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthFilter jwtAuthFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/index.html", "/login.html", "/warden/login.html", "/dashboard.html", "/students.html", "/reports.html", "/dashboard", "/warden-manual.html", "/student-guidelines.html", "/error").permitAll()
                .requestMatchers("/admin/login.html", "/admin/dashboard.html", "/admin/wardens.html", "/admin/logs.html").permitAll()
                .requestMatchers("/css/**", "/js/**", "/assets/**", "/mfa_code.txt", "/manifest.json", "/sw.js", "/favicon.ico").permitAll()
                .requestMatchers("/api/auth/login", "/api/auth/mfa/verify", "/api/auth/mfa/setup", "/api/auth/register-warden", "/api/auth/mfa/test-code", "/api/auth/public/wardens", "/api/concerns").permitAll()
                .requestMatchers("/h2-console/**").permitAll()
                .requestMatchers("/admin/**").hasAnyRole("SUPER_ADMIN", "DISTRICT_ADMIN")
                .requestMatchers("/api/admin/**").hasAnyRole("SUPER_ADMIN", "DISTRICT_ADMIN")
                .anyRequest().authenticated()
            );

        http.headers(headers -> headers
            .frameOptions(frame -> frame.sameOrigin())
            .cacheControl(cache -> cache.disable())
        );
        http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }
}
