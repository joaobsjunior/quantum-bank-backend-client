package com.quantumbank.backendclient.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Runtime configuration for the external-service integration.
 *
 * Every value is supplied by container environment (see the Docker Compose
 * service) — no secret is hard-coded. The `backend-client` reaches Quantum Bank
 * only through the gateway, authenticates with its own client-credentials
 * client, and presents a PKI-issued service certificate for mTLS.
 */
@ConfigurationProperties(prefix = "quantum-bank.client")
data class BackendClientProperties(
    /** Gateway banking-listener origin. All banking calls target this base URL. */
    val gatewayBaseUrl: String,
    /** OAuth2 token endpoint for the client-credentials grant. */
    val tokenUri: String,
    /** Confidential client id registered in the local IdP. */
    val clientId: String,
    /** Confidential client secret (from container env, never hard-coded). */
    val clientSecret: String,
    /** Space-delimited scopes requested for the service token. */
    val scope: String,
    /** PKCS12 keystore holding the service client certificate + key. */
    val keyStore: String,
    val keyStorePassword: String,
    /** PKCS12 truststore holding the CA anchors used to trust the gateway. */
    val trustStore: String,
    val trustStorePassword: String,
    /**
     * Host names that identify a backend service directly. If the configured
     * gateway base URL resolves to one of these, the configuration is rejected
     * so the service can never bypass the gateway.
     */
    val forbiddenDirectHosts: List<String> = listOf("backend"),
)
