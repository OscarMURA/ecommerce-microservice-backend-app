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
import com.selimhorri.app.exception.wrapper.CredentialNotFoundException;
import com.selimhorri.app.repository.CredentialRepository;

/**
 * Pruebas unitarias para CredentialServiceImpl
 * Valida la lógica de negocio del servicio de credenciales
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CredentialServiceImpl Unit Tests")
class CredentialServiceImplTest {

	@Mock
	private CredentialRepository credentialRepository;

	@InjectMocks
	private CredentialServiceImpl credentialService;

	private Credential testCredential;
	private CredentialDto testCredentialDto;

	@BeforeEach
	void setUp() {
		// Configurar datos de prueba
		User testUser = new User();
		testUser.setUserId(1);
		testUser.setFirstName("John");
		testUser.setLastName("Doe");
		testUser.setEmail("john@example.com");
		
		testCredential = new Credential();
		testCredential.setCredentialId(1);
		testCredential.setUsername("johndoe");
		testCredential.setPassword("encrypted_password_123");
		testCredential.setIsEnabled(true);
		testCredential.setUser(testUser);

		UserDto userDto = new UserDto();
		userDto.setUserId(1);
		userDto.setFirstName("John");
		userDto.setLastName("Doe");
		userDto.setEmail("john@example.com");
		
		testCredentialDto = new CredentialDto();
		testCredentialDto.setCredentialId(1);
		testCredentialDto.setUsername("johndoe");
		testCredentialDto.setPassword("encrypted_password_123");
		testCredentialDto.setIsEnabled(true);
		testCredentialDto.setUserDto(userDto);
	}

	/**
	 * Prueba 1: Validar que findById retorna una credencial existente
	 */
	@Test
	@DisplayName("Debe encontrar una credencial por ID correctamente")
	void testFindByIdSuccess() {
		// Arrange
		Integer credentialId = 1;
		when(credentialRepository.findById(credentialId)).thenReturn(Optional.of(testCredential));

		// Act
		CredentialDto result = credentialService.findById(credentialId);

		// Assert
		assertNotNull(result);
		verify(credentialRepository, times(1)).findById(credentialId);
	}

	/**
	 * Prueba 2: Validar que findById lanza excepción cuando credencial no existe
	 */
	@Test
	@DisplayName("Debe lanzar excepción cuando credencial no existe")
	void testFindByIdNotFound() {
		// Arrange
		Integer credentialId = 999;
		when(credentialRepository.findById(credentialId)).thenReturn(Optional.empty());

		// Act & Assert
		assertThrows(CredentialNotFoundException.class, () -> {
			credentialService.findById(credentialId);
		});
		verify(credentialRepository, times(1)).findById(credentialId);
	}

	/**
	 * Prueba 3: Validar que save guarda una nueva credencial correctamente
	 */
	@Test
	@DisplayName("Debe guardar una nueva credencial correctamente")
	void testSaveCredentialSuccess() {
		// Arrange
		when(credentialRepository.save(any(Credential.class))).thenReturn(testCredential);

		// Act
		CredentialDto result = credentialService.save(testCredentialDto);

		// Assert
		assertNotNull(result);
		verify(credentialRepository, times(1)).save(any(Credential.class));
	}

	/**
	 * Prueba 4: Validar que deleteById elimina una credencial
	 */
	@Test
	@DisplayName("Debe eliminar una credencial por ID correctamente")
	void testDeleteByIdSuccess() {
		// Arrange
		Integer credentialId = 1;
		doNothing().when(credentialRepository).deleteById(credentialId);

		// Act
		credentialService.deleteById(credentialId);

		// Assert
		verify(credentialRepository, times(1)).deleteById(credentialId);
	}

	/**
	 * Prueba 5: Validar que findAll retorna lista de credenciales
	 */
	@Test
	@DisplayName("Debe retornar lista de todas las credenciales")
	void testFindAllSuccess() {
		// Arrange
		User user2 = new User();
		user2.setUserId(2);
		user2.setFirstName("Jane");
		user2.setLastName("Doe");
		user2.setEmail("jane@example.com");
		
		Credential credential2 = new Credential();
		credential2.setCredentialId(2);
		credential2.setUsername("janedoe");
		credential2.setPassword("encrypted_password_456");
		credential2.setIsEnabled(true);
		credential2.setUser(user2);

		when(credentialRepository.findAll()).thenReturn(List.of(testCredential, credential2));

		// Act
		List<CredentialDto> result = credentialService.findAll();

		// Assert
		assertNotNull(result);
		assertTrue(result.size() >= 2);
		verify(credentialRepository, times(1)).findAll();
	}

	/**
	 * Prueba adicional: Validar que update actualiza una credencial
	 */
	@Test
	@DisplayName("Debe actualizar una credencial correctamente")
	void testUpdateCredentialSuccess() {
		// Arrange
		testCredentialDto.setPassword("new_encrypted_password");
		
		when(credentialRepository.save(any(Credential.class))).thenReturn(testCredential);

		// Act
		CredentialDto result = credentialService.update(testCredentialDto);

		// Assert
		assertNotNull(result);
		verify(credentialRepository, times(1)).save(any(Credential.class));
	}
}
