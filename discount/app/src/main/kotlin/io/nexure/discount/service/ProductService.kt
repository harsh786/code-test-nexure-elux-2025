package io.nexure.discount.service

import io.nexure.discount.config.VatConfig
import io.nexure.discount.model.Discount
import io.nexure.discount.model.Product
import io.nexure.discount.model.ProductResponse
import io.nexure.discount.repository.ProductRepository

class ProductService(private val repository: ProductRepository) {
    
    suspend fun getProductsByCountry(country: String): List<ProductResponse> {
        val products = repository.findByCountry(country)
        return products.map { toProductResponse(it) }
    }
    
    suspend fun applyDiscount(productId: String, discount: Discount): ProductResponse? {
        val updatedProduct = repository.applyDiscount(productId, discount) ?: return null
        return toProductResponse(updatedProduct)
    }
    
    private fun toProductResponse(product: Product): ProductResponse {
        val finalPrice = calculateFinalPrice(product)
        return ProductResponse(
            id = product.id,
            name = product.name,
            basePrice = product.basePrice,
            country = product.country,
            discounts = product.discounts,
            finalPrice = finalPrice
        )
    }
    
    private fun calculateFinalPrice(product: Product): Double {
        val vatRate = VatConfig.getVatRate(product.country)
        val totalDiscountPercent = product.discounts.sumOf { it.percent / 100.0 }
        return product.basePrice * (1 - totalDiscountPercent) * (1 + vatRate)
    }
}
