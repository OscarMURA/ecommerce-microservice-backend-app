package com.selimhorri.app.controller;

import com.selimhorri.app.dto.PaymentDto;
import com.selimhorri.app.dto.response.collection.DtoCollectionResponse;
import com.selimhorri.app.service.TestDataStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    
    @Autowired
    private TestDataStore dataStore;
    
    @PostMapping
    public ResponseEntity<PaymentDto> createPayment(@RequestBody PaymentDto paymentDto) {
        paymentDto.setPaymentId(dataStore.generatePaymentId());
        dataStore.addPayment(paymentDto);
        return ResponseEntity.ok(paymentDto);
    }
    
    @GetMapping
    public ResponseEntity<DtoCollectionResponse<PaymentDto>> getAllPayments() {
        DtoCollectionResponse<PaymentDto> response = DtoCollectionResponse.<PaymentDto>builder()
                .collection(dataStore.getAllPayments())
                .build();
        return ResponseEntity.ok(response);
    }
}
