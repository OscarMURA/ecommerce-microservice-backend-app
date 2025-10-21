package com.selimhorri.app.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;

import javax.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.selimhorri.app.domain.Favourite;
import com.selimhorri.app.domain.id.FavouriteId;
import com.selimhorri.app.repository.FavouriteRepository;

/**
 * Integration tests for Favourite Service inter-service communication
 * 
 * These tests validate:
 * - Favourite creation and retrieval with composite keys
 * - Service integration patterns
 * - Transaction boundaries
 * - Direct repository communication
 * - Composite key handling
 * 
 * @author Testing Team
 * @version 1.0
 */
@SpringBootTest
@Transactional
class FavouriteServiceIntegrationTest {

    @Autowired
    private FavouriteRepository favouriteRepository;

    @BeforeEach
    void setUp() {
        favouriteRepository.deleteAll();
    }

    @Test
    @DisplayName("Test 1: Create Favourite with Composite Key")
    void testCreateFavourite() {
        // ARRANGE - Setup test data
        LocalDateTime likeDate = LocalDateTime.now();
        Favourite favourite = Favourite.builder()
                .userId(1)
                .productId(100)
                .likeDate(likeDate)
                .build();

        // ACT - Execute operation
        Favourite savedFavourite = favouriteRepository.save(favourite);

        // ASSERT - Verify results
        assertNotNull(savedFavourite);
        assertEquals(1, savedFavourite.getUserId());
        assertEquals(100, savedFavourite.getProductId());
        assertEquals(likeDate, savedFavourite.getLikeDate());
        assertEquals(1, favouriteRepository.count());
    }

    @Test
    @DisplayName("Test 2: Retrieve Favourite by Composite ID")
    void testRetrieveFavouriteById() {
        // ARRANGE - Create and save favourite
        LocalDateTime likeDate = LocalDateTime.now();
        Favourite favourite = Favourite.builder()
                .userId(2)
                .productId(200)
                .likeDate(likeDate)
                .build();
        
        favouriteRepository.save(favourite);
        FavouriteId favouriteId = new FavouriteId(2, 200, likeDate);

        // ACT - Retrieve by composite ID
        Favourite retrievedFavourite = favouriteRepository.findById(favouriteId).orElse(null);

        // ASSERT - Verify retrieval
        assertNotNull(retrievedFavourite);
        assertEquals(2, retrievedFavourite.getUserId());
        assertEquals(200, retrievedFavourite.getProductId());
        assertEquals(likeDate, retrievedFavourite.getLikeDate());
    }

    @Test
    @DisplayName("Test 3: Update Favourite Like Date")
    void testUpdateFavourite() {
        // ARRANGE - Create and save favourite
        LocalDateTime originalDate = LocalDateTime.now().minusDays(1);
        Favourite favourite = Favourite.builder()
                .userId(3)
                .productId(300)
                .likeDate(originalDate)
                .build();
        
        Favourite savedFavourite = favouriteRepository.save(favourite);

        // ACT - Update like date
        LocalDateTime newDate = LocalDateTime.now();
        savedFavourite.setLikeDate(newDate);
        
        Favourite updatedFavourite = favouriteRepository.save(savedFavourite);

        // ASSERT - Verify update
        assertEquals(newDate, updatedFavourite.getLikeDate());
        assertEquals(3, updatedFavourite.getUserId());
        assertEquals(300, updatedFavourite.getProductId());
        assertEquals(1, favouriteRepository.count());
    }

    @Test
    @DisplayName("Test 4: Retrieve All Favourites with Count")
    void testRetrieveAllFavourites() {
        // ARRANGE - Create multiple favourites
        LocalDateTime date1 = LocalDateTime.now().minusDays(2);
        LocalDateTime date2 = LocalDateTime.now().minusDays(1);
        LocalDateTime date3 = LocalDateTime.now();

        Favourite favourite1 = Favourite.builder()
                .userId(10)
                .productId(1000)
                .likeDate(date1)
                .build();
                
        Favourite favourite2 = Favourite.builder()
                .userId(20)
                .productId(2000)
                .likeDate(date2)
                .build();

        Favourite favourite3 = Favourite.builder()
                .userId(30)
                .productId(3000)
                .likeDate(date3)
                .build();

        favouriteRepository.save(favourite1);
        favouriteRepository.save(favourite2);
        favouriteRepository.save(favourite3);

        // ACT - Retrieve all favourites
        List<Favourite> allFavourites = favouriteRepository.findAll();

        // ASSERT - Verify count and content
        assertEquals(3, allFavourites.size());
        assertEquals(3, favouriteRepository.count());
        assertTrue(allFavourites.stream().anyMatch(f -> f.getUserId().equals(10)));
        assertTrue(allFavourites.stream().anyMatch(f -> f.getUserId().equals(20)));
        assertTrue(allFavourites.stream().anyMatch(f -> f.getUserId().equals(30)));
    }

    @Test
    @DisplayName("Test 5: Delete Favourite with Composite Key")
    void testDeleteFavourite() {
        // ARRANGE - Create multiple favourites
        LocalDateTime date1 = LocalDateTime.now().minusDays(1);
        LocalDateTime date2 = LocalDateTime.now();

        Favourite favourite1 = Favourite.builder()
                .userId(40)
                .productId(4000)
                .likeDate(date1)
                .build();
                
        Favourite favourite2 = Favourite.builder()
                .userId(50)
                .productId(5000)
                .likeDate(date2)
                .build();

        favouriteRepository.save(favourite1);
        favouriteRepository.save(favourite2);

        // Verify initial count
        assertEquals(2, favouriteRepository.count());

        // ACT - Delete one favourite using composite key
        FavouriteId favouriteId = new FavouriteId(40, 4000, date1);
        favouriteRepository.deleteById(favouriteId);

        // ASSERT - Verify deletion
        assertEquals(1, favouriteRepository.count());
        assertFalse(favouriteRepository.existsById(favouriteId));
        
        // Verify the other favourite still exists
        FavouriteId remainingId = new FavouriteId(50, 5000, date2);
        assertTrue(favouriteRepository.existsById(remainingId));
    }
}
