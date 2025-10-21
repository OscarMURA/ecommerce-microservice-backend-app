package com.selimhorri.app.controller;

import com.selimhorri.app.dto.AddressDto;
import com.selimhorri.app.service.TestDataStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/addresses")
public class AddressController {
    
    @Autowired
    private TestDataStore dataStore;
    
    @PostMapping
    public ResponseEntity<AddressDto> createAddress(@RequestBody AddressDto addressDto) {
        addressDto.setAddressId(dataStore.generateAddressId());
        return ResponseEntity.ok(addressDto);
    }
}
