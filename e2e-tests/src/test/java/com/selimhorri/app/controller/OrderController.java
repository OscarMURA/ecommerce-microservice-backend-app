package com.selimhorri.app.controller;

import com.selimhorri.app.dto.OrderDto;
import com.selimhorri.app.service.TestDataStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    
    @Autowired
    private TestDataStore dataStore;
    
    @PostMapping
    public ResponseEntity<OrderDto> createOrder(@RequestBody OrderDto orderDto) {
        orderDto.setOrderId(dataStore.generateOrderId());
        dataStore.addOrder(orderDto);
        return ResponseEntity.ok(orderDto);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<OrderDto> getOrder(@PathVariable Integer id) {
        OrderDto order = dataStore.getOrder(id);
        if (order == null) {
            order = OrderDto.builder()
                    .orderId(id)
                    .orderDesc("Test Order")
                    .build();
        }
        return ResponseEntity.ok(order);
    }
}
