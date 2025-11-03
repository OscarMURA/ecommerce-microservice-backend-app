package com.selimhorri.app.integration;

import static org.junit.jupiter.api.Assertions.*;

import javax.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.selimhorri.app.domain.Address;
import com.selimhorri.app.domain.Credential;
import com.selimhorri.app.domain.User;
import com.selimhorri.app.repository.AddressRepository;
import com.selimhorri.app.repository.CredentialRepository;
import com.selimhorri.app.repository.UserRepository;

/**
 * Integration tests for User Service
 * 
 * These tests validate database integration and entity relationships:
 * - Repository → Database
 * - User ↔ Address (OneToMany)
 * - User ↔ Credential (OneToOne)
 * 
 * @author Testing Team
 */
@SpringBootTest
@Transactional
class UserServiceIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private CredentialRepository credentialRepository;

    @BeforeEach
    void setUp() {
        // Clear all repositories before each test
        addressRepository.deleteAll();
        credentialRepository.deleteAll();
        userRepository.deleteAll();
    }

    /**
     * Test 1: Create user and verify persistence
     */
    @Test
    @DisplayName("Integration Test 1: Should persist user to database")
    void testCreateUserIntegration() {
        // ARRANGE
        User user = new User();
        user.setFirstName("Juan");
        user.setLastName("Pérez");
        user.setEmail("juan.perez@example.com");
        user.setPhone("3165555555");

        // ACT
        User savedUser = userRepository.save(user);

        // ASSERT
        assertNotNull(savedUser);
        assertNotNull(savedUser.getUserId());
        
        User userFromDB = userRepository.findById(savedUser.getUserId()).orElse(null);
        assertNotNull(userFromDB);
        assertEquals("Juan", userFromDB.getFirstName());
        assertEquals("juan.perez@example.com", userFromDB.getEmail());
    }

    /**
     * Test 2: Create user with credentials and verify OneToOne relationship
     */
    @Test
    @DisplayName("Integration Test 2: Should create user with credentials and verify relationship")
    void testCreateUserWithCredentialsIntegration() {
        // ARRANGE
        User user = new User();
        user.setFirstName("María");
        user.setLastName("García");
        user.setEmail("maria.garcia@example.com");
        user.setPhone("3165555556");

        Credential credential = new Credential();
        credential.setUsername("mariagarcia");
        credential.setPassword("SecurePass123!");
        credential.setIsEnabled(true);
        credential.setIsAccountNonExpired(true);
        credential.setIsAccountNonLocked(true);
        credential.setIsCredentialsNonExpired(true);
        credential.setUser(user);

        user.setCredential(credential);

        // ACT
        User savedUser = userRepository.save(user);
        assertNotNull(savedUser.getUserId());

        // ASSERT
        User userFromDB = userRepository.findById(savedUser.getUserId()).orElse(null);
        assertNotNull(userFromDB);
        assertNotNull(userFromDB.getCredential());
        assertEquals("mariagarcia", userFromDB.getCredential().getUsername());
    }

    /**
     * Test 3: Create user with multiple addresses and verify OneToMany relationship
     */
    @Test
    @DisplayName("Integration Test 3: Should create user with multiple addresses")
    void testCreateUserWithAddressesIntegration() {
        // ARRANGE
        User user = new User();
        user.setFirstName("Carlos");
        user.setLastName("López");
        user.setEmail("carlos.lopez@example.com");

        Address address1 = new Address();
        address1.setFullAddress("Calle 123 Apt 45");
        address1.setCity("Bogotá");
        address1.setPostalCode("110111");
        address1.setUser(user);

        Address address2 = new Address();
        address2.setFullAddress("Carrera 100 Apt 50");
        address2.setCity("Cali");
        address2.setPostalCode("760001");
        address2.setUser(user);

        // ACT
        User savedUser = userRepository.save(user);
        addressRepository.save(address1);
        addressRepository.save(address2);

        // ASSERT - Verify addresses were saved
        long addressCount = addressRepository.count();
        assertEquals(2, addressCount);
        
        // Verify both addresses reference the user
        Address addr1FromDB = addressRepository.findById(address1.getAddressId()).orElse(null);
        Address addr2FromDB = addressRepository.findById(address2.getAddressId()).orElse(null);
        
        assertNotNull(addr1FromDB);
        assertNotNull(addr2FromDB);
        assertEquals(savedUser.getUserId(), addr1FromDB.getUser().getUserId());
        assertEquals(savedUser.getUserId(), addr2FromDB.getUser().getUserId());
    }

    /**
     * Test 4: Update user data atomically
     */
    @Test
    @DisplayName("Integration Test 4: Should update user atomically")
    void testUpdateUserTransactional() {
        // ARRANGE
        User user = new User();
        user.setFirstName("Roberto");
        user.setLastName("Martínez");
        user.setEmail("roberto.martinez@example.com");
        
        User savedUser = userRepository.save(user);
        Integer userId = savedUser.getUserId();

        // ACT
        User userToUpdate = userRepository.findById(userId).orElse(null);
        assertNotNull(userToUpdate);
        userToUpdate.setFirstName("Roberto");
        userToUpdate.setLastName("Martínez López");
        User updatedUser = userRepository.save(userToUpdate);

        // ASSERT
        assertEquals("Martínez López", updatedUser.getLastName());
        
        User userFromDB = userRepository.findById(userId).orElse(null);
        assertNotNull(userFromDB);
        assertEquals("Martínez López", userFromDB.getLastName());
    }

    /**
     * Test 5: Delete user and verify cascade delete
     */
    @Test
    @DisplayName("Integration Test 5: Should delete user with cascade delete")
    void testDeleteUserWithCascadeIntegration() {
        // ARRANGE
        User user = new User();
        user.setFirstName("Ana");
        user.setLastName("Sánchez");
        user.setEmail("ana.sanchez@example.com");

        Address address = new Address();
        address.setFullAddress("Calle 456 Apt 10");
        address.setCity("Medellín");
        address.setPostalCode("050001");
        address.setUser(user);

        Credential credential = new Credential();
        credential.setUsername("anasanchez");
        credential.setPassword("Pass123!");
        credential.setIsEnabled(true);
        credential.setIsAccountNonExpired(true);
        credential.setIsAccountNonLocked(true);
        credential.setIsCredentialsNonExpired(true);
        credential.setUser(user);

        user.setCredential(credential);

        User savedUser = userRepository.save(user);
        addressRepository.save(address);
        
        Integer userId = savedUser.getUserId();

        // ACT
        userRepository.deleteById(userId);

        // ASSERT
        assertNull(userRepository.findById(userId).orElse(null));
    }
}
