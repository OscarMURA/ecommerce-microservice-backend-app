#!/usr/bin/env python3
"""
Individual Microservice Performance Tests

This module contains focused performance tests for each microservice,
allowing for isolated testing and bottleneck identification.

Microservices tested:
- User Service (Port 8700)
- Product Service (Port 8500) 
- Order Service (Port 8300)
- Payment Service (Port 8400)
- Favourite Service (Port 8800)
- Shipping Service (Port 8600)
"""

import json
import random
import time
from datetime import datetime
from typing import Dict, List

from faker import Faker
from locust import HttpUser, between, events, task

fake = Faker()

class UserServiceUser(HttpUser):
    """Performance tests for User Service"""
    
    host = "http://localhost:8700"
    wait_time = between(1, 2)
    
    def on_start(self):
        self.user_id = None
        self.created_users = []
    
    @task(5)
    def create_user(self):
        """Test user creation performance"""
        user_data = {
            "firstName": fake.first_name(),
            "lastName": fake.last_name(),
            "email": fake.email(),
            "phone": fake.phone_number(),
            "username": fake.user_name(),
            "password": fake.password()
        }
        
        with self.client.post("/user-service/api/users", json=user_data, catch_response=True) as response:
            if response.status_code == 200:
                user_id = response.json().get("userId")
                self.created_users.append(user_id)
                if not self.user_id:
                    self.user_id = user_id
                response.success()
            else:
                response.failure(f"User creation failed: {response.status_code}")
    
    @task(8)
    def get_all_users(self):
        """Test user listing performance"""
        with self.client.get("/user-service/api/users", catch_response=True) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Get users failed: {response.status_code}")
    
    @task(6)
    def get_user_by_id(self):
        """Test user retrieval by ID"""
        if not self.user_id:
            return
        
        with self.client.get(f"/user-service/api/users/{self.user_id}", catch_response=True) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Get user by ID failed: {response.status_code}")
    
    @task(3)
    def update_user(self):
        """Test user update performance"""
        if not self.user_id:
            return
        
        update_data = {
            "userId": self.user_id,
            "firstName": fake.first_name(),
            "lastName": fake.last_name(),
            "phone": fake.phone_number()
        }
        
        with self.client.put(f"/user-service/api/users/{self.user_id}", json=update_data, catch_response=True) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"User update failed: {response.status_code}")
    
    @task(2)
    def create_credential(self):
        """Test credential creation"""
        if not self.user_id:
            return
        
        credential_data = {
            "userId": self.user_id,
            "username": fake.user_name(),
            "password": fake.password(),
            "role": random.choice(["USER", "ADMIN"])
        }
        
        with self.client.post("/user-service/api/credentials", json=credential_data, catch_response=True) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Credential creation failed: {response.status_code}")


