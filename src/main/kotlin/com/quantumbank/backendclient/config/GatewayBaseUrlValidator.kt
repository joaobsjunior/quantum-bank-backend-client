package com.quantumbank.backendclient.config

import com.quantumbank.backendclient.integration.ExternalIntegrationException
import java.net.URI

/**
 * Guards the gateway-only rule: the configured base URL must point at the
 * gateway, never directly at a backend service host. A misconfiguration fails
 * fast at startup instead of silently bypassing the gateway.
 */
object GatewayBaseUrlValidator {

    fun validate(gatewayBaseUrl: String, forbiddenDirectHosts: List<String>): String {
        val host = try {
            URI(gatewayBaseUrl).host
        } catch (ex: Exception) {
            throw ExternalIntegrationException("invalid gateway base URL '$gatewayBaseUrl': ${ex.message}")
        }
        if (host.isNullOrBlank()) {
            throw ExternalIntegrationException("gateway base URL '$gatewayBaseUrl' has no host")
        }
        if (forbiddenDirectHosts.any { it.equals(host, ignoreCase = true) }) {
            throw ExternalIntegrationException(
                "gateway base URL host '$host' resolves to a backend service; calls must go through the gateway",
            )
        }
        return gatewayBaseUrl
    }
}
