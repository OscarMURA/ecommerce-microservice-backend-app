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
import org.springframework.web.client.RestTemplate;

import com.selimhorri.app.domain.Favourite;
import com.selimhorri.app.domain.id.FavouriteId;
import com.selimhorri.app.dto.FavouriteDto;
import com.selimhorri.app.dto.ProductDto;
import com.selimhorri.app.dto.UserDto;
import com.selimhorri.app.exception.wrapper.FavouriteNotFoundException;
import com.selimhorri.app.repository.FavouriteRepository;

/**
 * Pruebas unitarias para FavouriteServiceImpl
 * Valida la lógica de negocio del servicio de favoritos
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FavouriteServiceImpl Unit Tests")
class FavouriteServiceImplTest {

	@Mock
	private FavouriteRepository favouriteRepository;

	@Mock
	private RestTemplate restTemplate;

	@InjectMocks
	private FavouriteServiceImpl favouriteService;

	private Favourite testFavourite;
	private FavouriteDto testFavouriteDto;
	private FavouriteId testFavouriteId;

	@BeforeEach
	void setUp() {
		// Configurar datos de prueba
		testFavouriteId = new FavouriteId(1, 1, LocalDateTime.now());

		testFavourite = new Favourite();
		testFavourite.setUserId(1);
		testFavourite.setProductId(1);
		testFavourite.setLikeDate(LocalDateTime.now());

		UserDto userDto = new UserDto();
		userDto.setUserId(1);
		userDto.setFirstName("John");

		ProductDto productDto = new ProductDto();
		productDto.setProductId(1);
		productDto.setProductTitle("Laptop");

		testFavouriteDto = new FavouriteDto();
		testFavouriteDto.setUserId(1);
		testFavouriteDto.setProductId(1);
		testFavouriteDto.setLikeDate(LocalDateTime.now());
		testFavouriteDto.setUserDto(userDto);
		testFavouriteDto.setProductDto(productDto);
	}

	/**
	 * Prueba 1: Validar que findById retorna un favorito existente
	 */
	@Test
	@DisplayName("Debe encontrar un favorito por ID correctamente")
	void testFindByIdSuccess() {
		// Arrange
		UserDto userDto = new UserDto();
		userDto.setUserId(1);
		ProductDto productDto = new ProductDto();
		productDto.setProductId(1);

		when(favouriteRepository.findById(testFavouriteId)).thenReturn(Optional.of(testFavourite));
		when(restTemplate.getForObject(anyString(), eq(UserDto.class))).thenReturn(userDto);
		when(restTemplate.getForObject(anyString(), eq(ProductDto.class))).thenReturn(productDto);

		// Act
		FavouriteDto result = favouriteService.findById(testFavouriteId);

		// Assert
		assertNotNull(result);
		assertEquals(1, result.getUserId());
		assertEquals(1, result.getProductId());
		verify(favouriteRepository, times(1)).findById(testFavouriteId);
	}

	/**
	 * Prueba 2: Validar que findById lanza excepción cuando favorito no existe
	 */
	@Test
	@DisplayName("Debe lanzar excepción cuando favorito no existe")
	void testFindByIdNotFound() {
		// Arrange
		when(favouriteRepository.findById(testFavouriteId)).thenReturn(Optional.empty());

		// Act & Assert
		assertThrows(FavouriteNotFoundException.class, () -> {
			favouriteService.findById(testFavouriteId);
		});
		verify(favouriteRepository, times(1)).findById(testFavouriteId);
	}

	/**
	 * Prueba 3: Validar que save guarda un nuevo favorito correctamente
	 */
	@Test
	@DisplayName("Debe guardar un nuevo favorito correctamente")
	void testSaveFavouriteSuccess() {
		// Arrange
		when(favouriteRepository.save(any(Favourite.class))).thenReturn(testFavourite);

		// Act
		FavouriteDto result = favouriteService.save(testFavouriteDto);

		// Assert
		assertNotNull(result);
		assertEquals(1, result.getUserId());
		verify(favouriteRepository, times(1)).save(any(Favourite.class));
	}

	/**
	 * Prueba 4: Validar que findAll retorna lista de favoritos
	 */
	@Test
	@DisplayName("Debe retornar lista de todos los favoritos")
	void testFindAllSuccess() {
		// Arrange
		Favourite favourite2 = new Favourite();
		favourite2.setUserId(2);
		favourite2.setProductId(2);
		favourite2.setLikeDate(LocalDateTime.now());

		UserDto userDto = new UserDto();
		userDto.setUserId(1);
		ProductDto productDto = new ProductDto();
		productDto.setProductId(1);

		when(favouriteRepository.findAll()).thenReturn(List.of(testFavourite, favourite2));
		when(restTemplate.getForObject(anyString(), eq(UserDto.class))).thenReturn(userDto);
		when(restTemplate.getForObject(anyString(), eq(ProductDto.class))).thenReturn(productDto);

		// Act
		List<FavouriteDto> result = favouriteService.findAll();

		// Assert
		assertNotNull(result);
		assertTrue(result.size() >= 2);
		verify(favouriteRepository, times(1)).findAll();
	}

	/**
	 * Prueba 5: Validar que update actualiza un favorito
	 */
	@Test
	@DisplayName("Debe actualizar un favorito correctamente")
	void testUpdateFavouriteSuccess() {
		// Arrange
		testFavouriteDto.setLikeDate(LocalDateTime.now());
		
		when(favouriteRepository.save(any(Favourite.class))).thenReturn(testFavourite);

		// Act
		FavouriteDto result = favouriteService.update(testFavouriteDto);

		// Assert
		assertNotNull(result);
		verify(favouriteRepository, times(1)).save(any(Favourite.class));
	}

	/**
	 * Prueba 6: Validar que deleteById elimina un favorito por ID
	 */
	@Test
	@DisplayName("Debe eliminar un favorito por ID correctamente")
	void testDeleteByIdSuccess() {
		// Arrange
		doNothing().when(favouriteRepository).deleteById(testFavouriteId);

		// Act
		favouriteService.deleteById(testFavouriteId);

		// Assert
		verify(favouriteRepository, times(1)).deleteById(testFavouriteId);
	}
}