class ProductServiceUser(HttpUser):
    """Performance tests for Product Service"""
    
    host = "http://localhost:8500"
    wait_time = between(0.5, 1.5)
    
    def on_start(self):
        self.product_ids = []
        self.category_ids = []
    
    @task(6)
    def get_all_products(self):
        """Test product listing performance"""
        with self.client.get("/product-service/api/products", catch_response=True) as response:
            if response.status_code == 200:
                products = response.json().get("collection", [])
                if products:
                    self.product_ids = [p.get("productId") for p in products[:10]]
                response.success()
            else:
                response.failure(f"Get products failed: {response.status_code}")
    
    @task(8)
    def get_product_by_id(self):
        """Test product retrieval by ID"""
        if not self.product_ids:
            self.get_all_products()
        
        if self.product_ids:
            product_id = random.choice(self.product_ids)
            with self.client.get(f"/product-service/api/products/{product_id}", catch_response=True) as response:
                if response.status_code == 200:
                    response.success()
                else:
                    response.failure(f"Get product by ID failed: {response.status_code}")
    
    @task(4)
    def create_product(self):
        """Test product creation performance"""
        product_data = {
            "productName": fake.word().title(),
            "description": fake.text(max_nb_chars=200),
            "price": round(random.uniform(10.0, 1000.0), 2),
            "stockQuantity": random.randint(0, 100),
            "categoryId": random.randint(1, 10)
        }
        
        with self.client.post("/product-service/api/products", json=product_data, catch_response=True) as response:
            if response.status_code == 200:
                product_id = response.json().get("productId")
                self.product_ids.append(product_id)
                response.success()
            else:
                response.failure(f"Product creation failed: {response.status_code}")
    
    @task(5)
    def get_categories(self):
        """Test category listing performance"""
        with self.client.get("/product-service/api/categories", catch_response=True) as response:
            if response.status_code == 200:
                categories = response.json().get("collection", [])
                if categories:
                    self.category_ids = [c.get("categoryId") for c in categories]
                response.success()
            else:
                response.failure(f"Get categories failed: {response.status_code}")
    
    @task(3)
    def create_category(self):
        """Test category creation performance"""
        category_data = {
            "categoryName": fake.word().title(),
            "description": fake.text(max_nb_chars=100)
        }
        
        with self.client.post("/product-service/api/categories", json=category_data, catch_response=True) as response:
            if response.status_code == 200:
                category_id = response.json().get("categoryId")
                self.category_ids.append(category_id)
                response.success()
            else:
                response.failure(f"Category creation failed: {response.status_code}")


class OrderServiceUser(HttpUser):
    """Performance tests for Order Service"""
    
    host = "http://localhost:8300"
    wait_time = between(1, 3)
    
    def on_start(self):
        self.order_ids = []
        self.user_id = random.randint(1, 1000)
    
    @task(5)
    def create_order(self):
        """Test order creation performance"""
        order_items = []
        for _ in range(random.randint(1, 5)):
            order_items.append({
                "productId": random.randint(1, 100),
                "quantity": random.randint(1, 10),
                "price": round(random.uniform(10.0, 200.0), 2)
            })
        
        order_data = {
            "userId": self.user_id,
            "orderDate": datetime.now().isoformat(),
            "totalAmount": sum(item["price"] * item["quantity"] for item in order_items),
            "status": "PENDING",
            "orderItems": order_items
        }
        
        with self.client.post("/order-service/api/orders", json=order_data, catch_response=True) as response:
            if response.status_code == 200:
                order_id = response.json().get("orderId")
                self.order_ids.append(order_id)
                response.success()
            else:
                response.failure(f"Order creation failed: {response.status_code}")
    
    @task(6)
    def get_all_orders(self):
        """Test order listing performance"""
        with self.client.get("/order-service/api/orders", catch_response=True) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Get orders failed: {response.status_code}")
    
    @task(4)
    def get_order_by_id(self):
        """Test order retrieval by ID"""
        if not self.order_ids:
            return
        
        order_id = random.choice(self.order_ids)
        with self.client.get(f"/order-service/api/orders/{order_id}", catch_response=True) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Get order by ID failed: {response.status_code}")
    
    @task(3)
    def update_order(self):
        """Test order update performance"""
        if not self.order_ids:
            return
        
        order_id = random.choice(self.order_ids)
        update_data = {
            "orderId": order_id,
            "status": random.choice(["PROCESSING", "SHIPPED", "DELIVERED", "CANCELLED"])
        }
        
        with self.client.put(f"/order-service/api/orders/{order_id}", json=update_data, catch_response=True) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Order update failed: {response.status_code}")
    
    @task(2)
    def create_cart(self):
        """Test cart creation performance"""
        cart_data = {
            "userId": self.user_id,
            "productId": random.randint(1, 100),
            "quantity": random.randint(1, 5),
            "addedDate": datetime.now().isoformat()
        }
        
        with self.client.post("/order-service/api/carts", json=cart_data, catch_response=True) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Cart creation failed: {response.status_code}")


