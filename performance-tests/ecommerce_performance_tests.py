#!/usr/bin/env python3
"""
E-commerce Microservices Performance Tests with Locust

This module contains comprehensive performance and stress tests for the e-commerce
microservices system, covering all major business scenarios.

Test Scenarios:
1. User Management Operations
2. Product Catalog Browsing
3. Order Processing Workflow
4. Payment Processing
5. Favourite Management
6. Mixed Load Scenarios

Metrics Tracked:
- Response Time (avg, min, max, 95th percentile)
- Throughput (requests per second)
- Error Rate
- Resource Utilization
"""

import json
import random
import time
from datetime import datetime
from typing import Dict, List, Optional

from faker import Faker
from locust import HttpUser, between, events, task

fake = Faker()

class EcommerceUser(HttpUser):
    """Base class for e-commerce user behavior simulation"""
    
    wait_time = between(1, 3)  # Wait 1-3 seconds between tasks
    
    def on_start(self):
        """Initialize user session with authentication and data"""
        self.user_id = None
        self.product_ids = []
        self.order_ids = []
        self.favourite_ids = []
        
        # Initialize with some test data
        self._initialize_test_data()
    
    def _initialize_test_data(self):
        """Create initial test data for the user"""
        # Create a test user
        user_data = {
            "firstName": fake.first_name(),
            "lastName": fake.last_name(),
            "email": fake.email(),
            "phone": fake.phone_number(),
            "username": fake.user_name(),
            "password": fake.password()
        }
        
        try:
            response = self.client.post("/app/api/users", json=user_data)
            if response.status_code == 200:
                self.user_id = response.json().get("userId")
                print(f"Created test user: {self.user_id}")
        except Exception as e:
            print(f"Error creating test user: {e}")
            # Use a default user ID for testing
            self.user_id = random.randint(1, 1000)
    
    @task(10)
    def browse_products(self):
        """Simulate product browsing - most common activity"""
        with self.client.get("/app/api/products", catch_response=True) as response:
            if response.status_code == 200:
                products = response.json().get("collection", [])
                if products:
                    # Store some product IDs for later use
                    self.product_ids = [p.get("productId") for p in products[:5]]
                    response.success()
                else:
                    response.failure("No products found")
            else:
                response.failure(f"Failed to fetch products: {response.status_code}")
    
    @task(8)
    def view_product_details(self):
        """View specific product details"""
        if not self.product_ids:
            self.browse_products()
        
        product_id = random.choice(self.product_ids)
        with self.client.get(f"/app/api/products/{product_id}", catch_response=True) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Failed to fetch product {product_id}: {response.status_code}")
    
    @task(6)
    def browse_categories(self):
        """Browse product categories"""
        with self.client.get("/app/api/categories", catch_response=True) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Failed to fetch categories: {response.status_code}")
    
    @task(4)
    def add_to_favourites(self):
        """Add product to favourites"""
        if not self.product_ids:
            self.browse_products()
        
        if not self.user_id:
            return
        
        product_id = random.choice(self.product_ids)
        favourite_data = {
            "userId": self.user_id,
            "productId": product_id,
            "likeDate": datetime.now().isoformat()
        }
        
        with self.client.post("/app/api/favourites", json=favourite_data, catch_response=True) as response:
            if response.status_code == 200:
                self.favourite_ids.append(response.json().get("favouriteId"))
                response.success()
            else:
                response.failure(f"Failed to add favourite: {response.status_code}")
    
    @task(3)
    def view_favourites(self):
        """View user's favourite products"""
        with self.client.get("/app/api/favourites", catch_response=True) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Failed to fetch favourites: {response.status_code}")
    
    @task(2)
    def create_order(self):
        """Create a new order"""
        if not self.product_ids or not self.user_id:
            return
        
        # Create order with random products
        order_items = []
        for _ in range(random.randint(1, 3)):
            product_id = random.choice(self.product_ids)
            order_items.append({
                "productId": product_id,
                "quantity": random.randint(1, 5),
                "price": round(random.uniform(10.0, 100.0), 2)
            })
        
        order_data = {
            "userId": self.user_id,
            "orderDate": datetime.now().isoformat(),
            "totalAmount": sum(item["price"] * item["quantity"] for item in order_items),
            "status": "PENDING",
            "orderItems": order_items
        }
        
        with self.client.post("/app/api/orders", json=order_data, catch_response=True) as response:
            if response.status_code == 200:
                order_id = response.json().get("orderId")
                self.order_ids.append(order_id)
                response.success()
            else:
                response.failure(f"Failed to create order: {response.status_code}")
    
    @task(2)
    def view_orders(self):
        """View user's orders"""
        with self.client.get("/app/api/orders", catch_response=True) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Failed to fetch orders: {response.status_code}")
    
    @task(1)
    def process_payment(self):
        """Process payment for an order"""
        if not self.order_ids:
            return
        
        order_id = random.choice(self.order_ids)
        payment_data = {
            "orderId": order_id,
            "amount": round(random.uniform(50.0, 500.0), 2),
            "paymentMethod": random.choice(["CREDIT_CARD", "DEBIT_CARD", "PAYPAL"]),
            "status": "PENDING",
            "paymentDate": datetime.now().isoformat()
        }
        
        with self.client.post("/app/api/payments", json=payment_data, catch_response=True) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Failed to process payment: {response.status_code}")
    
    @task(1)
    def update_user_profile(self):
        """Update user profile information"""
        if not self.user_id:
            return
        
        update_data = {
            "userId": self.user_id,
            "firstName": fake.first_name(),
            "lastName": fake.last_name(),
            "phone": fake.phone_number()
        }
        
        with self.client.put(f"/app/api/users/{self.user_id}", json=update_data, catch_response=True) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Failed to update user profile: {response.status_code}")


