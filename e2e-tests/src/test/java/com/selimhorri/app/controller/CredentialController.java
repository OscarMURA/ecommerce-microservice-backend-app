package com.selimhorri.app.controller;

import com.selimhorri.app.dto.CredentialDto;
import com.selimhorri.app.service.TestDataStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/credentials")
public class CredentialController {
    
    @Autowired
    private TestDataStore dataStore;
    
    @PostMapping
    public ResponseEntity<CredentialDto> createCredential(@RequestBody CredentialDto credentialDto) {
        credentialDto.setCredentialId(dataStore.generateCredentialId());
        return ResponseEntity.ok(credentialDto);
    }
}
