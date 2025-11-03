# E-commerce Microservices Performance Test Configurations

# Test Scenarios Configuration
# Each scenario defines different load patterns and user behaviors

# =============================================================================
# PERFORMANCE TEST SCENARIOS
# =============================================================================

# Scenario 1: Normal Load - Typical e-commerce usage
NORMAL_LOAD_SCENARIO = {
    "name": "Normal Load",
    "description": "Simulates typical e-commerce usage patterns",
    "users": 20,
    "spawn_rate": 2,
    "duration": "10m",
    "user_distribution": {
        "EcommerceUser": 15,    # 75% normal users
        "HeavyUser": 3,         # 15% heavy users
        "LightUser": 2          # 10% light users
    },
    "expected_metrics": {
        "response_time_p95": 2000,  # 95th percentile < 2 seconds
        "error_rate": 0.01,         # < 1% error rate
        "throughput": 50            # > 50 requests/second
    }
}

# Scenario 2: Peak Load - Black Friday simulation
PEAK_LOAD_SCENARIO = {
    "name": "Peak Load",
    "description": "Simulates high traffic periods like Black Friday",
    "users": 100,
    "spawn_rate": 10,
    "duration": "15m",
    "user_distribution": {
        "EcommerceUser": 60,    # 60% normal users
        "HeavyUser": 30,        # 30% heavy users
        "LightUser": 10         # 10% light users
    },
    "expected_metrics": {
        "response_time_p95": 5000,  # 95th percentile < 5 seconds
        "error_rate": 0.05,         # < 5% error rate
        "throughput": 200           # > 200 requests/second
    }
}

# Scenario 3: Sustained Load - Continuous operation
SUSTAINED_LOAD_SCENARIO = {
    "name": "Sustained Load",
    "description": "Long-running test for stability validation",
    "users": 50,
    "spawn_rate": 5,
    "duration": "1h",
    "user_distribution": {
        "EcommerceUser": 35,    # 70% normal users
        "HeavyUser": 10,         # 20% heavy users
        "LightUser": 5           # 10% light users
    },
    "expected_metrics": {
        "response_time_p95": 3000,  # 95th percentile < 3 seconds
        "error_rate": 0.02,         # < 2% error rate
        "throughput": 100           # > 100 requests/second
    }
}

# =============================================================================
# STRESS TEST SCENARIOS
# =============================================================================

# Scenario 4: Stress Test - Find breaking point
STRESS_TEST_SCENARIO = {
    "name": "Stress Test",
    "description": "Gradually increase load to find system limits",
    "users": 200,
    "spawn_rate": 20,
    "duration": "20m",
    "user_distribution": {
        "StressTestUser": 200    # 100% stress test users
    },
    "expected_metrics": {
        "response_time_p95": 10000,  # 95th percentile < 10 seconds
        "error_rate": 0.10,          # < 10% error rate
        "throughput": 500            # > 500 requests/second
    }
}

# Scenario 5: Spike Test - Sudden traffic increase
SPIKE_TEST_SCENARIO = {
    "name": "Spike Test",
    "description": "Simulates sudden traffic spikes",
    "users": 150,
    "spawn_rate": 50,
    "duration": "5m",
    "user_distribution": {
        "StressTestUser": 150    # 100% stress test users
    },
    "expected_metrics": {
        "response_time_p95": 8000,   # 95th percentile < 8 seconds
        "error_rate": 0.15,          # < 15% error rate
        "throughput": 300            # > 300 requests/second
    }
}

# =============================================================================
# INDIVIDUAL SERVICE TEST SCENARIOS
# =============================================================================

# User Service Tests
USER_SERVICE_SCENARIOS = {
    "normal": {
        "users": 30,
        "spawn_rate": 3,
        "duration": "10m",
        "user_class": "UserServiceUser"
    },
    "stress": {
        "users": 100,
        "spawn_rate": 20,
        "duration": "10m",
        "user_class": "UserServiceStressUser"
    }
}

# Product Service Tests
PRODUCT_SERVICE_SCENARIOS = {
    "normal": {
        "users": 40,
        "spawn_rate": 4,
        "duration": "10m",
        "user_class": "ProductServiceUser"
    },
    "stress": {
        "users": 150,
        "spawn_rate": 30,
        "duration": "10m",
        "user_class": "ProductServiceStressUser"
    }
}

# Order Service Tests
ORDER_SERVICE_SCENARIOS = {
    "normal": {
        "users": 25,
        "spawn_rate": 2,
        "duration": "10m",
        "user_class": "OrderServiceUser"
    }
}

