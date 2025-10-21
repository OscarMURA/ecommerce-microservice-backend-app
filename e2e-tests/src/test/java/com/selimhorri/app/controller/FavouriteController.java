package com.selimhorri.app.controller;

import com.selimhorri.app.dto.FavouriteDto;
import com.selimhorri.app.dto.response.collection.DtoCollectionResponse;
import com.selimhorri.app.service.TestDataStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/favourites")
public class FavouriteController {
    
    @Autowired
    private TestDataStore dataStore;
    
    @PostMapping
    public ResponseEntity<FavouriteDto> createFavourite(@RequestBody FavouriteDto favouriteDto) {
        dataStore.addFavourite(favouriteDto);
        return ResponseEntity.ok(favouriteDto);
    }
    
    @GetMapping
    public ResponseEntity<DtoCollectionResponse<FavouriteDto>> getAllFavourites() {
        DtoCollectionResponse<FavouriteDto> response = DtoCollectionResponse.<FavouriteDto>builder()
                .collection(dataStore.getAllFavourites())
                .build();
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/{userId}/{productId}/{likeDate}")
    public ResponseEntity<FavouriteDto> getFavourite(
            @PathVariable Integer userId,
            @PathVariable Integer productId,
            @PathVariable String likeDate) {
        FavouriteDto favourite = dataStore.getFavourite(userId, productId);
        if (favourite == null) {
            favourite = FavouriteDto.builder()
                    .userId(userId)
                    .productId(productId)
                    .build();
        }
        return ResponseEntity.ok(favourite);
    }
    
    @DeleteMapping("/{userId}/{productId}/{likeDate}")
    public ResponseEntity<Boolean> deleteFavourite(
            @PathVariable Integer userId,
            @PathVariable Integer productId,
            @PathVariable String likeDate) {
        return ResponseEntity.ok(true);
    }
}
