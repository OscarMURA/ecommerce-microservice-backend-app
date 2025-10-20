package com.selimhorri.app.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import com.selimhorri.app.domain.Payment;
import com.selimhorri.app.domain.PaymentStatus;
import com.selimhorri.app.dto.OrderDto;
import com.selimhorri.app.dto.PaymentDto;
import com.selimhorri.app.exception.wrapper.PaymentNotFoundException;
import com.selimhorri.app.repository.PaymentRepository;

/**
 * Pruebas unitarias para PaymentServiceImpl
 * Valida la lógica de negocio del servicio de pagos
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentServiceImpl Unit Tests")
class PaymentServiceImplTest {

	@Mock
	private PaymentRepository paymentRepository;

	@Mock
	private RestTemplate restTemplate;

	@InjectMocks
	private PaymentServiceImpl paymentService;

	private Payment testPayment;
	private PaymentDto testPaymentDto;

	@BeforeEach
	void setUp() {
		// Configurar datos de prueba
		testPayment = new Payment();
		testPayment.setPaymentId(1);
		testPayment.setOrderId(1);
		testPayment.setIsPayed(true);
		testPayment.setPaymentStatus(PaymentStatus.COMPLETED);

		testPaymentDto = new PaymentDto();
		testPaymentDto.setPaymentId(1);
		testPaymentDto.setIsPayed(true);
		testPaymentDto.setPaymentStatus(PaymentStatus.COMPLETED);
		
		OrderDto orderDto = new OrderDto();
		orderDto.setOrderId(1);
		testPaymentDto.setOrderDto(orderDto);
	}

	/**
	 * Prueba 1: Validar que findById retorna un pago existente
	 */
	@Test
	@DisplayName("Debe encontrar un pago por ID correctamente")
	void testFindByIdSuccess() {
		// Arrange
		Integer paymentId = 1;
		OrderDto orderDto = new OrderDto();
		orderDto.setOrderId(1);
		
		when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(testPayment));
		when(restTemplate.getForObject(anyString(), eq(OrderDto.class))).thenReturn(orderDto);

		// Act
		PaymentDto result = paymentService.findById(paymentId);

		// Assert
		assertNotNull(result);
		assertEquals(PaymentStatus.COMPLETED, result.getPaymentStatus());
		verify(paymentRepository, times(1)).findById(paymentId);
	}

	/**
	 * Prueba 2: Validar que findById lanza excepción cuando pago no existe
	 */
	@Test
	@DisplayName("Debe lanzar excepción cuando pago no existe")
	void testFindByIdNotFound() {
		// Arrange
		Integer paymentId = 999;
		when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

		// Act & Assert
		assertThrows(PaymentNotFoundException.class, () -> {
			paymentService.findById(paymentId);
		});
		verify(paymentRepository, times(1)).findById(paymentId);
	}

	/**
	 * Prueba 3: Validar que save guarda un nuevo pago correctamente
	 */
	@Test
	@DisplayName("Debe guardar un nuevo pago correctamente")
	void testSavePaymentSuccess() {
		// Arrange
		when(paymentRepository.save(any(Payment.class))).thenReturn(testPayment);

		// Act
		PaymentDto result = paymentService.save(testPaymentDto);

		// Assert
		assertNotNull(result);
		assertEquals(PaymentStatus.COMPLETED, result.getPaymentStatus());
		verify(paymentRepository, times(1)).save(any(Payment.class));
	}

	/**
	 * Prueba 4: Validar que findAll retorna lista de pagos
	 */
	@Test
	@DisplayName("Debe retornar lista de todos los pagos")
	void testFindAllSuccess() {
		// Arrange
		Payment payment2 = new Payment();
		payment2.setPaymentId(2);
		payment2.setOrderId(2);
		payment2.setIsPayed(false);
		payment2.setPaymentStatus(PaymentStatus.IN_PROGRESS);

		OrderDto orderDto = new OrderDto();
		orderDto.setOrderId(1);
		
		when(paymentRepository.findAll()).thenReturn(List.of(testPayment, payment2));
		when(restTemplate.getForObject(anyString(), eq(OrderDto.class))).thenReturn(orderDto);

		// Act
		List<PaymentDto> result = paymentService.findAll();

		// Assert
		assertNotNull(result);
		assertTrue(result.size() >= 2);
		verify(paymentRepository, times(1)).findAll();
	}

	/**
	 * Prueba 5: Validar que update actualiza un pago correctamente
	 */
	@Test
	@DisplayName("Debe actualizar un pago correctamente")
	void testUpdatePaymentSuccess() {
		// Arrange
		testPaymentDto.setPaymentStatus(PaymentStatus.IN_PROGRESS);
		testPaymentDto.setIsPayed(false);

		when(paymentRepository.save(any(Payment.class))).thenReturn(testPayment);

		// Act
		PaymentDto result = paymentService.update(testPaymentDto);

		// Assert
		assertNotNull(result);
		verify(paymentRepository, times(1)).save(any(Payment.class));
	}

	/**
	 * Prueba 6: Validar que deleteById elimina un pago
	 */
	@Test
	@DisplayName("Debe eliminar un pago por ID correctamente")
	void testDeleteByIdSuccess() {
		// Arrange
		Integer paymentId = 1;
		doNothing().when(paymentRepository).deleteById(paymentId);

		// Act
		paymentService.deleteById(paymentId);

		// Assert
		verify(paymentRepository, times(1)).deleteById(paymentId);
	}
}
