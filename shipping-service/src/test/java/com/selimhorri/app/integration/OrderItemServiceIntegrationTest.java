package com.selimhorri.app.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import javax.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.selimhorri.app.domain.OrderItem;
import com.selimhorri.app.domain.id.OrderItemId;
import com.selimhorri.app.repository.OrderItemRepository;

/**
 * Integration tests for OrderItem Service (Shipping Service) inter-service communication
 * 
 * These tests validate:
 * - OrderItem creation and retrieval with composite keys
 * - Service integration patterns
 * - Transaction boundaries
 * - Direct repository communication
 * - Composite key handling for order items
 * 
 * @author Testing Team
 * @version 1.0
 */
@SpringBootTest
@Transactional
class OrderItemServiceIntegrationTest {

    @Autowired
    private OrderItemRepository orderItemRepository;

    @BeforeEach
    void setUp() {
        orderItemRepository.deleteAll();
    }

    @Test
    @DisplayName("Test 1: Create OrderItem with Composite Key")
    void testCreateOrderItem() {
        // ARRANGE - Setup test data
        OrderItem orderItem = OrderItem.builder()
                .productId(100)
                .orderId(200)
                .orderedQuantity(5)
                .build();

        // ACT - Execute operation
        OrderItem savedOrderItem = orderItemRepository.save(orderItem);

        // ASSERT - Verify results
        assertNotNull(savedOrderItem);
        assertEquals(100, savedOrderItem.getProductId());
        assertEquals(200, savedOrderItem.getOrderId());
        assertEquals(5, savedOrderItem.getOrderedQuantity());
        assertEquals(1, orderItemRepository.count());
    }

    @Test
    @DisplayName("Test 2: Retrieve OrderItem by Composite ID")
    void testRetrieveOrderItemById() {
        // ARRANGE - Create and save order item
        OrderItem orderItem = OrderItem.builder()
                .productId(300)
                .orderId(400)
                .orderedQuantity(10)
                .build();
        
        orderItemRepository.save(orderItem);
        OrderItemId orderItemId = new OrderItemId(300, 400);

        // ACT - Retrieve by composite ID
        OrderItem retrievedOrderItem = orderItemRepository.findById(orderItemId).orElse(null);

        // ASSERT - Verify retrieval
        assertNotNull(retrievedOrderItem);
        assertEquals(300, retrievedOrderItem.getProductId());
        assertEquals(400, retrievedOrderItem.getOrderId());
        assertEquals(10, retrievedOrderItem.getOrderedQuantity());
    }

    @Test
    @DisplayName("Test 3: Update OrderItem Quantity")
    void testUpdateOrderItem() {
        // ARRANGE - Create and save order item
        OrderItem orderItem = OrderItem.builder()
                .productId(500)
                .orderId(600)
                .orderedQuantity(3)
                .build();
        
        OrderItem savedOrderItem = orderItemRepository.save(orderItem);

        // ACT - Update quantity
        savedOrderItem.setOrderedQuantity(8);
        
        OrderItem updatedOrderItem = orderItemRepository.save(savedOrderItem);

        // ASSERT - Verify update
        assertEquals(8, updatedOrderItem.getOrderedQuantity());
        assertEquals(500, updatedOrderItem.getProductId());
        assertEquals(600, updatedOrderItem.getOrderId());
        assertEquals(1, orderItemRepository.count());
    }

    @Test
    @DisplayName("Test 4: Retrieve All OrderItems with Count")
    void testRetrieveAllOrderItems() {
        // ARRANGE - Create multiple order items
        OrderItem orderItem1 = OrderItem.builder()
                .productId(1000)
                .orderId(2000)
                .orderedQuantity(2)
                .build();
                
        OrderItem orderItem2 = OrderItem.builder()
                .productId(3000)
                .orderId(4000)
                .orderedQuantity(7)
                .build();

        OrderItem orderItem3 = OrderItem.builder()
                .productId(5000)
                .orderId(6000)
                .orderedQuantity(1)
                .build();

        orderItemRepository.save(orderItem1);
        orderItemRepository.save(orderItem2);
        orderItemRepository.save(orderItem3);

        // ACT - Retrieve all order items
        List<OrderItem> allOrderItems = orderItemRepository.findAll();

        // ASSERT - Verify count and content
        assertEquals(3, allOrderItems.size());
        assertEquals(3, orderItemRepository.count());
        assertTrue(allOrderItems.stream().anyMatch(oi -> oi.getProductId().equals(1000)));
        assertTrue(allOrderItems.stream().anyMatch(oi -> oi.getProductId().equals(3000)));
        assertTrue(allOrderItems.stream().anyMatch(oi -> oi.getProductId().equals(5000)));
    }

    @Test
    @DisplayName("Test 5: Delete OrderItem with Composite Key")
    void testDeleteOrderItem() {
        // ARRANGE - Create multiple order items
        OrderItem orderItem1 = OrderItem.builder()
                .productId(7000)
                .orderId(8000)
                .orderedQuantity(4)
                .build();
                
        OrderItem orderItem2 = OrderItem.builder()
                .productId(9000)
                .orderId(10000)
                .orderedQuantity(6)
                .build();

        orderItemRepository.save(orderItem1);
        orderItemRepository.save(orderItem2);

        // Verify initial count
        assertEquals(2, orderItemRepository.count());

        // ACT - Delete one order item using composite key
        OrderItemId orderItemId = new OrderItemId(7000, 8000);
        orderItemRepository.deleteById(orderItemId);

        // ASSERT - Verify deletion
        assertEquals(1, orderItemRepository.count());
        assertFalse(orderItemRepository.existsById(orderItemId));
        
        // Verify the other order item still exists
        OrderItemId remainingId = new OrderItemId(9000, 10000);
        assertTrue(orderItemRepository.existsById(remainingId));
    }
}
