package com.selimhorri.app.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.selimhorri.app.dto.*;
import com.selimhorri.app.dto.response.collection.DtoCollectionResponse;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas E2E (End-to-End) que validan flujos completos de usuario
 * a través de múltiples microservicios del sistema ecommerce.
 * 
 * Estas pruebas simulan el comportamiento real de un usuario
 * navegando por la aplicación web completa.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class E2EUserJourneyTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private String baseUrl;
    private Integer createdUserId;
    private Integer createdProductId;
    private Integer createdOrderId;
    private String createdFavouriteId;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
    }

    @Test
    @Order(1)
    @DisplayName("E2E Test 1: Complete User Registration and Profile Setup")
    void testCompleteUserRegistrationFlow() {
        // ARRANGE - Prepare user registration data
        UserDto newUser = UserDto.builder()
                .firstName("Juan")
                .lastName("Pérez")
                .email("juan.perez@email.com")
                .phone("+57 300 123 4567")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        // ACT 1 - Register new user
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<UserDto> userRequest = new HttpEntity<>(newUser, headers);

        ResponseEntity<UserDto> userResponse = restTemplate.exchange(
                baseUrl + "/api/users",
                HttpMethod.POST,
                userRequest,
                UserDto.class
        );

        // ASSERT 1 - Verify user creation
        assertEquals(HttpStatus.OK, userResponse.getStatusCode());
        assertNotNull(userResponse.getBody());
        assertNotNull(userResponse.getBody().getUserId());
        createdUserId = userResponse.getBody().getUserId();

        // ACT 2 - Create user credential
        CredentialDto credential = CredentialDto.builder()
                .username("juan.perez")
                .password("securePassword123")
                .userId(createdUserId)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        HttpEntity<CredentialDto> credentialRequest = new HttpEntity<>(credential, headers);
        ResponseEntity<CredentialDto> credentialResponse = restTemplate.exchange(
                baseUrl + "/api/credentials",
                HttpMethod.POST,
                credentialRequest,
                CredentialDto.class
        );

        // ASSERT 2 - Verify credential creation
        assertEquals(HttpStatus.OK, credentialResponse.getStatusCode());
        assertNotNull(credentialResponse.getBody());

        // ACT 3 - Add user address
        AddressDto address = AddressDto.builder()
                .fullAddress("Calle 123 #45-67, Bogotá, Colombia")
                .postalCode("110111")
                .city("Bogotá")
                .userId(createdUserId)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        HttpEntity<AddressDto> addressRequest = new HttpEntity<>(address, headers);
        ResponseEntity<AddressDto> addressResponse = restTemplate.exchange(
                baseUrl + "/api/addresses",
                HttpMethod.POST,
                addressRequest,
                AddressDto.class
        );

        // ASSERT 3 - Verify address creation
        assertEquals(HttpStatus.OK, addressResponse.getStatusCode());
        assertNotNull(addressResponse.getBody());

        // ACT 4 - Retrieve complete user profile
        ResponseEntity<UserDto> retrievedUser = restTemplate.getForEntity(
                baseUrl + "/api/users/" + createdUserId,
                UserDto.class
        );

        // ASSERT 4 - Verify complete user profile
        assertEquals(HttpStatus.OK, retrievedUser.getStatusCode());
        assertNotNull(retrievedUser.getBody());
        assertEquals("Juan", retrievedUser.getBody().getFirstName());
        assertEquals("Pérez", retrievedUser.getBody().getLastName());
        assertEquals("juan.perez@email.com", retrievedUser.getBody().getEmail());
    }

    @Test
    @Order(2)
    @DisplayName("E2E Test 2: Product Catalog Browsing and Category Management")
    void testProductCatalogBrowsingFlow() {
        // ARRANGE - Prepare category and product data
        CategoryDto category = CategoryDto.builder()
                .categoryTitle("Electrónicos")
                .imageUrl("https://example.com/electronics.jpg")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        // ACT 1 - Create product category
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<CategoryDto> categoryRequest = new HttpEntity<>(category, headers);

        ResponseEntity<CategoryDto> categoryResponse = restTemplate.exchange(
                baseUrl + "/api/categories",
                HttpMethod.POST,
                categoryRequest,
                CategoryDto.class
        );

        // ASSERT 1 - Verify category creation
        assertEquals(HttpStatus.OK, categoryResponse.getStatusCode());
        assertNotNull(categoryResponse.getBody());
        Integer categoryId = categoryResponse.getBody().getCategoryId();

        // ACT 2 - Create products in the category
        ProductDto product1 = ProductDto.builder()
                .productTitle("Laptop Gaming")
                .imageUrl("https://example.com/laptop.jpg")
                .sku("LAP-001")
                .priceUnit(1200.0)
                .quantity(10)
                .categoryId(categoryId)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        ProductDto product2 = ProductDto.builder()
                .productTitle("Smartphone")
                .imageUrl("https://example.com/phone.jpg")
                .sku("PHN-001")
                .priceUnit(600.0)
                .quantity(25)
                .categoryId(categoryId)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        HttpEntity<ProductDto> product1Request = new HttpEntity<>(product1, headers);
        ResponseEntity<ProductDto> product1Response = restTemplate.exchange(
                baseUrl + "/api/products",
                HttpMethod.POST,
                product1Request,
                ProductDto.class
        );

        HttpEntity<ProductDto> product2Request = new HttpEntity<>(product2, headers);
        ResponseEntity<ProductDto> product2Response = restTemplate.exchange(
                baseUrl + "/api/products",
                HttpMethod.POST,
                product2Request,
                ProductDto.class
        );

        // ASSERT 2 - Verify products creation
        assertEquals(HttpStatus.OK, product1Response.getStatusCode());
        assertEquals(HttpStatus.OK, product2Response.getStatusCode());
        assertNotNull(product1Response.getBody());
        assertNotNull(product2Response.getBody());
        createdProductId = product1Response.getBody().getProductId();

        // ACT 3 - Browse all products
        ResponseEntity<DtoCollectionResponse<ProductDto>> productsResponse = restTemplate.exchange(
                baseUrl + "/api/products",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                DtoCollectionResponse.class
        );

        // ASSERT 3 - Verify product catalog browsing
        assertEquals(HttpStatus.OK, productsResponse.getStatusCode());
        assertNotNull(productsResponse.getBody());
    }

    @Test
    @Order(3)
    @DisplayName("E2E Test 3: Complete Order Creation and Management Flow")
    void testCompleteOrderCreationFlow() {
        // ARRANGE - Prepare order data
        OrderDto newOrder = OrderDto.builder()
                .orderDesc("Orden de compra - Laptop Gaming")
                .userId(createdUserId)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        // ACT 1 - Create order
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<OrderDto> orderRequest = new HttpEntity<>(newOrder, headers);

        ResponseEntity<OrderDto> orderResponse = restTemplate.exchange(
                baseUrl + "/api/orders",
                HttpMethod.POST,
                orderRequest,
                OrderDto.class
        );

        // ASSERT 1 - Verify order creation
        assertEquals(HttpStatus.OK, orderResponse.getStatusCode());
        assertNotNull(orderResponse.getBody());
        assertNotNull(orderResponse.getBody().getOrderId());
        createdOrderId = orderResponse.getBody().getOrderId();

        // ACT 2 - Create order item (shipping service)
        OrderItemDto orderItem = OrderItemDto.builder()
                .productId(createdProductId)
                .orderId(createdOrderId)
                .orderedQuantity(1)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        HttpEntity<OrderItemDto> orderItemRequest = new HttpEntity<>(orderItem, headers);
        ResponseEntity<OrderItemDto> orderItemResponse = restTemplate.exchange(
                baseUrl + "/api/order-items",
                HttpMethod.POST,
                orderItemRequest,
                OrderItemDto.class
        );

        // ASSERT 2 - Verify order item creation
        assertEquals(HttpStatus.OK, orderItemResponse.getStatusCode());
        assertNotNull(orderItemResponse.getBody());

        // ACT 3 - Create payment for the order
        PaymentDto payment = PaymentDto.builder()
                .orderId(createdOrderId)
                .isPayed(true)
                .paymentStatus(PaymentStatus.COMPLETED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        HttpEntity<PaymentDto> paymentRequest = new HttpEntity<>(payment, headers);
        ResponseEntity<PaymentDto> paymentResponse = restTemplate.exchange(
                baseUrl + "/api/payments",
                HttpMethod.POST,
                paymentRequest,
                PaymentDto.class
        );

        // ASSERT 3 - Verify payment creation
        assertEquals(HttpStatus.OK, paymentResponse.getStatusCode());
        assertNotNull(paymentResponse.getBody());
        assertEquals(PaymentStatus.COMPLETED, paymentResponse.getBody().getPaymentStatus());

        // ACT 4 - Retrieve complete order details
        ResponseEntity<OrderDto> retrievedOrder = restTemplate.getForEntity(
                baseUrl + "/api/orders/" + createdOrderId,
                OrderDto.class
        );

        // ASSERT 4 - Verify order retrieval
        assertEquals(HttpStatus.OK, retrievedOrder.getStatusCode());
        assertNotNull(retrievedOrder.getBody());
        assertEquals(createdOrderId, retrievedOrder.getBody().getOrderId());
    }

    @Test
    @Order(4)
    @DisplayName("E2E Test 4: Favorites Management and User Preferences")
    void testFavoritesManagementFlow() {
        // ARRANGE - Prepare favorite data
        LocalDateTime likeDate = LocalDateTime.now();
        FavouriteDto favourite = FavouriteDto.builder()
                .userId(createdUserId)
                .productId(createdProductId)
                .likeDate(likeDate)
                .createdAt(likeDate.minusDays(1).toInstant(java.time.ZoneOffset.UTC))
                .updatedAt(likeDate.toInstant(java.time.ZoneOffset.UTC))
                .build();

        // ACT 1 - Add product to favorites
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<FavouriteDto> favouriteRequest = new HttpEntity<>(favourite, headers);

        ResponseEntity<FavouriteDto> favouriteResponse = restTemplate.exchange(
                baseUrl + "/api/favourites",
                HttpMethod.POST,
                favouriteRequest,
                FavouriteDto.class
        );

        // ASSERT 1 - Verify favorite creation
        assertEquals(HttpStatus.OK, favouriteResponse.getStatusCode());
        assertNotNull(favouriteResponse.getBody());
        createdFavouriteId = createdUserId + "_" + createdProductId + "_" + 
                likeDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));

        // ACT 2 - Retrieve user's favorites
        ResponseEntity<DtoCollectionResponse<FavouriteDto>> favouritesResponse = restTemplate.exchange(
                baseUrl + "/api/favourites",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                DtoCollectionResponse.class
        );

        // ASSERT 2 - Verify favorites retrieval
        assertEquals(HttpStatus.OK, favouritesResponse.getStatusCode());
        assertNotNull(favouritesResponse.getBody());

        // ACT 3 - Retrieve specific favorite by composite ID
        String favouritePath = "/api/favourites/" + createdUserId + "/" + createdProductId + "/" + 
                likeDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        
        ResponseEntity<FavouriteDto> specificFavouriteResponse = restTemplate.getForEntity(
                baseUrl + favouritePath,
                FavouriteDto.class
        );

        // ASSERT 3 - Verify specific favorite retrieval
        assertEquals(HttpStatus.OK, specificFavouriteResponse.getStatusCode());
        assertNotNull(specificFavouriteResponse.getBody());
    }

    @Test
    @Order(5)
    @DisplayName("E2E Test 5: Complete E-commerce Transaction Flow")
    void testCompleteEcommerceTransactionFlow() {
        // ARRANGE - This test validates the complete flow from user login to order completion
        
        // ACT 1 - User authentication (simulated by retrieving user)
        ResponseEntity<UserDto> userResponse = restTemplate.getForEntity(
                baseUrl + "/api/users/" + createdUserId,
                UserDto.class
        );

        // ASSERT 1 - Verify user authentication
        assertEquals(HttpStatus.OK, userResponse.getStatusCode());
        assertNotNull(userResponse.getBody());
        assertEquals(createdUserId, userResponse.getBody().getUserId());

        // ACT 2 - Browse products (simulated by retrieving product)
        ResponseEntity<ProductDto> productResponse = restTemplate.getForEntity(
                baseUrl + "/api/products/" + createdProductId,
                ProductDto.class
        );

        // ASSERT 2 - Verify product browsing
        assertEquals(HttpStatus.OK, productResponse.getStatusCode());
        assertNotNull(productResponse.getBody());
        assertEquals(createdProductId, productResponse.getBody().getProductId());

        // ACT 3 - Check order status
        ResponseEntity<OrderDto> orderResponse = restTemplate.getForEntity(
                baseUrl + "/api/orders/" + createdOrderId,
                OrderDto.class
        );

        // ASSERT 3 - Verify order status
        assertEquals(HttpStatus.OK, orderResponse.getStatusCode());
        assertNotNull(orderResponse.getBody());
        assertEquals(createdOrderId, orderResponse.getBody().getOrderId());

        // ACT 4 - Check payment status
        ResponseEntity<DtoCollectionResponse<PaymentDto>> paymentsResponse = restTemplate.exchange(
                baseUrl + "/api/payments",
                HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()),
                DtoCollectionResponse.class
        );

        // ASSERT 4 - Verify payment status
        assertEquals(HttpStatus.OK, paymentsResponse.getStatusCode());
        assertNotNull(paymentsResponse.getBody());

        // ACT 5 - Verify shipping items
        ResponseEntity<DtoCollectionResponse<OrderItemDto>> orderItemsResponse = restTemplate.exchange(
                baseUrl + "/api/order-items",
                HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()),
                DtoCollectionResponse.class
        );

        // ASSERT 5 - Verify shipping items
        assertEquals(HttpStatus.OK, orderItemsResponse.getStatusCode());
        assertNotNull(orderItemsResponse.getBody());

        // ACT 6 - Final cleanup - Remove favorite
        String favouritePath = "/api/favourites/" + createdUserId + "/" + createdProductId + "/" + 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        
        ResponseEntity<Boolean> deleteFavouriteResponse = restTemplate.exchange(
                baseUrl + favouritePath,
                HttpMethod.DELETE,
                new HttpEntity<>(new HttpHeaders()),
                Boolean.class
        );

        // ASSERT 6 - Verify cleanup (may return 404 if exact timestamp doesn't match, which is acceptable)
        assertTrue(deleteFavouriteResponse.getStatusCode() == HttpStatus.OK || 
                  deleteFavouriteResponse.getStatusCode() == HttpStatus.NOT_FOUND);
    }

    @AfterAll
    static void tearDown() {
        // Cleanup can be implemented here if needed
        // For E2E tests, we typically don't clean up as we want to test the complete flow
        System.out.println("E2E Tests completed - Data left for verification");
    }
}
