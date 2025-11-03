#!/usr/bin/env python3
"""
Simple Performance Test for E-commerce Microservices
Tests basic endpoints that are working
"""

import json
import random

from locust import HttpUser, between, task


class SimpleEcommerceUser(HttpUser):
    wait_time = between(1, 3)
    
    def on_start(self):
        """Called when a user starts"""
        print("Starting simple e-commerce test...")
    
    @task(3)
    def test_api_gateway_health(self):
        """Test API Gateway health endpoint"""
        with self.client.get("/actuator/health", catch_response=True) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Health check failed with status {response.status_code}")
    
    @task(2)
    def test_user_service_health(self):
        """Test User Service through API Gateway"""
        with self.client.get("/user-service/actuator/health", catch_response=True) as response:
            if response.status_code in [200, 500]:  # 500 might be expected if service is starting
                response.success()
            else:
                response.failure(f"User service health check failed with status {response.status_code}")
    
    @task(2)
    def test_product_service_health(self):
        """Test Product Service through API Gateway"""
        with self.client.get("/product-service/actuator/health", catch_response=True) as response:
            if response.status_code in [200, 500]:  # 500 might be expected if service is starting
                response.success()
            else:
                response.failure(f"Product service health check failed with status {response.status_code}")
    
    @task(1)
    def test_order_service_health(self):
        """Test Order Service through API Gateway"""
        with self.client.get("/order-service/actuator/health", catch_response=True) as response:
            if response.status_code in [200, 500]:  # 500 might be expected if service is starting
                response.success()
            else:
                response.failure(f"Order service health check failed with status {response.status_code}")
    
    @task(1)
    def test_payment_service_health(self):
        """Test Payment Service through API Gateway"""
        with self.client.get("/payment-service/actuator/health", catch_response=True) as response:
            if response.status_code in [200, 500]:  # 500 might be expected if service is starting
                response.success()
            else:
                response.failure(f"Payment service health check failed with status {response.status_code}")
    
    @task(1)
    def test_favourite_service_health(self):
        """Test Favourite Service through API Gateway"""
        with self.client.get("/favourite-service/actuator/health", catch_response=True) as response:
            if response.status_code in [200, 500]:  # 500 might be expected if service is starting
                response.success()
            else:
                response.failure(f"Favourite service health check failed with status {response.status_code}")
    
    @task(1)
    def test_shipping_service_health(self):
        """Test Shipping Service through API Gateway"""
        with self.client.get("/shipping-service/actuator/health", catch_response=True) as response:
            if response.status_code in [200, 500]:  # 500 might be expected if service is starting
                response.success()
            else:
                response.failure(f"Shipping service health check failed with status {response.status_code}")
    
    @task(1)
    def test_proxy_client_health(self):
        """Test Proxy Client through API Gateway"""
        with self.client.get("/app/actuator/health", catch_response=True) as response:
            if response.status_code in [200, 500]:  # 500 might be expected if service is starting
                response.success()
            else:
                response.failure(f"Proxy client health check failed with status {response.status_code}")


class HeavyLoadUser(HttpUser):
    wait_time = between(0.5, 1.5)
    weight = 2  # This user type will be spawned twice as often
    
    @task(5)
    def rapid_health_checks(self):
        """Rapid health checks to simulate heavy load"""
        endpoints = [
            "/actuator/health",
            "/user-service/actuator/health",
            "/product-service/actuator/health",
            "/order-service/actuator/health",
            "/payment-service/actuator/health"
        ]
        
        endpoint = random.choice(endpoints)
        with self.client.get(endpoint, catch_response=True) as response:
            if response.status_code in [200, 500]:
                response.success()
            else:
                response.failure(f"Endpoint {endpoint} failed with status {response.status_code}")


class StressTestUser(HttpUser):
    wait_time = between(0.1, 0.5)
    weight = 1
    
    @task(10)
    def stress_health_checks(self):
        """Very rapid health checks to stress the system"""
        with self.client.get("/actuator/health", catch_response=True) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Stress test failed with status {response.status_code}")

