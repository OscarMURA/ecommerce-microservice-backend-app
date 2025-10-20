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

import com.selimhorri.app.domain.OrderItem;
import com.selimhorri.app.domain.id.OrderItemId;
import com.selimhorri.app.dto.OrderDto;
import com.selimhorri.app.dto.OrderItemDto;
import com.selimhorri.app.dto.ProductDto;
import com.selimhorri.app.exception.wrapper.OrderItemNotFoundException;
import com.selimhorri.app.repository.OrderItemRepository;

/**
 * Pruebas unitarias para OrderItemServiceImpl
 * Valida la lógica de negocio del servicio de items de orden
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderItemServiceImpl Unit Tests")
class OrderItemServiceImplTest {

	@Mock
	private OrderItemRepository orderItemRepository;

	@Mock
	private RestTemplate restTemplate;

	@InjectMocks
	private OrderItemServiceImpl orderItemService;

	private OrderItem testOrderItem;
	private OrderItemDto testOrderItemDto;
	private OrderItemId testOrderItemId;

	@BeforeEach
	void setUp() {
		// Configurar datos de prueba
		testOrderItemId = new OrderItemId(1, 1);

		testOrderItem = new OrderItem();
		testOrderItem.setProductId(1);
		testOrderItem.setOrderId(1);
		testOrderItem.setOrderedQuantity(5);

		OrderDto orderDto = new OrderDto();
		orderDto.setOrderId(1);

		ProductDto productDto = new ProductDto();
		productDto.setProductId(1);
		productDto.setProductTitle("Laptop");

		testOrderItemDto = new OrderItemDto();
		testOrderItemDto.setProductId(1);
		testOrderItemDto.setOrderId(1);
		testOrderItemDto.setOrderedQuantity(5);
		testOrderItemDto.setOrderDto(orderDto);
		testOrderItemDto.setProductDto(productDto);
	}

	/**
	 * Prueba 1: Validar que findById retorna un item de orden existente
	 */
	@Test
	@DisplayName("Debe encontrar un item de orden por ID correctamente")
	void testFindByIdSuccess() {
		// Arrange
		OrderDto orderDto = new OrderDto();
		orderDto.setOrderId(1);
		ProductDto productDto = new ProductDto();
		productDto.setProductId(1);

		when(orderItemRepository.findById(any())).thenReturn(Optional.of(testOrderItem));
		when(restTemplate.getForObject(anyString(), eq(ProductDto.class))).thenReturn(productDto);
		when(restTemplate.getForObject(anyString(), eq(OrderDto.class))).thenReturn(orderDto);

		// Act
		OrderItemDto result = orderItemService.findById(testOrderItemId);

		// Assert
		assertNotNull(result);
		assertEquals(1, result.getProductId());
		assertEquals(1, result.getOrderId());
		verify(orderItemRepository, times(1)).findById(any());
	}

	/**
	 * Prueba 2: Validar que findById lanza excepción cuando item no existe
	 */
	@Test
	@DisplayName("Debe lanzar excepción cuando item de orden no existe")
	void testFindByIdNotFound() {
		// Arrange
		when(orderItemRepository.findById(any())).thenReturn(Optional.empty());

		// Act & Assert
		assertThrows(OrderItemNotFoundException.class, () -> {
			orderItemService.findById(testOrderItemId);
		});
		verify(orderItemRepository, times(1)).findById(any());
	}

	/**
	 * Prueba 3: Validar que save guarda un nuevo item de orden correctamente
	 */
	@Test
	@DisplayName("Debe guardar un nuevo item de orden correctamente")
	void testSaveOrderItemSuccess() {
		// Arrange
		when(orderItemRepository.save(any(OrderItem.class))).thenReturn(testOrderItem);

		// Act
		OrderItemDto result = orderItemService.save(testOrderItemDto);

		// Assert
		assertNotNull(result);
		assertEquals(5, result.getOrderedQuantity());
		verify(orderItemRepository, times(1)).save(any(OrderItem.class));
	}

	/**
	 * Prueba 4: Validar que findAll retorna lista de items de orden
	 */
	@Test
	@DisplayName("Debe retornar lista de todos los items de orden")
	void testFindAllSuccess() {
		// Arrange
		OrderItem orderItem2 = new OrderItem();
		orderItem2.setProductId(2);
		orderItem2.setOrderId(2);
		orderItem2.setOrderedQuantity(3);

		OrderDto orderDto = new OrderDto();
		orderDto.setOrderId(1);
		ProductDto productDto = new ProductDto();
		productDto.setProductId(1);

		when(orderItemRepository.findAll()).thenReturn(List.of(testOrderItem, orderItem2));
		when(restTemplate.getForObject(anyString(), eq(ProductDto.class))).thenReturn(productDto);
		when(restTemplate.getForObject(anyString(), eq(OrderDto.class))).thenReturn(orderDto);

		// Act
		List<OrderItemDto> result = orderItemService.findAll();

		// Assert
		assertNotNull(result);
		assertTrue(result.size() >= 2);
		verify(orderItemRepository, times(1)).findAll();
	}

	/**
	 * Prueba 5: Validar que update actualiza un item de orden correctamente
	 */
	@Test
	@DisplayName("Debe actualizar un item de orden correctamente")
	void testUpdateOrderItemSuccess() {
		// Arrange
		testOrderItemDto.setOrderedQuantity(10);

		when(orderItemRepository.save(any(OrderItem.class))).thenReturn(testOrderItem);

		// Act
		OrderItemDto result = orderItemService.update(testOrderItemDto);

		// Assert
		assertNotNull(result);
		verify(orderItemRepository, times(1)).save(any(OrderItem.class));
	}

	/**
	 * Prueba 6: Validar que deleteById elimina un item de orden
	 */
	@Test
	@DisplayName("Debe eliminar un item de orden por ID correctamente")
	void testDeleteByIdSuccess() {
		// Arrange
		doNothing().when(orderItemRepository).deleteById(testOrderItemId);

		// Act
		orderItemService.deleteById(testOrderItemId);

		// Assert
		verify(orderItemRepository, times(1)).deleteById(testOrderItemId);
	}
}
