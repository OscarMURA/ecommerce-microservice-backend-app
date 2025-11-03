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

import com.selimhorri.app.domain.Address;
import com.selimhorri.app.domain.Credential;
import com.selimhorri.app.domain.User;
import com.selimhorri.app.dto.AddressDto;
import com.selimhorri.app.dto.CredentialDto;
import com.selimhorri.app.dto.UserDto;
import com.selimhorri.app.exception.wrapper.AddressNotFoundException;
import com.selimhorri.app.repository.AddressRepository;

/**
 * Pruebas unitarias para AddressServiceImpl
 * Valida la lógica de negocio del servicio de direcciones
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AddressServiceImpl Unit Tests")
class AddressServiceImplTest {

	@Mock
	private AddressRepository addressRepository;

	@InjectMocks
	private AddressServiceImpl addressService;

	private Address testAddress;
	private AddressDto testAddressDto;

	@BeforeEach
	void setUp() {
		// Configurar datos de prueba
		Credential credential = new Credential();
		credential.setCredentialId(1);
		credential.setUsername("johndoe");
		
		User testUser = new User();
		testUser.setUserId(1);
		testUser.setFirstName("John");
		testUser.setLastName("Doe");
		testUser.setEmail("john@example.com");
		testUser.setCredential(credential);
		
		testAddress = new Address();
		testAddress.setAddressId(1);
		testAddress.setFullAddress("123 Main St");
		testAddress.setCity("New York");
		testAddress.setPostalCode("10001");
		testAddress.setUser(testUser);

		CredentialDto credentialDto = new CredentialDto();
		credentialDto.setCredentialId(1);
		credentialDto.setUsername("johndoe");
		
		UserDto userDto = new UserDto();
		userDto.setUserId(1);
		userDto.setFirstName("John");
		userDto.setLastName("Doe");
		userDto.setEmail("john@example.com");
		userDto.setCredentialDto(credentialDto);
		
		testAddressDto = new AddressDto();
		testAddressDto.setAddressId(1);
		testAddressDto.setFullAddress("123 Main St");
		testAddressDto.setCity("New York");
		testAddressDto.setPostalCode("10001");
		testAddressDto.setUserDto(userDto);
	}

	/**
	 * Prueba 1: Validar que findById retorna una dirección existente
	 */
	@Test
	@DisplayName("Debe encontrar una dirección por ID correctamente")
	void testFindByIdSuccess() {
		// Arrange
		Integer addressId = 1;
		when(addressRepository.findById(addressId)).thenReturn(Optional.of(testAddress));

		// Act
		AddressDto result = addressService.findById(addressId);

		// Assert
		assertNotNull(result);
		verify(addressRepository, times(1)).findById(addressId);
	}

	/**
	 * Prueba 2: Validar que findById lanza excepción cuando dirección no existe
	 */
	@Test
	@DisplayName("Debe lanzar excepción cuando dirección no existe")
	void testFindByIdNotFound() {
		// Arrange
		Integer addressId = 999;
		when(addressRepository.findById(addressId)).thenReturn(Optional.empty());

		// Act & Assert
		assertThrows(AddressNotFoundException.class, () -> {
			addressService.findById(addressId);
		});
		verify(addressRepository, times(1)).findById(addressId);
	}

	/**
	 * Prueba 3: Validar que save guarda una nueva dirección correctamente
	 */
	@Test
	@DisplayName("Debe guardar una nueva dirección correctamente")
	void testSaveAddressSuccess() {
		// Arrange
		when(addressRepository.save(any(Address.class))).thenReturn(testAddress);

		// Act
		AddressDto result = addressService.save(testAddressDto);

		// Assert
		assertNotNull(result);
		verify(addressRepository, times(1)).save(any(Address.class));
	}

	/**
	 * Prueba 4: Validar que deleteById elimina una dirección
	 */
	@Test
	@DisplayName("Debe eliminar una dirección por ID correctamente")
	void testDeleteByIdSuccess() {
		// Arrange
		Integer addressId = 1;
		doNothing().when(addressRepository).deleteById(addressId);

		// Act
		addressService.deleteById(addressId);

		// Assert
		verify(addressRepository, times(1)).deleteById(addressId);
	}

	/**
	 * Prueba 5: Validar que findAll retorna lista de direcciones
	 */
	@Test
	@DisplayName("Debe retornar lista de todas las direcciones")
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
		
		Address address2 = new Address();
		address2.setAddressId(2);
		address2.setFullAddress("456 Oak Ave");
		address2.setCity("Los Angeles");
		address2.setPostalCode("90001");
		address2.setUser(user2);

		when(addressRepository.findAll()).thenReturn(List.of(testAddress, address2));

		// Act
		List<AddressDto> result = addressService.findAll();

		// Assert
		assertNotNull(result);
		assertTrue(result.size() >= 2);
		verify(addressRepository, times(1)).findAll();
	}

	/**
	 * Prueba adicional: Validar que update actualiza una dirección
	 */
	@Test
	@DisplayName("Debe actualizar una dirección correctamente")
	void testUpdateAddressSuccess() {
		// Arrange
		testAddressDto.setCity("Boston");
		
		when(addressRepository.save(any(Address.class))).thenReturn(testAddress);

		// Act
		AddressDto result = addressService.update(testAddressDto);

		// Assert
		assertNotNull(result);
		verify(addressRepository, times(1)).save(any(Address.class));
	}
}