# Payment Service Tests
PAYMENT_SERVICE_SCENARIOS = {
    "normal": {
        "users": 20,
        "spawn_rate": 2,
        "duration": "10m",
        "user_class": "PaymentServiceUser"
    }
}

# Favourite Service Tests
FAVOURITE_SERVICE_SCENARIOS = {
    "normal": {
        "users": 35,
        "spawn_rate": 3,
        "duration": "10m",
        "user_class": "FavouriteServiceUser"
    }
}

# Shipping Service Tests
SHIPPING_SERVICE_SCENARIOS = {
    "normal": {
        "users": 20,
        "spawn_rate": 2,
        "duration": "10m",
        "user_class": "ShippingServiceUser"
    }
}

# =============================================================================
# TEST EXECUTION CONFIGURATIONS
# =============================================================================

# Quick Test Suite - For CI/CD pipelines
QUICK_TEST_SUITE = [
    {
        "name": "User Service Quick Test",
        "service": "user-service",
        "scenario": "normal",
        "users": 10,
        "spawn_rate": 2,
        "duration": "2m"
    },
    {
        "name": "Product Service Quick Test",
        "service": "product-service",
        "scenario": "normal",
        "users": 15,
        "spawn_rate": 3,
        "duration": "2m"
    },
    {
        "name": "E-commerce Quick Test",
        "service": "ecommerce",
        "scenario": "normal",
        "users": 20,
        "spawn_rate": 4,
        "duration": "3m"
    }
]

# Comprehensive Test Suite - For release validation
COMPREHENSIVE_TEST_SUITE = [
    {
        "name": "All Services Performance Test",
        "services": ["user-service", "product-service", "order-service", "payment-service", "favourite-service", "shipping-service"],
        "scenario": "normal",
        "users": 30,
        "spawn_rate": 3,
        "duration": "10m"
    },
    {
        "name": "E-commerce Peak Load Test",
        "service": "ecommerce",
        "scenario": "peak",
        "users": 100,
        "spawn_rate": 10,
        "duration": "15m"
    },
    {
        "name": "E-commerce Stress Test",
        "service": "ecommerce",
        "scenario": "stress",
        "users": 200,
        "spawn_rate": 20,
        "duration": "20m"
    }
]

# =============================================================================
# MONITORING AND ALERTING THRESHOLDS
# =============================================================================

PERFORMANCE_THRESHOLDS = {
    "response_time": {
        "warning": 2000,    # 2 seconds
        "critical": 5000     # 5 seconds
    },
    "error_rate": {
        "warning": 0.01,     # 1%
        "critical": 0.05     # 5%
    },
    "throughput": {
        "warning": 50,       # 50 req/sec
        "critical": 20       # 20 req/sec
    },
    "cpu_usage": {
        "warning": 70,       # 70%
        "critical": 90       # 90%
    },
    "memory_usage": {
        "warning": 80,       # 80%
        "critical": 95       # 95%
    }
}

# =============================================================================
# TEST DATA CONFIGURATIONS
# =============================================================================

TEST_DATA_CONFIG = {
    "users": {
        "count": 1000,
        "prefix": "perf_test_user",
        "roles": ["USER", "ADMIN"]
    },
    "products": {
        "count": 500,
        "categories": 20,
        "price_range": [10.0, 1000.0]
    },
    "orders": {
        "max_items_per_order": 10,
        "statuses": ["PENDING", "PROCESSING", "SHIPPED", "DELIVERED", "CANCELLED"]
    },
    "payments": {
        "methods": ["CREDIT_CARD", "DEBIT_CARD", "PAYPAL", "BANK_TRANSFER"],
        "statuses": ["PENDING", "COMPLETED", "FAILED", "REFUNDED"]
    }
}

# =============================================================================
# REPORTING CONFIGURATIONS
# =============================================================================

REPORTING_CONFIG = {
    "formats": ["html", "csv", "json"],
    "include_charts": True,
    "include_statistics": True,
    "include_percentiles": [50, 90, 95, 99],
    "include_error_analysis": True,
    "include_resource_usage": True
}

# =============================================================================
# ENVIRONMENT CONFIGURATIONS
# =============================================================================

ENVIRONMENT_CONFIGS = {
    "development": {
        "host": "http://localhost:8080",
        "max_users": 50,
        "default_duration": "5m"
    },
    "staging": {
        "host": "http://staging-api.example.com",
        "max_users": 200,
        "default_duration": "15m"
    },
    "production": {
        "host": "http://api.example.com",
        "max_users": 500,
        "default_duration": "30m"
    }
}
