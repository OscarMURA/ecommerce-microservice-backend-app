package com.selimhorri.app.controller;

import com.selimhorri.app.dto.CategoryDto;
import com.selimhorri.app.service.TestDataStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {
    
    @Autowired
    private TestDataStore dataStore;
    
    @PostMapping
    public ResponseEntity<CategoryDto> createCategory(@RequestBody CategoryDto categoryDto) {
        categoryDto.setCategoryId(dataStore.generateCategoryId());
        return ResponseEntity.ok(categoryDto);
    }
}
