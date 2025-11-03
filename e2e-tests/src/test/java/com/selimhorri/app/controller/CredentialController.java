package com.selimhorri.app.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.selimhorri.app.dto.CredentialDto;
import com.selimhorri.app.service.TestDataStore;

@RestController
@RequestMapping("/api/credentials")
@Profile("!cluster")  // Only active when NOT in cluster mode
public class CredentialController {
    
    @Autowired
    private TestDataStore dataStore;
    
    @PostMapping
    public ResponseEntity<CredentialDto> createCredential(@RequestBody CredentialDto credentialDto) {
        credentialDto.setCredentialId(dataStore.generateCredentialId());
        return ResponseEntity.ok(credentialDto);
    }
}
