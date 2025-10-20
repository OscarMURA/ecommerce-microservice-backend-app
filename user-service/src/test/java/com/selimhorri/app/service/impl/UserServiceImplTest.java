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

import com.selimhorri.app.domain.Credential;
import com.selimhorri.app.domain.User;
import com.selimhorri.app.dto.CredentialDto;
import com.selimhorri.app.dto.UserDto;
import com.selimhorri.app.exception.wrapper.UserObjectNotFoundException;
import com.selimhorri.app.repository.UserRepository;

/**
 * Pruebas unitarias para UserServiceImpl
 * Valida la lógica de negocio del servicio de usuarios
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserServiceImpl Unit Tests")
class UserServiceImplTest {

	@Mock
	private UserRepository userRepository;

	@InjectMocks
	private UserServiceImpl userService;

	private User testUser;
	private UserDto testUserDto;

	@BeforeEach
	void setUp() {
		// Configurar datos de prueba - inicializar el User con relaciones
		Credential credential = new Credential();
		credential.setCredentialId(1);
		credential.setUsername("johndoe");
		
		testUser = new User();
		testUser.setUserId(1);
		testUser.setFirstName("John");
		testUser.setLastName("Doe");
		testUser.setEmail("john@example.com");
		testUser.setCredential(credential); // Inicializar relación con Credential
		testUser.setAddresses(null); // Inicializar relación vacía

		testUserDto = new UserDto();
		testUserDto.setUserId(1);
		testUserDto.setFirstName("John");
		testUserDto.setLastName("Doe");
		testUserDto.setEmail("john@example.com");
	}

	/**
	 * Prueba 1: Validar que findById retorna un usuario existente
	 */
	@Test
	@DisplayName("Debe encontrar un usuario por ID correctamente")
	void testFindByIdSuccess() {
		// Arrange
		Integer userId = 1;
		when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

		// Act
		UserDto result = userService.findById(userId);

		// Assert
		assertNotNull(result);
		verify(userRepository, times(1)).findById(userId);
	}

	/**
	 * Prueba 2: Validar que findById lanza excepción cuando usuario no existe
	 */
	@Test
	@DisplayName("Debe lanzar excepción cuando usuario no existe")
	void testFindByIdNotFound() {
		// Arrange
		Integer userId = 999;
		when(userRepository.findById(userId)).thenReturn(Optional.empty());

		// Act & Assert
		assertThrows(UserObjectNotFoundException.class, () -> {
			userService.findById(userId);
		});
		verify(userRepository, times(1)).findById(userId);
	}

	/**
	 * Prueba 3: Validar que save guarda un nuevo usuario correctamente
	 */
	@Test
	@DisplayName("Debe guardar un nuevo usuario correctamente")
	void testSaveUserSuccess() {
		// Arrange
		CredentialDto credentialDto = new CredentialDto();
		credentialDto.setCredentialId(1);
		credentialDto.setUsername("johndoe");
		credentialDto.setPassword("password123");
		credentialDto.setIsEnabled(true);
		credentialDto.setIsAccountNonExpired(true);
		credentialDto.setIsAccountNonLocked(true);
		credentialDto.setIsCredentialsNonExpired(true);
		
		testUserDto.setCredentialDto(credentialDto);
		testUserDto.setAddressDtos(null);
		
		when(userRepository.save(any(User.class))).thenReturn(testUser);

		// Act
		UserDto result = userService.save(testUserDto);

		// Assert
		assertNotNull(result);
		verify(userRepository, times(1)).save(any(User.class));
	}

	/**
	 * Prueba 4: Validar que deleteById elimina un usuario
	 */
	@Test
	@DisplayName("Debe eliminar un usuario por ID correctamente")
	void testDeleteByIdSuccess() {
		// Arrange
		Integer userId = 1;
		doNothing().when(userRepository).deleteById(userId);

		// Act
		userService.deleteById(userId);

		// Assert
		verify(userRepository, times(1)).deleteById(userId);
	}

	/**
	 * Prueba 5: Validar que findAll retorna lista de usuarios
	 */
	@Test
	@DisplayName("Debe retornar lista de todos los usuarios")
	void testFindAllSuccess() {
		// Arrange
		Credential credential2 = new Credential();
		credential2.setCredentialId(2);
		credential2.setUsername("janedoe");
		
		User user2 = new User();
		user2.setUserId(2);
		user2.setFirstName("Jane");
		user2.setLastName("Doe");
		user2.setEmail("jane@example.com");
		user2.setCredential(credential2);
		user2.setAddresses(null);

		when(userRepository.findAll()).thenReturn(List.of(testUser, user2));

		// Act
		List<UserDto> result = userService.findAll();

		// Assert
		assertNotNull(result);
		assertTrue(result.size() >= 2);
		verify(userRepository, times(1)).findAll();
	}

	/**
	 * Prueba adicional: Validar que findByUsername encuentra un usuario por username
	 */
	@Test
	@DisplayName("Debe encontrar un usuario por username correctamente")
	void testFindByUsernameSuccess() {
		// Arrange
		String username = "johndoe";
		when(userRepository.findByCredentialUsername(username)).thenReturn(Optional.of(testUser));

		// Act
		UserDto result = userService.findByUsername(username);

		// Assert
		assertNotNull(result);
		verify(userRepository, times(1)).findByCredentialUsername(username);
	}

	/**
	 * Prueba adicional: Validar que findByUsername lanza excepción cuando no existe
	 */
	@Test
	@DisplayName("Debe lanzar excepción cuando usuario por username no existe")
	void testFindByUsernameNotFound() {
		// Arrange
		String username = "nonexistent";
		when(userRepository.findByCredentialUsername(username)).thenReturn(Optional.empty());

		// Act & Assert
		assertThrows(UserObjectNotFoundException.class, () -> {
			userService.findByUsername(username);
		});
		verify(userRepository, times(1)).findByCredentialUsername(username);
	}
}
