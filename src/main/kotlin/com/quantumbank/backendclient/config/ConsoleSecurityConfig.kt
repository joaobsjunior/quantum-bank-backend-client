package com.quantumbank.backendclient.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain

/**
 * Authentication and CSRF protection for the operator console.
 *
 * Every page requires a logged-in operator (form login with the credentials
 * from [ConsoleProperties]); every state-changing POST carries the CSRF token
 * Thymeleaf injects into `th:action` forms, so a page on another origin cannot
 * trigger a Pix transfer through an operator's browser session.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(ConsoleProperties::class)
class ConsoleSecurityConfig {

    @Bean
    fun consoleSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { authorize ->
                authorize
                    .requestMatchers("/error").permitAll()
                    .anyRequest().authenticated()
            }
            .formLogin(Customizer.withDefaults())
            .logout(Customizer.withDefaults())
            .headers { headers ->
                headers.contentSecurityPolicy { csp ->
                    csp.policyDirectives("default-src 'self'; frame-ancestors 'none'; form-action 'self'")
                }
            }
        return http.build()
    }

    @Bean
    fun consoleUserDetailsService(properties: ConsoleProperties): UserDetailsService {
        val encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()
        val operator = User.withUsername(properties.username)
            .password(encoder.encode(properties.password))
            .roles("OPERATOR")
            .build()
        return InMemoryUserDetailsManager(operator)
    }
}
