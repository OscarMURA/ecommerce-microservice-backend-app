package com.selimhorri.app.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.selimhorri.app.dto.UserDto;
import com.selimhorri.app.service.TestDataStore;

@RestController
@RequestMapping("/api/users")
@Profile("!cluster")  // Only active when NOT in cluster mode
public class UserController {
    
    @Autowired
    private TestDataStore dataStore;
    
    @PostMapping
    public ResponseEntity<UserDto> createUser(@RequestBody UserDto userDto) {
        userDto.setUserId(dataStore.generateUserId());
        dataStore.addUser(userDto);
        return ResponseEntity.ok(userDto);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getUser(@PathVariable Integer id) {
        UserDto user = dataStore.getUser(id);
        if (user == null) {
            user = UserDto.builder()
                    .userId(id)
                    .firstName("Juan")
                    .lastName("Pérez")
                    .email("juan.perez@email.com")
                    .phone("+57 300 123 4567")
                    .build();
        }
        return ResponseEntity.ok(user);
    }
}
