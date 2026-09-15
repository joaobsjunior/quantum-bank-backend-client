package com.quantumbank.backendclient.config

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.factory.PasswordEncoderFactories

class ConsoleSecurityConfigTest {

    @Test
    fun `console credentials are mandatory and the password has a minimum length`() {
        assertThatThrownBy { ConsoleProperties(username = "", password = "operator-password-1") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("username is required")

        assertThatThrownBy { ConsoleProperties(username = "operator", password = "short") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("at least ${ConsoleProperties.MIN_PASSWORD_LENGTH}")
    }

    @Test
    fun `operator user is registered with an encoded password`() {
        val properties = ConsoleProperties(username = "operator", password = "operator-password-1")

        val user = ConsoleSecurityConfig().consoleUserDetailsService(properties).loadUserByUsername("operator")

        assertThat(user.password).doesNotContain("operator-password-1")
        assertThat(PasswordEncoderFactories.createDelegatingPasswordEncoder().matches("operator-password-1", user.password)).isTrue()
        assertThat(user.authorities.map { it.authority }).containsExactly("ROLE_OPERATOR")
    }
}
