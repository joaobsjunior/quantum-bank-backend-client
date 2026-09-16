package com.quantumbank.backendclient.config

import org.springframework.context.annotation.Configuration

/**
 * Guarantees the post-quantum TLS policy is installed before the gateway or
 * token-endpoint clients are built, even when the context starts without `main`.
 */
@Configuration
class PostQuantumTlsConfiguration {
    init {
        PostQuantumTls.install()
    }
}
