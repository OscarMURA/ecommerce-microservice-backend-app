package com.selimhorri.app.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.selimhorri.app.domain.Cart;
import com.selimhorri.app.domain.Order;
import com.selimhorri.app.dto.CartDto;
import com.selimhorri.app.dto.OrderDto;
import com.selimhorri.app.exception.wrapper.OrderNotFoundException;
import com.selimhorri.app.repository.OrderRepository;

/**
 * Pruebas unitarias para OrderServiceImpl
 * Valida la lógica de negocio del servicio de órdenes
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderServiceImpl Unit Tests")
class OrderServiceImplTest {

	@Mock
	private OrderRepository orderRepository;

	@InjectMocks
	private OrderServiceImpl orderService;

	private Order testOrder;
	private OrderDto testOrderDto;

	@BeforeEach
	void setUp() {
		// Configurar datos de prueba
		Cart cart = new Cart();
		cart.setCartId(1);

		testOrder = new Order();
		testOrder.setOrderId(1);
		testOrder.setOrderDate(LocalDateTime.now());
		testOrder.setOrderDesc("Electronics order");
		testOrder.setOrderFee(150.00);
		testOrder.setCart(cart);

		CartDto cartDto = new CartDto();
		cartDto.setCartId(1);

		testOrderDto = new OrderDto();
		testOrderDto.setOrderId(1);
		testOrderDto.setOrderDate(LocalDateTime.now());
		testOrderDto.setOrderDesc("Electronics order");
		testOrderDto.setOrderFee(150.00);
		testOrderDto.setCartDto(cartDto);
	}

	/**
	 * Prueba 1: Validar que findById retorna una orden existente
	 */
	@Test
	@DisplayName("Debe encontrar una orden por ID correctamente")
	void testFindByIdSuccess() {
		// Arrange
		Integer orderId = 1;
		when(orderRepository.findById(orderId)).thenReturn(Optional.of(testOrder));

		// Act
		OrderDto result = orderService.findById(orderId);

		// Assert
		assertNotNull(result);
		assertEquals("Electronics order", result.getOrderDesc());
		verify(orderRepository, times(1)).findById(orderId);
	}

	/**
	 * Prueba 2: Validar que findById lanza excepción cuando orden no existe
	 */
	@Test
	@DisplayName("Debe lanzar excepción cuando orden no existe")
	void testFindByIdNotFound() {
		// Arrange
		Integer orderId = 999;
		when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

		// Act & Assert
		assertThrows(OrderNotFoundException.class, () -> {
			orderService.findById(orderId);
		});
		verify(orderRepository, times(1)).findById(orderId);
	}

	/**
	 * Prueba 3: Validar que save guarda una nueva orden correctamente
	 */
	@Test
	@DisplayName("Debe guardar una nueva orden correctamente")
	void testSaveOrderSuccess() {
		// Arrange
		when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

		// Act
		OrderDto result = orderService.save(testOrderDto);

		// Assert
		assertNotNull(result);
		assertEquals("Electronics order", result.getOrderDesc());
		verify(orderRepository, times(1)).save(any(Order.class));
	}

	/**
	 * Prueba 4: Validar que findAll retorna lista de órdenes
	 */
	@Test
	@DisplayName("Debe retornar lista de todas las órdenes")
	void testFindAllSuccess() {
		// Arrange
		Cart cart2 = new Cart();
		cart2.setCartId(2);

		Order order2 = new Order();
		order2.setOrderId(2);
		order2.setOrderDate(LocalDateTime.now());
		order2.setOrderDesc("Clothing order");
		order2.setOrderFee(75.00);
		order2.setCart(cart2);

		when(orderRepository.findAll()).thenReturn(List.of(testOrder, order2));

		// Act
		List<OrderDto> result = orderService.findAll();

		// Assert
		assertNotNull(result);
		assertTrue(result.size() >= 2);
		verify(orderRepository, times(1)).findAll();
	}

	/**
	 * Prueba 5: Validar que update actualiza una orden correctamente
	 */
	@Test
	@DisplayName("Debe actualizar una orden correctamente")
	void testUpdateOrderSuccess() {
		// Arrange
		testOrderDto.setOrderFee(200.00);
		testOrderDto.setOrderDesc("Updated order");

		when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

		// Act
		OrderDto result = orderService.update(testOrderDto);

		// Assert
		assertNotNull(result);
		verify(orderRepository, times(1)).save(any(Order.class));
	}

	/**
	 * Prueba 6: Validar que deleteById elimina una orden
	 */
	@Test
	@DisplayName("Debe eliminar una orden por ID correctamente")
	void testDeleteByIdSuccess() {
		// Arrange
		Integer orderId = 1;
		when(orderRepository.findById(orderId)).thenReturn(Optional.of(testOrder));
		doNothing().when(orderRepository).delete(any(Order.class));

		// Act
		orderService.deleteById(orderId);

		// Assert
		verify(orderRepository, times(1)).findById(orderId);
		verify(orderRepository, times(1)).delete(any(Order.class));
	}
}
