package com.option.global.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.option.global.apiPayLoad.error.AuthErrorCode
import com.option.global.security.jwt.JwtAuthenticationFilter
import com.option.global.security.jwt.JwtTokenProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
class SecurityConfig(
    private val jwtTokenProvider: JwtTokenProvider,
    private val objectMapper: ObjectMapper,
) {
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        val jwtFilter = JwtAuthenticationFilter(jwtTokenProvider, objectMapper)
        http
            .csrf(AbstractHttpConfigurer<*, *>::disable)
            .formLogin(AbstractHttpConfigurer<*, *>::disable)
            .httpBasic(AbstractHttpConfigurer<*, *>::disable)
            .logout(AbstractHttpConfigurer<*, *>::disable)
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it
                    .requestMatchers(*JwtAuthenticationFilter.PUBLIC_PATHS).permitAll()
                    .requestMatchers("/actuator/**", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                    .anyRequest().authenticated()
            }
            .exceptionHandling {
                it.authenticationEntryPoint { _, response, _ -> jwtFilter.writeError(response, AuthErrorCode.UNAUTHORIZED) }
            }
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }
}
