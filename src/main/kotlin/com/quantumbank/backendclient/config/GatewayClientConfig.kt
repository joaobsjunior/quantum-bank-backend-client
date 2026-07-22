package com.quantumbank.backendclient.config

import tools.jackson.databind.ObjectMapper
import com.quantumbank.backendclient.integration.ClientCredentialsTokenProvider
import com.quantumbank.backendclient.integration.CorrelationIdGenerator
import com.quantumbank.backendclient.integration.GatewayClient
import com.quantumbank.backendclient.integration.TokenProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient

/**
 * Wires the gateway-fronted integration: an mTLS RestClient whose base URL is
 * validated to be the gateway (never a backend), a client-credentials token
 * provider, and the [GatewayClient] itself.
 */
@Configuration
class GatewayClientConfig {

    @Bean
    fun gatewayRestClient(properties: BackendClientProperties): RestClient {
        val baseUrl = GatewayBaseUrlValidator.validate(
            properties.gatewayBaseUrl,
            properties.forbiddenDirectHosts,
        )
        val httpClient = MutualTlsClientFactory.createHttpClient(
            properties.keyStore,
            properties.keyStorePassword,
            properties.trustStore,
            properties.trustStorePassword,
        )
        return RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(JdkClientHttpRequestFactory(httpClient))
            .build()
    }

    @Bean
    fun tokenProvider(properties: BackendClientProperties): TokenProvider =
        ClientCredentialsTokenProvider(
            tokenRestClient = RestClient.builder().build(),
            tokenUri = properties.tokenUri,
            clientId = properties.clientId,
            clientSecret = properties.clientSecret,
            scope = properties.scope,
        )

    @Bean
    fun gatewayClient(
        gatewayRestClient: RestClient,
        tokenProvider: TokenProvider,
        correlationIdGenerator: CorrelationIdGenerator,
        objectMapper: ObjectMapper,
    ): GatewayClient =
        GatewayClient(gatewayRestClient, tokenProvider, correlationIdGenerator, objectMapper)
}
