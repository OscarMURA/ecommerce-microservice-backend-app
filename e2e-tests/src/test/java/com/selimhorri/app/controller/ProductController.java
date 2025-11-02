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

import com.selimhorri.app.dto.ProductDto;
import com.selimhorri.app.dto.response.collection.DtoCollectionResponse;
import com.selimhorri.app.service.TestDataStore;

@RestController
@RequestMapping("/api/products")
@Profile("!cluster")  // Only active when NOT in cluster mode
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