class PaymentServiceUser(HttpUser):
    """Performance tests for Payment Service"""
    
    host = "http://localhost:8400"
    wait_time = between(1, 2)
    
    def on_start(self):
        self.payment_ids = []
        self.order_id = random.randint(1, 1000)
    
    @task(6)
    def create_payment(self):
        """Test payment creation performance"""
        payment_data = {
            "orderId": self.order_id,
            "amount": round(random.uniform(50.0, 500.0), 2),
            "paymentMethod": random.choice(["CREDIT_CARD", "DEBIT_CARD", "PAYPAL", "BANK_TRANSFER"]),
            "status": "PENDING",
            "paymentDate": datetime.now().isoformat()
        }
        
        with self.client.post("/payment-service/api/payments", json=payment_data, catch_response=True) as response:
            if response.status_code == 200:
                payment_id = response.json().get("paymentId")
                self.payment_ids.append(payment_id)
                response.success()
            else:
                response.failure(f"Payment creation failed: {response.status_code}")
    
    @task(5)
    def get_all_payments(self):
        """Test payment listing performance"""
        with self.client.get("/payment-service/api/payments", catch_response=True) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Get payments failed: {response.status_code}")
    
    @task(4)
    def get_payment_by_id(self):
        """Test payment retrieval by ID"""
        if not self.payment_ids:
            return
        
        payment_id = random.choice(self.payment_ids)
        with self.client.get(f"/payment-service/api/payments/{payment_id}", catch_response=True) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Get payment by ID failed: {response.status_code}")
    
    @task(3)
    def update_payment_status(self):
        """Test payment status update performance"""
        if not self.payment_ids:
            return
        
        payment_id = random.choice(self.payment_ids)
        update_data = {
            "paymentId": payment_id,
            "status": random.choice(["COMPLETED", "FAILED", "REFUNDED"])
        }
        
        with self.client.put(f"/payment-service/api/payments/{payment_id}", json=update_data, catch_response=True) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Payment update failed: {response.status_code}")


class FavouriteServiceUser(HttpUser):
    """Performance tests for Favourite Service"""
    
    host = "http://localhost:8800"
    wait_time = between(1, 2)
    
    def on_start(self):
        self.user_id = random.randint(1, 1000)
        self.favourite_ids = []
    
    @task(6)
    def add_favourite(self):
        """Test adding product to favourites"""
        favourite_data = {
            "userId": self.user_id,
            "productId": random.randint(1, 100),
            "likeDate": datetime.now().isoformat()
        }
        
        with self.client.post("/favourite-service/api/favourites", json=favourite_data, catch_response=True) as response:
            if response.status_code == 200:
                favourite_id = response.json().get("favouriteId")
                self.favourite_ids.append(favourite_id)
                response.success()
            else:
                response.failure(f"Add favourite failed: {response.status_code}")
    
    @task(5)
    def get_favourites(self):
        """Test getting user favourites"""
        with self.client.get("/favourite-service/api/favourites", catch_response=True) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Get favourites failed: {response.status_code}")
    
    @task(3)
    def remove_favourite(self):
        """Test removing favourite"""
        if not self.favourite_ids:
            return
        
        # Use a composite key for deletion
        favourite_id = random.choice(self.favourite_ids)
        with self.client.delete(f"/favourite-service/api/favourites/{self.user_id}/{random.randint(1, 100)}/{datetime.now().isoformat()}", catch_response=True) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Remove favourite failed: {response.status_code}")


