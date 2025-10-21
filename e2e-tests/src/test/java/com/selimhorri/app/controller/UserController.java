package com.selimhorri.app.controller;

import com.selimhorri.app.dto.UserDto;
import com.selimhorri.app.service.TestDataStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
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
