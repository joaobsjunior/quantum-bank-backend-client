package com.quantumbank.backendclient.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class CorrelationIdGeneratorTest {

    @Test
    fun `generates distinct, valid uuid correlation ids`() {
        val generator = CorrelationIdGenerator()

        val first = generator.newCorrelationId()
        val second = generator.newCorrelationId()

        assertThat(first).isNotEqualTo(second)
        assertThat(UUID.fromString(first)).isNotNull()
    }
}
