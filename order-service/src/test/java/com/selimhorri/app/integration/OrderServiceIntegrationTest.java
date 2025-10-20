package com.selimhorri.app.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;

import javax.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.selimhorri.app.domain.Order;
import com.selimhorri.app.repository.OrderRepository;

/**
 * Integration tests for Order Service inter-service communication
 * 
 * These tests validate:
 * - Order creation and retrieval
 * - Service integration patterns
 * - Transaction boundaries
 * - Direct repository communication
 * 
 * @author Testing Team
 * @version 1.0
 */
@SpringBootTest
@Transactional
class OrderServiceIntegrationTest {

    @Autowired
    private OrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        // Clear repository before each test
        orderRepository.deleteAll();
    }

    /**
     * Test 1: Create order and persist to database
     */
    @Test
    @DisplayName("Integration Test 1: Should create order and persist to database")
    void testCreateOrderIntegration() {
        // ARRANGE
        Order order = new Order();
        order.setOrderDesc("Test Order");
        order.setOrderFee(5000.0);
        order.setOrderDate(LocalDateTime.now());

        // ACT
        Order savedOrder = orderRepository.save(order);

        // ASSERT
        assertNotNull(savedOrder);
        assertNotNull(savedOrder.getOrderId());
        assertEquals(5000.0, savedOrder.getOrderFee());
        assertEquals("Test Order", savedOrder.getOrderDesc());
        assertNotNull(savedOrder.getOrderDate());
    }

    /**
     * Test 2: Retrieve order by ID with complete data
     */
    @Test
    @DisplayName("Integration Test 2: Should retrieve order by ID with complete data")
    void testRetrieveOrderWithCompleteData() {
        // ARRANGE
        Order order = new Order();
        order.setOrderDesc("Retrieve Test Order");
        order.setOrderFee(7500.0);
        order.setOrderDate(LocalDateTime.now());
        Order savedOrder = orderRepository.save(order);

        // ACT
        Order retrievedOrder = orderRepository.findById(savedOrder.getOrderId()).orElse(null);

        // ASSERT
        assertNotNull(retrievedOrder);
        assertEquals(savedOrder.getOrderId(), retrievedOrder.getOrderId());
        assertEquals(7500.0, retrievedOrder.getOrderFee());
        assertEquals("Retrieve Test Order", retrievedOrder.getOrderDesc());
        assertNotNull(retrievedOrder.getOrderDate());
    }

    /**
     * Test 3: Update order description atomically
     */
    @Test
    @DisplayName("Integration Test 3: Should update order description atomically")
    void testUpdateOrderStatusTransactional() {
        // ARRANGE
        Order order = new Order();
        order.setOrderDesc("Original Description");
        order.setOrderFee(10000.0);
        order.setOrderDate(LocalDateTime.now());
        Order savedOrder = orderRepository.save(order);

        // ACT
        savedOrder.setOrderDesc("Updated Description");
        Order updatedOrder = orderRepository.save(savedOrder);

        // ASSERT
        assertNotNull(updatedOrder);
        assertEquals("Updated Description", updatedOrder.getOrderDesc());
        
        // Verify in DB
        Order orderFromDB = orderRepository.findById(savedOrder.getOrderId()).orElse(null);
        assertNotNull(orderFromDB);
        assertEquals("Updated Description", orderFromDB.getOrderDesc());
    }

    /**
     * Test 4: Retrieve all orders with correct data
     */
    @Test
    @DisplayName("Integration Test 4: Should retrieve all orders with correct data")
    void testRetrieveAllOrdersIntegration() {
        // ARRANGE
        Order order1 = new Order();
        order1.setOrderDesc("Order 1");
        order1.setOrderFee(3000.0);
        order1.setOrderDate(LocalDateTime.now());
        orderRepository.save(order1);

        Order order2 = new Order();
        order2.setOrderDesc("Order 2");
        order2.setOrderFee(6000.0);
        order2.setOrderDate(LocalDateTime.now());
        orderRepository.save(order2);

        // ACT
        long totalOrders = orderRepository.count();

        // ASSERT
        assertNotNull(totalOrders);
        assertEquals(2, totalOrders);
    }

    /**
     * Test 5: Delete order and clean up database
     */
    @Test
    @DisplayName("Integration Test 5: Should delete order and clean up database")
    void testDeleteOrderIntegration() {
        // ARRANGE
        Order order1 = new Order();
        order1.setOrderDesc("Delete Test Order 1");
        order1.setOrderFee(5000.0);
        order1.setOrderDate(LocalDateTime.now());
        Order savedOrder1 = orderRepository.save(order1);

        Order order2 = new Order();
        order2.setOrderDesc("Delete Test Order 2");
        order2.setOrderFee(6000.0);
        order2.setOrderDate(LocalDateTime.now());
        Order savedOrder2 = orderRepository.save(order2);

        assertEquals(2, orderRepository.count());

        // ACT
        orderRepository.deleteById(savedOrder1.getOrderId());

        // ASSERT
        assertNull(orderRepository.findById(savedOrder1.getOrderId()).orElse(null));
        assertNotNull(orderRepository.findById(savedOrder2.getOrderId()).orElse(null));
        assertEquals(1, orderRepository.count());
    }
}
