package com.selimhorri.app.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import javax.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.selimhorri.app.domain.Category;
import com.selimhorri.app.domain.Product;
import com.selimhorri.app.repository.CategoryRepository;
import com.selimhorri.app.repository.ProductRepository;

/**
 * Integration tests for Product Service inter-service communication
 * 
 * These tests validate:
 * - Product creation and retrieval
 * - Category relationships
 * - Service integration patterns
 * - Transaction boundaries
 * - Direct repository communication
 * 
 * @author Testing Team
 * @version 1.0
 */
@SpringBootTest
@Transactional
class ProductServiceIntegrationTest {

    @Autowired
    private ProductRepository productRepository;
    
    @Autowired
    private CategoryRepository categoryRepository;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    @Test
    @DisplayName("Test 1: Create Product and Persist")
    void testCreateProduct() {
        // ARRANGE - Setup test data
        Product product = Product.builder()
                .productTitle("Test Product")
                .imageUrl("http://test.com/image.jpg")
                .sku("TEST-SKU-001")
                .priceUnit(99.99)
                .quantity(10)
                .build();

        // ACT - Execute operation
        Product savedProduct = productRepository.save(product);

        // ASSERT - Verify results
        assertNotNull(savedProduct.getProductId());
        assertEquals("Test Product", savedProduct.getProductTitle());
        assertEquals("TEST-SKU-001", savedProduct.getSku());
        assertEquals(99.99, savedProduct.getPriceUnit());
        assertEquals(10, savedProduct.getQuantity());
        assertEquals(1, productRepository.count());
    }

    @Test
    @DisplayName("Test 2: Retrieve Product by ID")
    void testRetrieveProductById() {
        // ARRANGE - Create and save product
        Product product = Product.builder()
                .productTitle("Retrieve Test Product")
                .sku("RETRIEVE-SKU-001")
                .priceUnit(149.99)
                .quantity(5)
                .build();
        
        Product savedProduct = productRepository.save(product);

        // ACT - Retrieve by ID
        Product retrievedProduct = productRepository.findById(savedProduct.getProductId()).orElse(null);

        // ASSERT - Verify retrieval
        assertNotNull(retrievedProduct);
        assertEquals(savedProduct.getProductId(), retrievedProduct.getProductId());
        assertEquals("Retrieve Test Product", retrievedProduct.getProductTitle());
        assertEquals("RETRIEVE-SKU-001", retrievedProduct.getSku());
        assertEquals(149.99, retrievedProduct.getPriceUnit());
        assertEquals(5, retrievedProduct.getQuantity());
    }

    @Test
    @DisplayName("Test 3: Update Product Information")
    void testUpdateProduct() {
        // ARRANGE - Create and save product
        Product product = Product.builder()
                .productTitle("Original Product")
                .sku("UPDATE-SKU-001")
                .priceUnit(50.00)
                .quantity(20)
                .build();
        
        Product savedProduct = productRepository.save(product);

        // ACT - Update product
        savedProduct.setProductTitle("Updated Product");
        savedProduct.setPriceUnit(75.00);
        savedProduct.setQuantity(15);
        
        Product updatedProduct = productRepository.save(savedProduct);

        // ASSERT - Verify update
        assertEquals("Updated Product", updatedProduct.getProductTitle());
        assertEquals(75.00, updatedProduct.getPriceUnit());
        assertEquals(15, updatedProduct.getQuantity());
        assertEquals(1, productRepository.count());
    }

    @Test
    @DisplayName("Test 4: Retrieve All Products with Count")
    void testRetrieveAllProducts() {
        // ARRANGE - Create multiple products
        Product product1 = Product.builder()
                .productTitle("Product 1")
                .sku("SKU-001")
                .priceUnit(10.00)
                .quantity(5)
                .build();
                
        Product product2 = Product.builder()
                .productTitle("Product 2")
                .sku("SKU-002")
                .priceUnit(20.00)
                .quantity(10)
                .build();

        productRepository.save(product1);
        productRepository.save(product2);

        // ACT - Retrieve all products
        List<Product> allProducts = productRepository.findAll();

        // ASSERT - Verify count and content
        assertEquals(2, allProducts.size());
        assertEquals(2, productRepository.count());
        assertTrue(allProducts.stream().anyMatch(p -> "Product 1".equals(p.getProductTitle())));
        assertTrue(allProducts.stream().anyMatch(p -> "Product 2".equals(p.getProductTitle())));
    }

    @Test
    @DisplayName("Test 5: Product with Category Relationship")
    void testProductWithCategory() {
        // ARRANGE - Create category and product
        Category category = Category.builder()
                .categoryTitle("Electronics")
                .imageUrl("http://test.com/electronics.jpg")
                .build();
        
        Category savedCategory = categoryRepository.save(category);

        Product product = Product.builder()
                .productTitle("Laptop")
                .sku("LAPTOP-001")
                .priceUnit(999.99)
                .quantity(3)
                .category(savedCategory)
                .build();

        // ACT - Save product with category
        Product savedProduct = productRepository.save(product);

        // ASSERT - Verify relationship
        assertNotNull(savedProduct.getProductId());
        assertNotNull(savedProduct.getCategory());
        assertEquals("Electronics", savedProduct.getCategory().getCategoryTitle());
        assertEquals("Laptop", savedProduct.getProductTitle());
        assertEquals(1, productRepository.count());
        assertEquals(1, categoryRepository.count());
    }
}


