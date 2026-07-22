package com.quantumbank.backendclient

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class QuantumBankBackendClientApplication

fun main(args: Array<String>) {
    runApplication<QuantumBankBackendClientApplication>(*args)
}
