package com.selimhorri.app.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.selimhorri.app.dto.PaymentDto;
import com.selimhorri.app.dto.response.collection.DtoCollectionResponse;
import com.selimhorri.app.service.TestDataStore;

@RestController
@RequestMapping("/api/payments")
@Profile("!cluster")  // Only active when NOT in cluster mode
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
