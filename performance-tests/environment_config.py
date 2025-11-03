# Performance Test Environment Configurations

# =============================================================================
# ENVIRONMENT-SPECIFIC CONFIGURATIONS
# =============================================================================

# Development Environment
DEVELOPMENT_CONFIG = {
    "host": "http://localhost:8080",
    "max_users": 50,
    "default_duration": "5m",
    "spawn_rate": 5,
    "expected_metrics": {
        "response_time_p95": 3000,
        "error_rate": 0.05,
        "throughput": 30
    },
    "services": {
        "user-service": "http://localhost:8700",
        "product-service": "http://localhost:8500",
        "order-service": "http://localhost:8300",
        "payment-service": "http://localhost:8400",
        "favourite-service": "http://localhost:8800",
        "shipping-service": "http://localhost:8600"
    }
}

# Staging Environment
STAGING_CONFIG = {
    "host": "http://staging-api.example.com",
    "max_users": 200,
    "default_duration": "15m",
    "spawn_rate": 10,
    "expected_metrics": {
        "response_time_p95": 2000,
        "error_rate": 0.02,
        "throughput": 100
    },
    "services": {
        "user-service": "http://staging-user.example.com",
        "product-service": "http://staging-product.example.com",
        "order-service": "http://staging-order.example.com",
        "payment-service": "http://staging-payment.example.com",
        "favourite-service": "http://staging-favourite.example.com",
        "shipping-service": "http://staging-shipping.example.com"
    }
}

# Production Environment
PRODUCTION_CONFIG = {
    "host": "http://api.example.com",
    "max_users": 500,
    "default_duration": "30m",
    "spawn_rate": 20,
    "expected_metrics": {
        "response_time_p95": 1500,
        "error_rate": 0.01,
        "throughput": 200
    },
    "services": {
        "user-service": "http://user-api.example.com",
        "product-service": "http://product-api.example.com",
        "order-service": "http://order-api.example.com",
        "payment-service": "http://payment-api.example.com",
        "favourite-service": "http://favourite-api.example.com",
        "shipping-service": "http://shipping-api.example.com"
    }
}

# Minikube Environment
# Note: Requires port-forwarding to be configured using setup-minikube-ports.sh
MINIKUBE_CONFIG = {
    "host": "http://localhost:8080",  # Not used - no API Gateway in Minikube
    "max_users": 100,
    "default_duration": "10m",
    "spawn_rate": 5,
    "expected_metrics": {
        "response_time_p95": 3000,
        "error_rate": 0.05,
        "throughput": 50
    },
    "services": {
        "user-service": "http://localhost:8085",      # Port-forwarded from Minikube
        "product-service": "http://localhost:8083",   # Port-forwarded from Minikube
        "order-service": "http://localhost:8081",     # Port-forwarded from Minikube
        "payment-service": "http://localhost:8082",   # Port-forwarded from Minikube
        "favourite-service": "http://localhost:8086",  # Port-forwarded from Minikube
        "shipping-service": "http://localhost:8084"    # Port-forwarded from Minikube
    }
}

# GKE Staging Environment
# Note: Requires port-forwarding to be configured using run-performance-gke.sh
GKE_CONFIG = {
    "host": "http://localhost:8080",  # Not used - no API Gateway in GKE staging
    "max_users": 50,
    "default_duration": "5m",
    "spawn_rate": 5,
    "expected_metrics": {
        "response_time_p95": 2000,
        "error_rate": 0.03,
        "throughput": 40
    },
    "services": {
        "user-service": "http://localhost:8085",      # Port-forwarded from GKE
        "product-service": "http://localhost:8083",   # Port-forwarded from GKE
        "order-service": "http://localhost:8081",     # Port-forwarded from GKE
        "payment-service": "http://localhost:8082",   # Port-forwarded from GKE
        "favourite-service": "http://localhost:8086",  # Port-forwarded from GKE
        "shipping-service": "http://localhost:8084"    # Port-forwarded from GKE
    }
}

# =============================================================================
# TEST SCENARIO TEMPLATES
# =============================================================================

# Quick Test Template (for CI/CD)
QUICK_TEST_TEMPLATE = {
    "name": "Quick Test",
    "description": "Fast test for CI/CD pipelines",
    "users": 10,
    "spawn_rate": 2,
    "duration": "2m",
    "user_distribution": {
        "EcommerceUser": 8,
        "HeavyUser": 1,
        "LightUser": 1
    }
}

