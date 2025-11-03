package com.selimhorri.app.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Helper class to build service URLs for E2E tests.
 * Automatically detects if running in cluster mode and uses appropriate URLs.
 */
@Slf4j
@Component
public class ServiceUrlHelper {
    
    @Autowired(required = false)
    private ClusterServiceConfig clusterServiceConfig;
    
    @Value("${spring.profiles.active:test}")
    private String activeProfile;
    
    private String localBaseUrl;
    
    /**
     * Initialize with local base URL (for non-cluster mode)
     */
    public void setLocalBaseUrl(String localBaseUrl) {
        this.localBaseUrl = localBaseUrl;
    }
    
    /**
     * Check if running in cluster mode
     */
    public boolean isClusterMode() {
        if (clusterServiceConfig == null) {
            return false;
        }
        // Check if cluster profile is active or explicit flag is set
        return (activeProfile != null && activeProfile.contains("cluster")) || 
               "true".equals(System.getProperty("e2e.cluster.mode")) ||
               "true".equals(System.getenv("E2E_CLUSTER_MODE"));
    }
    
    /**
     * Get base URL for the given service endpoint.
     * Automatically determines the service from the endpoint path.
     * 
     * @param endpoint The API endpoint (e.g., "/api/users", "/api/products")
     * @return Full URL to the endpoint
     */
    public String getUrl(String endpoint) {
        boolean clusterMode = isClusterMode();
        log.debug("ServiceUrlHelper.getUrl: endpoint='{}', clusterMode={}, clusterServiceConfig={}", 
                  endpoint, clusterMode, clusterServiceConfig != null);
        
        if (clusterMode && clusterServiceConfig != null) {
            // Determine service name from endpoint
            String serviceName = determineServiceFromEndpoint(endpoint);
            log.debug("Cluster mode: serviceName='{}'", serviceName);
            return clusterServiceConfig.getServiceUrl(serviceName, endpoint);
        } else {
            // Local mode - use local base URL
            String base = (localBaseUrl != null ? localBaseUrl : "http://localhost");
            // Ensure endpoint starts with /
            if (!endpoint.startsWith("/")) {
                endpoint = "/" + endpoint;
            }
            String url = base + endpoint;
            log.debug("Local mode: url='{}'", url);
            return url;
        }
    }
    
    /**
     * Determine service name from endpoint path
     */
    private String determineServiceFromEndpoint(String endpoint) {
        if (endpoint.startsWith("/api/users") || endpoint.startsWith("/api/credentials") || 
            endpoint.startsWith("/api/address")) {
            return "user-service";
        } else if (endpoint.startsWith("/api/products") || endpoint.startsWith("/api/categories")) {
            return "product-service";
        } else if (endpoint.startsWith("/api/orders") || endpoint.startsWith("/api/carts")) {
            return "order-service";
        } else if (endpoint.startsWith("/api/order-items") || endpoint.startsWith("/api/shippings")) {
            return "shipping-service";
        } else if (endpoint.startsWith("/api/payments")) {
            return "payment-service";
        } else if (endpoint.startsWith("/api/favourites")) {
            return "favourite-service";
        }
        
        // Default to user-service if cannot determine
        return "user-service";
    }
}

