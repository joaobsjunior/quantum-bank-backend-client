package com.quantumbank.backendclient.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Operator credentials for the web console. The console drives Pix transfers
 * with the service's own OAuth2 client and mTLS certificate, so it must never
 * be reachable anonymously. Both values come from the container environment and
 * the service refuses to start without them.
 */
@ConfigurationProperties(prefix = "quantum-bank.console")
data class ConsoleProperties(
    val username: String,
    val password: String,
) {
    init {
        require(username.isNotBlank()) {
            "quantum-bank.console.username is required (set QUANTUM_BANK_CONSOLE_USERNAME)"
        }
        require(password.length >= MIN_PASSWORD_LENGTH) {
            "quantum-bank.console.password must have at least $MIN_PASSWORD_LENGTH characters " +
                "(set QUANTUM_BANK_CONSOLE_PASSWORD)"
        }
    }

    companion object {
        const val MIN_PASSWORD_LENGTH = 12
    }
}