class HeavyUser(EcommerceUser):
    """Heavy user that performs more intensive operations"""
    
    wait_time = between(0.5, 1.5)  # Faster operations
    
    @task(15)
    def intensive_product_browsing(self):
        """Intensive product browsing with multiple requests"""
        # Browse products multiple times
        for _ in range(random.randint(2, 5)):
            self.browse_products()
            time.sleep(0.1)
    
    @task(8)
    def bulk_favourite_operations(self):
        """Add multiple products to favourites"""
        if not self.product_ids:
            self.browse_products()
        
        for _ in range(random.randint(2, 4)):
            self.add_to_favourites()
            time.sleep(0.1)
    
    @task(5)
    def multiple_order_creation(self):
        """Create multiple orders"""
        for _ in range(random.randint(1, 3)):
            self.create_order()
            time.sleep(0.2)


class LightUser(EcommerceUser):
    """Light user with minimal operations"""
    
    wait_time = between(3, 8)  # Slower operations
    
    @task(5)
    def simple_product_browse(self):
        """Simple product browsing"""
        self.browse_products()
    
    @task(2)
    def view_single_product(self):
        """View a single product"""
        self.view_product_details()


class StressTestUser(HttpUser):
    """User for stress testing - maximum load scenarios"""
    
    wait_time = between(0.1, 0.5)  # Very fast operations
    
    def on_start(self):
        self.user_id = random.randint(1, 1000)
        self.product_ids = list(range(1, 50))  # Assume products exist
    
    @task(20)
    def rapid_product_requests(self):
        """Rapid fire product requests"""
        product_id = random.choice(self.product_ids)
        with self.client.get(f"/app/api/products/{product_id}", catch_response=True) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Stress test failed: {response.status_code}")
    
    @task(10)
    def rapid_order_creation(self):
        """Rapid order creation"""
        order_data = {
            "userId": self.user_id,
            "orderDate": datetime.now().isoformat(),
            "totalAmount": random.uniform(10.0, 100.0),
            "status": "PENDING",
            "orderItems": [{
                "productId": random.choice(self.product_ids),
                "quantity": 1,
                "price": random.uniform(10.0, 50.0)
            }]
        }
        
        with self.client.post("/app/api/orders", json=order_data, catch_response=True) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Stress order creation failed: {response.status_code}")


# Custom event handlers for detailed metrics
@events.request.add_listener
def on_request(request_type, name, response_time, response_length, exception, context, **kwargs):
    """Custom request handler for detailed metrics"""
    if exception:
        print(f"Request failed: {name} - {exception}")
    else:
        print(f"Request success: {name} - {response_time}ms")


@events.user_error.add_listener
def on_user_error(user_instance, exception, tb, **kwargs):
    """Handle user errors"""
    print(f"User error: {exception}")


# Test configuration classes
class PerformanceTestConfig:
    """Configuration for performance tests"""
    
    HOST = "http://localhost:8080"
    USERS = [
        (EcommerceUser, 10),  # 10 normal users
        (HeavyUser, 3),        # 3 heavy users
        (LightUser, 5)         # 5 light users
    ]
    
    @classmethod
    def get_user_classes(cls):
        return [user_class for user_class, _ in cls.USERS]
    
    @classmethod
    def get_user_weights(cls):
        return {user_class: weight for user_class, weight in cls.USERS}


class StressTestConfig:
    """Configuration for stress tests"""
    
    HOST = "http://localhost:8080"
    USERS = [
        (StressTestUser, 50)  # 50 stress test users
    ]
    
    @classmethod
    def get_user_classes(cls):
        return [user_class for user_class, _ in cls.USERS]
    
    @classmethod
    def get_user_weights(cls):
        return {user_class: weight for user_class, weight in cls.USERS}


if __name__ == "__main__":
    print("E-commerce Microservices Performance Tests")
    print("Available test configurations:")
    print("1. Performance Test - Normal load with mixed user types")
    print("2. Stress Test - High load with rapid operations")
    print("\nTo run tests:")
    print("locust -f ecommerce_performance_tests.py --host=http://localhost:8080")