class ShippingServiceUser(HttpUser):
    """Performance tests for Shipping Service"""
    
    host = "http://localhost:8600"
    wait_time = between(1, 2)
    
    def on_start(self):
        self.order_item_ids = []
    
    @task(5)
    def create_order_item(self):
        """Test order item creation for shipping"""
        order_item_data = {
            "orderId": random.randint(1, 1000),
            "productId": random.randint(1, 100),
            "quantity": random.randint(1, 10),
            "price": round(random.uniform(10.0, 200.0), 2),
            "shippingAddress": {
                "street": fake.street_address(),
                "city": fake.city(),
                "state": fake.state(),
                "zipCode": fake.zipcode(),
                "country": fake.country()
            }
        }
        
        with self.client.post("/shipping-service/api/order-items", json=order_item_data, catch_response=True) as response:
            if response.status_code == 200:
                order_item_id = response.json().get("orderItemId")
                self.order_item_ids.append(order_item_id)
                response.success()
            else:
                response.failure(f"Order item creation failed: {response.status_code}")
    
    @task(4)
    def get_order_items(self):
        """Test order items listing"""
        with self.client.get("/shipping-service/api/order-items", catch_response=True) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Get order items failed: {response.status_code}")
    
    @task(3)
    def get_order_item_by_id(self):
        """Test order item retrieval by ID"""
        if not self.order_item_ids:
            return
        
        order_item_id = random.choice(self.order_item_ids)
        with self.client.get(f"/shipping-service/api/order-items/{order_item_id}", catch_response=True) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Get order item by ID failed: {response.status_code}")


# Stress test configurations for individual services
class UserServiceStressUser(HttpUser):
    """Stress tests for User Service"""
    
    host = "http://localhost:8700"
    wait_time = between(0.1, 0.3)
    
    @task(20)
    def rapid_user_creation(self):
        """Rapid user creation for stress testing"""
        user_data = {
            "firstName": fake.first_name(),
            "lastName": fake.last_name(),
            "email": fake.email(),
            "phone": fake.phone_number(),
            "username": fake.user_name(),
            "password": fake.password()
        }
        
        with self.client.post("/user-service/api/users", json=user_data, catch_response=True) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Stress user creation failed: {response.status_code}")
    
    @task(15)
    def rapid_user_retrieval(self):
        """Rapid user retrieval for stress testing"""
        user_id = random.randint(1, 1000)
        with self.client.get(f"/user-service/api/users/{user_id}", catch_response=True) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Stress user retrieval failed: {response.status_code}")


class ProductServiceStressUser(HttpUser):
    """Stress tests for Product Service"""
    
    host = "http://localhost:8500"
    wait_time = between(0.1, 0.3)
    
    @task(25)
    def rapid_product_requests(self):
        """Rapid product requests for stress testing"""
        product_id = random.randint(1, 1000)
        with self.client.get(f"/product-service/api/products/{product_id}", catch_response=True) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Stress product request failed: {response.status_code}")
    
    @task(10)
    def rapid_product_creation(self):
        """Rapid product creation for stress testing"""
        product_data = {
            "productName": fake.word().title(),
            "description": fake.text(max_nb_chars=100),
            "price": round(random.uniform(10.0, 1000.0), 2),
            "stockQuantity": random.randint(0, 100),
            "categoryId": random.randint(1, 10)
        }
        
        with self.client.post("/product-service/api/products", json=product_data, catch_response=True) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Stress product creation failed: {response.status_code}")


if __name__ == "__main__":
    print("Individual Microservice Performance Tests")
    print("Available test classes:")
    print("1. UserServiceUser - User Service performance tests")
    print("2. ProductServiceUser - Product Service performance tests")
    print("3. OrderServiceUser - Order Service performance tests")
    print("4. PaymentServiceUser - Payment Service performance tests")
    print("5. FavouriteServiceUser - Favourite Service performance tests")
    print("6. ShippingServiceUser - Shipping Service performance tests")
    print("\nStress test classes:")
    print("7. UserServiceStressUser - User Service stress tests")
    print("8. ProductServiceStressUser - Product Service stress tests")
    print("\nTo run individual service tests:")
    print("locust -f individual_service_tests.py UserServiceUser --host=http://localhost:8700")
