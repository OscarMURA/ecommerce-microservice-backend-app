package com.selimhorri.app.controller;

import com.selimhorri.app.dto.ProductDto;
import com.selimhorri.app.dto.response.collection.DtoCollectionResponse;
import com.selimhorri.app.service.TestDataStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    
    @Autowired
    private TestDataStore dataStore;
    
    @PostMapping
    public ResponseEntity<ProductDto> createProduct(@RequestBody ProductDto productDto) {
        productDto.setProductId(dataStore.generateProductId());
        dataStore.addProduct(productDto);
        return ResponseEntity.ok(productDto);
    }
    
    @GetMapping
    public ResponseEntity<DtoCollectionResponse<ProductDto>> getAllProducts() {
        DtoCollectionResponse<ProductDto> response = DtoCollectionResponse.<ProductDto>builder()
                .collection(dataStore.getAllProducts())
                .build();
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<ProductDto> getProduct(@PathVariable Integer id) {
        ProductDto product = dataStore.getProduct(id);
        if (product == null) {
            product = ProductDto.builder().productId(id).build();
        }
        return ResponseEntity.ok(product);
    }
}
