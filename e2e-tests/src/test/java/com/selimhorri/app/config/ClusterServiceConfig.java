package com.selimhorri.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Configuration class for cluster service URLs.
 * Used when running E2E tests against a Kubernetes cluster.
 */
@Slf4j
@Data
@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "cluster.services")
public class ClusterServiceConfig {
    
    private String baseUrl = "http://localhost";
    
    private ServiceConfig userService = new ServiceConfig();
    private ServiceConfig productService = new ServiceConfig();
    private ServiceConfig orderService = new ServiceConfig();
    private ServiceConfig paymentService = new ServiceConfig();
    private ServiceConfig shippingService = new ServiceConfig();
    private ServiceConfig favouriteService = new ServiceConfig();
    
    /**
     * Post-construct to log configuration after Spring injects properties
     */
    @javax.annotation.PostConstruct
    public void logConfiguration() {
        log.info("=== ClusterServiceConfig Loaded ===");
        log.info("baseUrl: {}", baseUrl);
        log.info("user-service: url={}, contextPath={}", userService.getUrl(), userService.getContextPath());
        log.info("product-service: url={}, contextPath={}", productService.getUrl(), productService.getContextPath());
        log.info("order-service: url={}, contextPath={}", orderService.getUrl(), orderService.getContextPath());
        log.info("payment-service: url={}, contextPath={}", paymentService.getUrl(), paymentService.getContextPath());
        log.info("shipping-service: url={}, contextPath={}", shippingService.getUrl(), shippingService.getContextPath());
        log.info("favourite-service: url={}, contextPath={}", favouriteService.getUrl(), favouriteService.getContextPath());
        log.info("===================================");
    }
    
    @Data
    public static class ServiceConfig {
        private String url;
        private String contextPath = "";
        
        /**
         * Check if this service config has valid URL
         */
        public boolean hasUrl() {
            return url != null && !url.isEmpty();
        }
    }
    
    /**
     * Get the full URL for a service endpoint.
     * @param serviceName The name of the service
     * @param endpoint The API endpoint (e.g., "/api/users")
     * @return Full URL to the endpoint
     */
    public String getServiceUrl(String serviceName, String endpoint) {
        ServiceConfig service = getServiceConfig(serviceName);
        
        // Build URL: if service has specific URL, use it; otherwise use baseUrl + port inference
        String base;
        if (service.hasUrl()) {
            base = service.getUrl();
        } else {
            // Infer port from service name and use baseUrl
            int port = getDefaultPortForService(serviceName);
            base = baseUrl + ":" + port;
        }
        
        // Get context path - default to /{service-name} if not set
        String context;
        if (service.getContextPath() != null && !service.getContextPath().isEmpty()) {
            context = service.getContextPath();
        } else {
            // Default context path based on service name
            context = getDefaultContextPathForService(serviceName);
        }
        
        // Ensure context path doesn't end with /
        if (context.endsWith("/")) {
            context = context.substring(0, context.length() - 1);
        }
        
        // Ensure endpoint starts with /
        if (!endpoint.startsWith("/")) {
            endpoint = "/" + endpoint;
        }
        
        String fullUrl = base + context + endpoint;
        log.debug("Building URL for service '{}': base='{}', context='{}', endpoint='{}' -> '{}'", 
                  serviceName, base, context, endpoint, fullUrl);
        return fullUrl;
    }
    
    /**
     * Get default port for a service based on service name
     */
    private int getDefaultPortForService(String serviceName) {
        switch (serviceName.toLowerCase()) {
            case "user":
            case "user-service":
                return 8085;
            case "product":
            case "product-service":
                return 8083;
            case "order":
            case "order-service":
                return 8081;
            case "payment":
            case "payment-service":
                return 8082;
            case "shipping":
            case "shipping-service":
                return 8084;
            case "favourite":
            case "favourite-service":
            case "favorite":
            case "favorite-service":
                return 8086;
            default:
                return 8080;
        }
    }
    
    /**
     * Get default context path for a service based on service name
     */
    private String getDefaultContextPathForService(String serviceName) {
        // Ensure service name ends with -service
        String normalized = serviceName.toLowerCase();
        if (!normalized.endsWith("-service")) {
            normalized = normalized + "-service";
        }
        return "/" + normalized;
    }
    
    private ServiceConfig getServiceConfig(String serviceName) {
        switch (serviceName.toLowerCase()) {
            case "user":
            case "user-service":
                return userService;
            case "product":
            case "product-service":
                return productService;
            case "order":
            case "order-service":
                return orderService;
            case "payment":
            case "payment-service":
                return paymentService;
            case "shipping":
            case "shipping-service":
                return shippingService;
            case "favourite":
            case "favourite-service":
            case "favorite":
            case "favorite-service":
                return favouriteService;
            default:
                return new ServiceConfig();
        }
    }
}

