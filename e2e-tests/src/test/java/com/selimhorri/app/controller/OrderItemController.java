package com.selimhorri.app.controller;

import com.selimhorri.app.dto.OrderItemDto;
import com.selimhorri.app.dto.response.collection.DtoCollectionResponse;
import com.selimhorri.app.service.TestDataStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/order-items")
public class OrderItemController {
    
    @Autowired
    private TestDataStore dataStore;
    
    @PostMapping
    public ResponseEntity<OrderItemDto> createOrderItem(@RequestBody OrderItemDto orderItemDto) {
        dataStore.addOrderItem(orderItemDto);
        return ResponseEntity.ok(orderItemDto);
    }
    
    @GetMapping
    public ResponseEntity<DtoCollectionResponse<OrderItemDto>> getAllOrderItems() {
        DtoCollectionResponse<OrderItemDto> response = DtoCollectionResponse.<OrderItemDto>builder()
                .collection(dataStore.getAllOrderItems())
                .build();
        return ResponseEntity.ok(response);
    }
}
