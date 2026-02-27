package io.nexure.discount

import com.mongodb.kotlin.client.coroutine.MongoClient
import io.nexure.discount.model.Discount
import io.nexure.discount.model.Product
import io.nexure.discount.repository.ProductRepository
import io.nexure.discount.service.ProductService  
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.utility.DockerImageName
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Test suite for the Product Discount Service.
 * 
 * Your job is to fix the implementation to make these tests pass.
 * If you identify tests with incorrect expectations, you may modify them (document why!).
 */
class ProductServiceTests {
    
    companion object {
        private val mongoContainer = MongoDBContainer(DockerImageName.parse("mongo:7.0"))
        private lateinit var mongoClient: MongoClient
        
        init {
            mongoContainer.start()
            mongoClient = MongoClient.create(mongoContainer.connectionString)
        }
    }
    
    private lateinit var repository: ProductRepository
    private lateinit var service: ProductService
    
    @BeforeTest
    fun setup() = runBlocking {
        repository = ProductRepository(mongoClient, "testdb_${System.currentTimeMillis()}")
        service = ProductService(repository)
        repository.init()
    }
    
    @AfterTest
    fun tearDown() = runBlocking {
        repository.deleteAll()
    }
    
    @Test
    fun `should calculate final price correctly with single discount`() = runBlocking {
        // Setup
        repository.save(
            Product(
                id = "prod-1",
                name = "Laptop",
                basePrice = 1000.0,
                country = "Sweden",
                discounts = listOf(Discount("SUMMER10", 10.0))
            )
        )
        
        // Test
        val products = service.getProductsByCountry("Sweden")
        
        // Assert
        assertEquals(1, products.size)
        val product = products[0]
        assertEquals("prod-1", product.id)
        assertEquals(1, product.discounts.size)
        
        // Expected: 1000 * (1 - 0.10) * (1 + 0.25) = 1000 * 0.9 * 1.25 = 1125.0
        assertEquals(1125.0, product.finalPrice, 0.01, 
            "Final price calculation is incorrect for single discount. " +
            "Formula: basePrice × (1 - discount) × (1 + VAT)")
    }
    
    @Test
    fun `should calculate final price correctly with multiple discounts`() = runBlocking {
        // Setup: Product with TWO discounts
        repository.save(
            Product(
                id = "prod-2",
                name = "Phone",
                basePrice = 500.0,
                country = "Germany",
                discounts = listOf(
                    Discount("DISCOUNT10", 10.0),
                    Discount("DISCOUNT20", 20.0)
                )
            )
        )
        
        // Test
        val products = service.getProductsByCountry("Germany")
        
        // Assert
        assertEquals(1, products.size)
        val product = products[0]
        assertEquals(2, product.discounts.size)
        
        assertEquals(428.40, product.finalPrice, 0.01,
            "Multiple discounts MUST be applied multiplicatively, not additively! " +
            "Correct: basePrice × (1-d1) × (1-d2) × ... × (1+VAT)")
    }
    
    @Test
    fun `should apply discount to product correctly`() = runBlocking {
        // Setup
        repository.save(
            Product(
                id = "prod-3",
                name = "Tablet",
                basePrice = 800.0,
                country = "France",
                discounts = emptyList()
            )
        )
        
        // Test: Apply a discount
        val result = service.applyDiscount("prod-3", Discount("NEWYEAR15", 15.0))
        
        // Assert
        assertNotNull(result)
        assertEquals("prod-3", result.id)
        assertEquals(1, result.discounts.size)
        assertEquals("NEWYEAR15", result.discounts[0].discountId)
        assertEquals(15.0, result.discounts[0].percent)
        
        // Expected: 800 * (1 - 0.15) * (1 + 0.20) = 800 * 0.85 * 1.20 = 816.0
        assertEquals(816.0, result.finalPrice, 0.01)
    }
    
