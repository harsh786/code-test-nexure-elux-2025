package io.nexure.discount.config

object VatConfig {
    private val vatRates = mapOf(
        "Sweden" to 0.25,
        "Germany" to 0.19,
        "France" to 0.20
    )
    
    fun getVatRate(country: String): Double {
        return vatRates[country] ?: 0.0
    }
}