# Smoke Test Template
SMOKE_TEST_TEMPLATE = {
    "name": "Smoke Test",
    "description": "Basic functionality verification",
    "users": 5,
    "spawn_rate": 1,
    "duration": "1m",
    "user_distribution": {
        "EcommerceUser": 5
    }
}

# Load Test Template
LOAD_TEST_TEMPLATE = {
    "name": "Load Test",
    "description": "Normal expected load",
    "users": 50,
    "spawn_rate": 5,
    "duration": "10m",
    "user_distribution": {
        "EcommerceUser": 35,
        "HeavyUser": 10,
        "LightUser": 5
    }
}

# Stress Test Template
STRESS_TEST_TEMPLATE = {
    "name": "Stress Test",
    "description": "Find system breaking point",
    "users": 200,
    "spawn_rate": 20,
    "duration": "20m",
    "user_distribution": {
        "StressTestUser": 200
    }
}

# =============================================================================
# MONITORING CONFIGURATIONS
# =============================================================================

# Metrics Collection
METRICS_CONFIG = {
    "collect_system_metrics": True,
    "collect_application_metrics": True,
    "collect_database_metrics": True,
    "collect_network_metrics": True,
    "sampling_interval": 5,  # seconds
    "retention_period": "7d"
}

# Alerting Thresholds
ALERTING_THRESHOLDS = {
    "response_time": {
        "warning": 2000,    # 2 seconds
        "critical": 5000    # 5 seconds
    },
    "error_rate": {
        "warning": 0.01,    # 1%
        "critical": 0.05    # 5%
    },
    "throughput": {
        "warning": 50,      # 50 req/sec
        "critical": 20      # 20 req/sec
    },
    "cpu_usage": {
        "warning": 70,      # 70%
        "critical": 90      # 90%
    },
    "memory_usage": {
        "warning": 80,      # 80%
        "critical": 95      # 95%
    }
}

# =============================================================================
# REPORTING CONFIGURATIONS
# =============================================================================

# Report Formats
REPORT_FORMATS = {
    "html": {
        "enabled": True,
        "include_charts": True,
        "include_statistics": True,
        "template": "default"
    },
    "csv": {
        "enabled": True,
        "include_raw_data": True,
        "include_aggregated_data": True
    },
    "json": {
        "enabled": True,
        "include_metadata": True,
        "include_timestamps": True
    },
    "pdf": {
        "enabled": False,
        "template": "executive_summary"
    }
}

# Chart Configurations
CHART_CONFIG = {
    "response_time_chart": {
        "enabled": True,
        "show_percentiles": [50, 90, 95, 99],
        "show_trend_line": True
    },
    "throughput_chart": {
        "enabled": True,
        "show_average": True,
        "show_peak": True
    },
    "error_rate_chart": {
        "enabled": True,
        "show_error_types": True,
        "show_trend_line": True
    },
    "resource_usage_chart": {
        "enabled": True,
        "show_cpu": True,
        "show_memory": True,
        "show_disk": True
    }
}

# =============================================================================
# NOTIFICATION CONFIGURATIONS
# =============================================================================

# Email Notifications
EMAIL_CONFIG = {
    "enabled": True,
    "smtp_server": "smtp.example.com",
    "smtp_port": 587,
    "username": "performance-tests@example.com",
    "password": "password",
    "recipients": [
        "dev-team@example.com",
        "qa-team@example.com",
        "ops-team@example.com"
    ],
    "send_on_failure": True,
    "send_on_success": False
}

# Slack Notifications
SLACK_CONFIG = {
    "enabled": True,
    "webhook_url": "https://hooks.slack.com/services/...",
    "channel": "#performance-tests",
    "send_on_failure": True,
    "send_on_success": False,
    "include_charts": True
}

# Webhook Notifications
WEBHOOK_CONFIG = {
    "enabled": False,
    "url": "https://api.example.com/webhooks/performance-tests",
    "headers": {
        "Authorization": "Bearer token",
        "Content-Type": "application/json"
    },
    "send_on_failure": True,
    "send_on_success": False
}

# =============================================================================
# DATA MANAGEMENT CONFIGURATIONS
# =============================================================================

# Test Data Management
TEST_DATA_CONFIG = {
    "cleanup_after_test": True,
    "backup_before_test": True,
    "restore_after_test": True,
    "data_retention_days": 30,
    "max_data_size_gb": 10
}

