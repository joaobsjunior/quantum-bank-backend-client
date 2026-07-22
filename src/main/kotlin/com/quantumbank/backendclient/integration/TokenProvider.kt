package com.quantumbank.backendclient.integration

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.util.Base64

/** Supplies a bearer access token for outbound banking calls. */
fun interface TokenProvider {
    /** @throws MissingCredentialException when no valid token can be obtained. */
    fun accessToken(): String
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class ClientCredentialsToken(
    @param:JsonProperty("access_token") val accessToken: String? = null,
)

/**
 * Obtains a token via the OAuth2 client-credentials grant against the IdP token
 * endpoint. Fail-closed: any error, or an empty/blank token, raises
 * [MissingCredentialException] so the caller never proceeds without credentials.
 */
class ClientCredentialsTokenProvider(
    private val tokenRestClient: RestClient,
    private val tokenUri: String,
    private val clientId: String,
    private val clientSecret: String,
    private val scope: String,
) : TokenProvider {

    override fun accessToken(): String {
        val form = LinkedMultiValueMap<String, String>()
        form.add("grant_type", "client_credentials")
        form.add("scope", scope)

        val basic = Base64.getEncoder()
            .encodeToString("$clientId:$clientSecret".toByteArray())

        val token = try {
            tokenRestClient.post()
                .uri(tokenUri)
                .header(HttpHeaders.AUTHORIZATION, "Basic $basic")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(ClientCredentialsToken::class.java)
        } catch (ex: RestClientException) {
            throw MissingCredentialException(
                "failed to obtain client-credentials token: ${ex.message}",
            )
        }

        val accessToken = token?.accessToken
        if (accessToken.isNullOrBlank()) {
            throw MissingCredentialException("token endpoint returned no access_token")
        }
        return accessToken
    }
}
