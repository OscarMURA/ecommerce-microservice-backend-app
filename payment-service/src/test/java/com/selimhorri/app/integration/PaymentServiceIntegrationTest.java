package com.selimhorri.app.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import javax.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.selimhorri.app.domain.Payment;
import com.selimhorri.app.domain.PaymentStatus;
import com.selimhorri.app.repository.PaymentRepository;

/**
 * Integration tests for Payment Service inter-service communication
 * 
 * These tests validate:
 * - Payment creation and retrieval
 * - Payment status management
 * - Service integration patterns
 * - Transaction boundaries
 * - Direct repository communication
 * 
 * @author Testing Team
 * @version 1.0
 */
@SpringBootTest
@Transactional
class PaymentServiceIntegrationTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
    }

    @Test
    @DisplayName("Test 1: Create Payment and Persist")
    void testCreatePayment() {
        // ARRANGE - Setup test data
        Payment payment = Payment.builder()
                .orderId(123)
                .isPayed(false)
                .paymentStatus(PaymentStatus.NOT_STARTED)
                .build();

        // ACT - Execute operation
        Payment savedPayment = paymentRepository.save(payment);

        // ASSERT - Verify results
        assertNotNull(savedPayment.getPaymentId());
        assertEquals(123, savedPayment.getOrderId());
        assertFalse(savedPayment.getIsPayed());
        assertEquals(PaymentStatus.NOT_STARTED, savedPayment.getPaymentStatus());
        assertEquals(1, paymentRepository.count());
    }

    @Test
    @DisplayName("Test 2: Retrieve Payment by ID")
    void testRetrievePaymentById() {
        // ARRANGE - Create and save payment
        Payment payment = Payment.builder()
                .orderId(456)
                .isPayed(true)
                .paymentStatus(PaymentStatus.COMPLETED)
                .build();
        
        Payment savedPayment = paymentRepository.save(payment);

        // ACT - Retrieve by ID
        Payment retrievedPayment = paymentRepository.findById(savedPayment.getPaymentId()).orElse(null);

        // ASSERT - Verify retrieval
        assertNotNull(retrievedPayment);
        assertEquals(savedPayment.getPaymentId(), retrievedPayment.getPaymentId());
        assertEquals(456, retrievedPayment.getOrderId());
        assertTrue(retrievedPayment.getIsPayed());
        assertEquals(PaymentStatus.COMPLETED, retrievedPayment.getPaymentStatus());
    }

    @Test
    @DisplayName("Test 3: Update Payment Status")
    void testUpdatePaymentStatus() {
        // ARRANGE - Create and save payment
        Payment payment = Payment.builder()
                .orderId(789)
                .isPayed(false)
                .paymentStatus(PaymentStatus.NOT_STARTED)
                .build();
        
        Payment savedPayment = paymentRepository.save(payment);

        // ACT - Update payment status
        savedPayment.setIsPayed(true);
        savedPayment.setPaymentStatus(PaymentStatus.COMPLETED);
        
        Payment updatedPayment = paymentRepository.save(savedPayment);

        // ASSERT - Verify update
        assertTrue(updatedPayment.getIsPayed());
        assertEquals(PaymentStatus.COMPLETED, updatedPayment.getPaymentStatus());
        assertEquals(789, updatedPayment.getOrderId());
        assertEquals(1, paymentRepository.count());
    }

    @Test
    @DisplayName("Test 4: Retrieve All Payments with Count")
    void testRetrieveAllPayments() {
        // ARRANGE - Create multiple payments
        Payment payment1 = Payment.builder()
                .orderId(100)
                .isPayed(false)
                .paymentStatus(PaymentStatus.NOT_STARTED)
                .build();
                
        Payment payment2 = Payment.builder()
                .orderId(200)
                .isPayed(true)
                .paymentStatus(PaymentStatus.COMPLETED)
                .build();

        Payment payment3 = Payment.builder()
                .orderId(300)
                .isPayed(false)
                .paymentStatus(PaymentStatus.IN_PROGRESS)
                .build();

        paymentRepository.save(payment1);
        paymentRepository.save(payment2);
        paymentRepository.save(payment3);

        // ACT - Retrieve all payments
        List<Payment> allPayments = paymentRepository.findAll();

        // ASSERT - Verify count and content
        assertEquals(3, allPayments.size());
        assertEquals(3, paymentRepository.count());
        assertTrue(allPayments.stream().anyMatch(p -> p.getOrderId().equals(100)));
        assertTrue(allPayments.stream().anyMatch(p -> p.getOrderId().equals(200)));
        assertTrue(allPayments.stream().anyMatch(p -> p.getOrderId().equals(300)));
    }

    @Test
    @DisplayName("Test 5: Delete Payment and Verify Cleanup")
    void testDeletePayment() {
        // ARRANGE - Create multiple payments
        Payment payment1 = Payment.builder()
                .orderId(400)
                .isPayed(false)
                .paymentStatus(PaymentStatus.NOT_STARTED)
                .build();
                
        Payment payment2 = Payment.builder()
                .orderId(500)
                .isPayed(true)
                .paymentStatus(PaymentStatus.COMPLETED)
                .build();

        Payment savedPayment1 = paymentRepository.save(payment1);
        Payment savedPayment2 = paymentRepository.save(payment2);

        // Verify initial count
        assertEquals(2, paymentRepository.count());

        // ACT - Delete one payment
        paymentRepository.deleteById(savedPayment1.getPaymentId());

        // ASSERT - Verify deletion
        assertEquals(1, paymentRepository.count());
        assertFalse(paymentRepository.existsById(savedPayment1.getPaymentId()));
        assertTrue(paymentRepository.existsById(savedPayment2.getPaymentId()));
    }
}