# Results Storage
RESULTS_STORAGE_CONFIG = {
    "local_storage": {
        "enabled": True,
        "path": "./results",
        "max_size_gb": 5
    },
    "cloud_storage": {
        "enabled": False,
        "provider": "aws_s3",
        "bucket": "performance-test-results",
        "region": "us-east-1"
    },
    "database_storage": {
        "enabled": False,
        "connection_string": "postgresql://user:pass@localhost/perf_tests",
        "table_prefix": "perf_test_"
    }
}

# =============================================================================
# SECURITY CONFIGURATIONS
# =============================================================================

# Authentication
AUTH_CONFIG = {
    "enabled": True,
    "type": "basic",  # basic, oauth2, jwt
    "username": "test_user",
    "password": "test_password",
    "token_endpoint": "http://localhost:8080/auth/token"
}

# SSL/TLS
SSL_CONFIG = {
    "enabled": False,
    "verify_certificates": True,
    "certificate_path": "/path/to/cert.pem",
    "key_path": "/path/to/key.pem"
}

# Rate Limiting
RATE_LIMITING_CONFIG = {
    "enabled": True,
    "requests_per_minute": 1000,
    "burst_limit": 100,
    "retry_after_seconds": 60
}

# =============================================================================
# PERFORMANCE TUNING CONFIGURATIONS
# =============================================================================

# Locust Configuration
LOCUST_CONFIG = {
    "master_host": "localhost",
    "master_port": 5557,
    "worker_count": 4,
    "connection_pool_size": 100,
    "connection_timeout": 30,
    "read_timeout": 30,
    "max_retries": 3,
    "retry_delay": 1
}

# HTTP Client Configuration
HTTP_CLIENT_CONFIG = {
    "connection_pool_size": 100,
    "connection_timeout": 30,
    "read_timeout": 30,
    "max_retries": 3,
    "retry_delay": 1,
    "keep_alive": True,
    "compression": True
}

# Database Connection Configuration
DATABASE_CONFIG = {
    "connection_pool_size": 20,
    "connection_timeout": 10,
    "query_timeout": 30,
    "max_retries": 3,
    "retry_delay": 1
}

# =============================================================================
# ENVIRONMENT DETECTION
# =============================================================================

def get_environment_config():
    """Get configuration based on current environment"""
    import os
    
    env = os.getenv('PERF_TEST_ENV', 'development').lower()
    
    configs = {
        'development': DEVELOPMENT_CONFIG,
        'staging': STAGING_CONFIG,
        'production': PRODUCTION_CONFIG,
        'minikube': MINIKUBE_CONFIG,
        'gke': GKE_CONFIG
    }
    
    return configs.get(env, DEVELOPMENT_CONFIG)

def get_test_template(template_name):
    """Get test template by name"""
    templates = {
        'quick': QUICK_TEST_TEMPLATE,
        'smoke': SMOKE_TEST_TEMPLATE,
        'load': LOAD_TEST_TEMPLATE,
        'stress': STRESS_TEST_TEMPLATE
    }
    
    return templates.get(template_name, LOAD_TEST_TEMPLATE)

# =============================================================================
# CONFIGURATION VALIDATION
# =============================================================================

def validate_config(config):
    """Validate configuration parameters"""
    required_fields = ['host', 'max_users', 'default_duration', 'spawn_rate']
    
    for field in required_fields:
        if field not in config:
            raise ValueError(f"Missing required field: {field}")
    
    if config['max_users'] <= 0:
        raise ValueError("max_users must be greater than 0")
    
    if config['spawn_rate'] <= 0:
        raise ValueError("spawn_rate must be greater than 0")
    
    return True

# =============================================================================
# CONFIGURATION EXPORT
# =============================================================================

def export_config_to_json(config, filename):
    """Export configuration to JSON file"""
    import json
    
    with open(filename, 'w') as f:
        json.dump(config, f, indent=2)

def export_config_to_yaml(config, filename):
    """Export configuration to YAML file"""
    import yaml
    
    with open(filename, 'w') as f:
        yaml.dump(config, f, default_flow_style=False)

if __name__ == "__main__":
    # Example usage
    config = get_environment_config()
    print(f"Current environment configuration: {config['host']}")
    
    # Validate configuration
    validate_config(config)
    print("Configuration is valid")
    
    # Export configuration
    export_config_to_json(config, "current_config.json")
    print("Configuration exported to current_config.json")