    @Test
    fun `should be idempotent when applying same discount twice sequentially`() = runBlocking {
        // Setup
        repository.save(
            Product(
                id = "prod-4",
                name = "Monitor",
                basePrice = 300.0,
                country = "Sweden",
                discounts = emptyList()
            )
        )
        
        val discount = Discount("REPEAT10", 10.0)
        
        // Test: Apply discount twice
        val result1 = service.applyDiscount("prod-4", discount)
        val result2 = service.applyDiscount("prod-4", discount)
        
        // Assert: Second application should not change anything (idempotency)
        assertNotNull(result1)
        assertNotNull(result2)
        assertEquals(1, result1.discounts.size)
        assertEquals(1, result2.discounts.size)
        assertEquals(result1.finalPrice, result2.finalPrice, 0.01,
            "Applying the same discount twice should be idempotent (no change on second application)")
    }
    
    @Test
    fun `should handle concurrent discount applications safely - NO DUPLICATES`() = runBlocking {
        // Setup
        repository.save(
            Product(
                id = "prod-5",
                name = "Keyboard",
                basePrice = 100.0,
                country = "Germany",
                discounts = emptyList()
            )
        )
        
        val discount = Discount("CONCURRENT25", 25.0)
        
        // Test: Simulate 50 concurrent requests trying to apply the same discount
        // This is the CRITICAL concurrency test!
        val jobs = List(50) {
            async(Dispatchers.IO) {
                service.applyDiscount("prod-5", discount)
            }
        }
        
        val results = jobs.awaitAll()
        
        // Assert: All requests should succeed
        results.forEach { result ->
            assertNotNull(result, "All discount application requests should succeed")
        }
        
        // Verify that despite 50 concurrent requests, the discount was only applied ONCE
        val finalProduct = repository.findById("prod-5")
        assertNotNull(finalProduct)
        assertEquals(1, finalProduct.discounts.size,
            "Concurrent requests resulted in duplicate discounts! " +
            "The discount application must be concurrency-safe at the database level.")
        assertEquals("CONCURRENT25", finalProduct.discounts[0].discountId)
        
        // Verify final price
        val productResponse = service.getProductsByCountry("Germany").find { it.id == "prod-5" }
        assertNotNull(productResponse)
        // Expected: 100 * (1 - 0.25) * (1 + 0.19) = 89.25
        assertEquals(89.25, productResponse.finalPrice, 0.01,
            "Final price should reflect only ONE application of the discount")
    }
    
    @Test
    fun `should return null when applying discount to non-existent product`() = runBlocking {
        // Test
        val result = service.applyDiscount("non-existent", Discount("NOTFOUND10", 10.0))
        
        // Assert
        assertNull(result, "Should return null for non-existent product")
    }
    
    @Test
    fun `should return empty list for country with no products`() = runBlocking {
        // Test
        val products = service.getProductsByCountry("NonExistentCountry")
        
        // Assert
        assertTrue(products.isEmpty(), "Should return empty list for country with no products")
    }
    
    @Test
    fun `should correctly apply VAT rates for different countries`() = runBlocking {
        // Setup products in different countries
        repository.save(Product("s1", "Item", 100.0, "Sweden", emptyList()))
        repository.save(Product("g1", "Item", 100.0, "Germany", emptyList()))
        repository.save(Product("f1", "Item", 100.0, "France", emptyList()))
        
        // Test
        val sweden = service.getProductsByCountry("Sweden")[0]
        val germany = service.getProductsByCountry("Germany")[0]
        val france = service.getProductsByCountry("France")[0]
        
        // Assert: VAT rates: Sweden 25%, Germany 19%, France 20%
        assertEquals(125.0, sweden.finalPrice, 0.01, "Sweden VAT should be 25%")
        assertEquals(119.0, germany.finalPrice, 0.01, "Germany VAT should be 19%")
        assertEquals(120.0, france.finalPrice, 0.01, "France VAT should be 20%")
    }
    
    @Test
    fun `should handle products from unknown countries`() = runBlocking {
        // Setup
        repository.save(Product("uk1", "Item", 100.0, "UnitedKingdom", emptyList()))
        
        // Test
        val products = service.getProductsByCountry("UnitedKingdom")
        
        // Assert
        assertEquals(1, products.size)
        assertEquals(100.0, products[0].finalPrice, 0.01)
    }
}
