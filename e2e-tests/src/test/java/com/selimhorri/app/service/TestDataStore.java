package com.selimhorri.app.service;

import com.selimhorri.app.dto.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class TestDataStore {
    
    private final AtomicInteger userIdGenerator = new AtomicInteger(1);
    private final AtomicInteger productIdGenerator = new AtomicInteger(1);
    private final AtomicInteger orderIdGenerator = new AtomicInteger(1);
    private final AtomicInteger paymentIdGenerator = new AtomicInteger(1);
    private final AtomicInteger addressIdGenerator = new AtomicInteger(1);
    private final AtomicInteger credentialIdGenerator = new AtomicInteger(1);
    private final AtomicInteger categoryIdGenerator = new AtomicInteger(1);
    
    private final List<UserDto> users = new ArrayList<>();
    private final List<ProductDto> products = new ArrayList<>();
    private final List<OrderDto> orders = new ArrayList<>();
    private final List<PaymentDto> payments = new ArrayList<>();
    private final List<OrderItemDto> orderItems = new ArrayList<>();
    private final List<FavouriteDto> favourites = new ArrayList<>();
    
    // User methods
    public Integer generateUserId() {
        return userIdGenerator.getAndIncrement();
    }
    
    public void addUser(UserDto user) {
        users.add(user);
    }
    
    public UserDto getUser(Integer id) {
        return users.stream()
                .filter(u -> u.getUserId().equals(id))
                .findFirst()
                .orElse(null);
    }
    
    // Product methods
    public Integer generateProductId() {
        return productIdGenerator.getAndIncrement();
    }
    
    public void addProduct(ProductDto product) {
        products.add(product);
    }
    
    public List<ProductDto> getAllProducts() {
        return new ArrayList<>(products);
    }
    
    public ProductDto getProduct(Integer id) {
        return products.stream()
                .filter(p -> p.getProductId().equals(id))
                .findFirst()
                .orElse(null);
    }
    
    // Order methods
    public Integer generateOrderId() {
        return orderIdGenerator.getAndIncrement();
    }
    
    public void addOrder(OrderDto order) {
        orders.add(order);
    }
    
    public OrderDto getOrder(Integer id) {
        return orders.stream()
                .filter(o -> o.getOrderId().equals(id))
                .findFirst()
                .orElse(null);
    }
    
    // Payment methods
    public Integer generatePaymentId() {
        return paymentIdGenerator.getAndIncrement();
    }
    
    public void addPayment(PaymentDto payment) {
        payments.add(payment);
    }
    
    public List<PaymentDto> getAllPayments() {
        return new ArrayList<>(payments);
    }
    
    // OrderItem methods
    public void addOrderItem(OrderItemDto orderItem) {
        orderItems.add(orderItem);
    }
    
    public List<OrderItemDto> getAllOrderItems() {
        return new ArrayList<>(orderItems);
    }
    
    // Favourite methods
    public void addFavourite(FavouriteDto favourite) {
        favourites.add(favourite);
    }
    
    public List<FavouriteDto> getAllFavourites() {
        return new ArrayList<>(favourites);
    }
    
    public FavouriteDto getFavourite(Integer userId, Integer productId) {
        return favourites.stream()
                .filter(f -> f.getUserId().equals(userId) && f.getProductId().equals(productId))
                .findFirst()
                .orElse(null);
    }
    
    // Other ID generators
    public Integer generateAddressId() {
        return addressIdGenerator.getAndIncrement();
    }
    
    public Integer generateCredentialId() {
        return credentialIdGenerator.getAndIncrement();
    }
    
    public Integer generateCategoryId() {
        return categoryIdGenerator.getAndIncrement();
    }
}
