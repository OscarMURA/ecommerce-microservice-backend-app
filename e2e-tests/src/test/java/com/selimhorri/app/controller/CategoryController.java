package com.selimhorri.app.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.selimhorri.app.dto.CategoryDto;
import com.selimhorri.app.service.TestDataStore;

@RestController
@RequestMapping("/api/categories")
@Profile("!cluster")  // Only active when NOT in cluster mode
public class CategoryController {
    
    @Autowired
    private TestDataStore dataStore;
    
    @PostMapping
    public ResponseEntity<CategoryDto> createCategory(@RequestBody CategoryDto categoryDto) {
        categoryDto.setCategoryId(dataStore.generateCategoryId());
        return ResponseEntity.ok(categoryDto);
    }
}
