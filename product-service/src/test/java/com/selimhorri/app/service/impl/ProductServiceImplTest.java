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

import com.selimhorri.app.domain.Category;
import com.selimhorri.app.domain.Product;
import com.selimhorri.app.dto.CategoryDto;
import com.selimhorri.app.dto.ProductDto;
import com.selimhorri.app.exception.wrapper.ProductNotFoundException;
import com.selimhorri.app.repository.ProductRepository;

/**
 * Pruebas unitarias para ProductServiceImpl
 * Valida la lógica de negocio del servicio de productos
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductServiceImpl Unit Tests")
class ProductServiceImplTest {

	@Mock
	private ProductRepository productRepository;

	@InjectMocks
	private ProductServiceImpl productService;

	private Product testProduct;
	private ProductDto testProductDto;

	@BeforeEach
	void setUp() {
		// Configurar datos de prueba
		Category category = new Category();
		category.setCategoryId(1);
		category.setCategoryTitle("Electronics");

		testProduct = new Product();
		testProduct.setProductId(1);
		testProduct.setProductTitle("Laptop");
		testProduct.setSku("LAPTOP-001");
		testProduct.setPriceUnit(999.99);
		testProduct.setQuantity(10);
		testProduct.setImageUrl("https://example.com/laptop.jpg");
		testProduct.setCategory(category);

		CategoryDto categoryDto = new CategoryDto();
		categoryDto.setCategoryId(1);
		categoryDto.setCategoryTitle("Electronics");

		testProductDto = new ProductDto();
		testProductDto.setProductId(1);
		testProductDto.setProductTitle("Laptop");
		testProductDto.setSku("LAPTOP-001");
		testProductDto.setPriceUnit(999.99);
		testProductDto.setQuantity(10);
		testProductDto.setImageUrl("https://example.com/laptop.jpg");
		testProductDto.setCategoryDto(categoryDto);
	}

	/**
	 * Prueba 1: Validar que findById retorna un producto existente
	 */
	@Test
	@DisplayName("Debe encontrar un producto por ID correctamente")
	void testFindByIdSuccess() {
		// Arrange
		Integer productId = 1;
		when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));

		// Act
		ProductDto result = productService.findById(productId);

		// Assert
		assertNotNull(result);
		assertEquals("Laptop", result.getProductTitle());
		verify(productRepository, times(1)).findById(productId);
	}

	/**
	 * Prueba 2: Validar que findById lanza excepción cuando producto no existe
	 */
	@Test
	@DisplayName("Debe lanzar excepción cuando producto no existe")
	void testFindByIdNotFound() {
		// Arrange
		Integer productId = 999;
		when(productRepository.findById(productId)).thenReturn(Optional.empty());

		// Act & Assert
		assertThrows(ProductNotFoundException.class, () -> {
			productService.findById(productId);
		});
		verify(productRepository, times(1)).findById(productId);
	}

	/**
	 * Prueba 3: Validar que save guarda un nuevo producto correctamente
	 */
	@Test
	@DisplayName("Debe guardar un nuevo producto correctamente")
	void testSaveProductSuccess() {
		// Arrange
		when(productRepository.save(any(Product.class))).thenReturn(testProduct);

		// Act
		ProductDto result = productService.save(testProductDto);

		// Assert
		assertNotNull(result);
		assertEquals("Laptop", result.getProductTitle());
		verify(productRepository, times(1)).save(any(Product.class));
	}

	/**
	 * Prueba 4: Validar que findAll retorna lista de productos
	 */
	@Test
	@DisplayName("Debe retornar lista de todos los productos")
	void testFindAllSuccess() {
		// Arrange
		Category category2 = new Category();
		category2.setCategoryId(2);
		category2.setCategoryTitle("Accessories");

		Product product2 = new Product();
		product2.setProductId(2);
		product2.setProductTitle("Mouse");
		product2.setSku("MOUSE-001");
		product2.setPriceUnit(29.99);
		product2.setQuantity(50);
		product2.setImageUrl("https://example.com/mouse.jpg");
		product2.setCategory(category2);

		when(productRepository.findAll()).thenReturn(List.of(testProduct, product2));

		// Act
		List<ProductDto> result = productService.findAll();

		// Assert
		assertNotNull(result);
		assertTrue(result.size() >= 2);
		verify(productRepository, times(1)).findAll();
	}

	/**
	 * Prueba 5: Validar que update actualiza un producto correctamente
	 */
	@Test
	@DisplayName("Debe actualizar un producto correctamente")
	void testUpdateProductSuccess() {
		// Arrange
		testProductDto.setProductTitle("Updated Laptop");
		testProductDto.setPriceUnit(1099.99);

		when(productRepository.save(any(Product.class))).thenReturn(testProduct);

		// Act
		ProductDto result = productService.update(testProductDto);

		// Assert
		assertNotNull(result);
		verify(productRepository, times(1)).save(any(Product.class));
	}

	/**
	 * Prueba 6: Validar que deleteById elimina un producto
	 */
	@Test
	@DisplayName("Debe eliminar un producto por ID correctamente")
	void testDeleteByIdSuccess() {
		// Arrange
		Integer productId = 1;
		when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));
		doNothing().when(productRepository).delete(any(Product.class));

		// Act
		productService.deleteById(productId);

		// Assert
		verify(productRepository, times(1)).findById(productId);
		verify(productRepository, times(1)).delete(any(Product.class));
	}
}
