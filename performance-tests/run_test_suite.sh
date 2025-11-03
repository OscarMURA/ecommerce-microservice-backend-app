#!/bin/bash

# Automated Performance Test Suite
# This script runs a comprehensive suite of performance tests
# and generates detailed reports

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
LOG_FILE="${SCRIPT_DIR}/test_suite_${TIMESTAMP}.log"

# Function to log with timestamp
log() {
    echo -e "${BLUE}[$(date '+%Y-%m-%d %H:%M:%S')]${NC} $1" | tee -a "$LOG_FILE"
}

log_success() {
    echo -e "${GREEN}[$(date '+%Y-%m-%d %H:%M:%S')] SUCCESS${NC} $1" | tee -a "$LOG_FILE"
}

log_warning() {
    echo -e "${YELLOW}[$(date '+%Y-%m-%d %H:%M:%S')] WARNING${NC} $1" | tee -a "$LOG_FILE"
}

log_error() {
    echo -e "${RED}[$(date '+%Y-%m-%d %H:%M:%S')] ERROR${NC} $1" | tee -a "$LOG_FILE"
}

# Function to check prerequisites
check_prerequisites() {
    log "Checking prerequisites..."
    
    # Check if Python 3 is installed
    if ! command -v python3 &> /dev/null; then
        log_error "Python 3 is not installed"
        exit 1
    fi
    
    # Check if pip3 is installed
    if ! command -v pip3 &> /dev/null; then
        log_error "pip3 is not installed"
        exit 1
    fi
    
    # Check if services are running
    log "Checking if microservices are running..."
    if ! "${SCRIPT_DIR}/run_tests.sh" check > /dev/null 2>&1; then
        log_error "Microservices are not running. Please start them first."
        log "Run: cd ../scripts && ./start-services.sh"
        exit 1
    fi
    
    log_success "Prerequisites check passed"
}

# Function to install dependencies
install_dependencies() {
    log "Installing Python dependencies..."
    "${SCRIPT_DIR}/run_tests.sh" install
    log_success "Dependencies installed"
}

# Function to run individual service tests
run_individual_service_tests() {
    local services=("user-service" "product-service" "order-service" "payment-service" "favourite-service" "shipping-service")
    local test_results=()
    
    log "Starting individual service performance tests..."
    
    for service in "${services[@]}"; do
        log "Testing $service..."
        
        if "${SCRIPT_DIR}/run_tests.sh" performance "$service" 20 2 5m; then
            log_success "$service test completed"
            test_results+=("$service:PASS")
        else
            log_error "$service test failed"
            test_results+=("$service:FAIL")
        fi
        
        # Wait between tests
        sleep 10
    done
    
    # Report individual service results
    log "Individual service test results:"
    for result in "${test_results[@]}"; do
        local service=$(echo $result | cut -d: -f1)
        local status=$(echo $result | cut -d: -f2)
        
        if [ "$status" = "PASS" ]; then
            log_success "$service: PASSED"
        else
            log_error "$service: FAILED"
        fi
    done
}

# Function to run e-commerce system tests
run_ecommerce_tests() {
    log "Starting e-commerce system performance tests..."
    
    # Normal load test
    log "Running normal load test..."
    if "${SCRIPT_DIR}/run_tests.sh" performance ecommerce 20 2 5m; then
        log_success "Normal load test completed"
    else
        log_error "Normal load test failed"
        return 1
    fi
    
    # Wait between tests
    sleep 30
    
    # Peak load test
    log "Running peak load test..."
    if "${SCRIPT_DIR}/run_tests.sh" performance ecommerce 50 5 5m; then
        log_success "Peak load test completed"
    else
        log_error "Peak load test failed"
        return 1
    fi
    
    # Wait between tests
    sleep 30
    
    # Stress test
    log "Running stress test..."
    if "${SCRIPT_DIR}/run_tests.sh" stress ecommerce 100 10 5m; then
        log_success "Stress test completed"
    else
        log_error "Stress test failed"
        return 1
    fi
}

# Function to generate comprehensive report
generate_comprehensive_report() {
    log "Generating comprehensive test report..."
    
    # Find the most recent test results
    local latest_timestamp=$(ls -t "${SCRIPT_DIR}/results"/*.csv 2>/dev/null | head -1 | grep -o '[0-9]\{8\}_[0-9]\{6\}' || echo "")
    
    if [ -z "$latest_timestamp" ]; then
        log_warning "No test results found to generate report"
        return 1
    fi
    
    # Generate reports for different test types
    local test_types=("ecommerce" "user-service" "product-service" "order-service" "payment-service" "favourite-service" "shipping-service")
    
    for test_type in "${test_types[@]}"; do
        if [ -f "${SCRIPT_DIR}/results/${test_type}_${latest_timestamp}_stats.csv" ]; then
            log "Generating report for $test_type..."
            python3 "${SCRIPT_DIR}/generate_report.py" --test-name "$test_type" --timestamp "$latest_timestamp" --results-dir "${SCRIPT_DIR}/results"
        fi
    done
    
    log_success "Comprehensive report generated"
}

# Function to analyze results
analyze_results() {
    log "Analyzing test results..."
    
    local results_dir="${SCRIPT_DIR}/results"
    local analysis_file="${results_dir}/analysis_${TIMESTAMP}.txt"
    
    echo "Performance Test Analysis - $(date)" > "$analysis_file"
    echo "=====================================" >> "$analysis_file"
    echo "" >> "$analysis_file"
    
    # Analyze each service
    local services=("ecommerce" "user-service" "product-service" "order-service" "payment-service" "favourite-service" "shipping-service")
    
    for service in "${services[@]}"; do
        local stats_file=$(ls -t "${results_dir}/${service}"_*_stats.csv 2>/dev/null | head -1)
        
        if [ -f "$stats_file" ]; then
            echo "=== $service Performance Analysis ===" >> "$analysis_file"
            
            # Extract key metrics
            local total_requests=$(tail -n +2 "$stats_file" | awk -F',' '{sum+=$2} END {print sum}')
            local total_failures=$(tail -n +2 "$stats_file" | awk -F',' '{sum+=$3} END {print sum}')
            local avg_response_time=$(tail -n +2 "$stats_file" | awk -F',' '{sum+=$4} END {print sum/NR}')
            local max_response_time=$(tail -n +2 "$stats_file" | awk -F',' '{if($5>max) max=$5} END {print max}')
            
            echo "Total Requests: $total_requests" >> "$analysis_file"
            echo "Total Failures: $total_failures" >> "$analysis_file"
            echo "Average Response Time: ${avg_response_time}ms" >> "$analysis_file"
            echo "Max Response Time: ${max_response_time}ms" >> "$analysis_file"
            
            # Calculate error rate
            local error_rate=$(echo "scale=2; $total_failures * 100 / $total_requests" | bc 2>/dev/null || echo "0")
            echo "Error Rate: ${error_rate}%" >> "$analysis_file"
            
            # Performance assessment
            if (( $(echo "$error_rate < 1" | bc -l) )); then
                echo "Status: EXCELLENT" >> "$analysis_file"
            elif (( $(echo "$error_rate < 5" | bc -l) )); then
                echo "Status: GOOD" >> "$analysis_file"
            else
                echo "Status: NEEDS IMPROVEMENT" >> "$analysis_file"
            fi
            
            echo "" >> "$analysis_file"
        fi
    done
    
    log_success "Results analysis completed: $analysis_file"
}

# Function to cleanup old results
cleanup_old_results() {
    log "Cleaning up old test results..."
    
    local results_dir="${SCRIPT_DIR}/results"
    local days_to_keep=7
    
    # Remove CSV files older than 7 days
    find "$results_dir" -name "*.csv" -mtime +$days_to_keep -delete 2>/dev/null || true
    
    # Remove HTML reports older than 7 days
    find "$results_dir" -name "*.html" -mtime +$days_to_keep -delete 2>/dev/null || true
    
    # Remove PNG charts older than 7 days
    find "$results_dir" -name "*.png" -mtime +$days_to_keep -delete 2>/dev/null || true
    
    log_success "Cleanup completed"
}

# Function to send notifications (placeholder)
send_notifications() {
    log "Sending test completion notifications..."
    
    # This is a placeholder for notification logic
    # In a real implementation, you might send emails, Slack messages, etc.
    
    local subject="Performance Test Suite Completed - $(date)"
    local body="The automated performance test suite has completed. Check the results in the reports directory."
    
    # Example: Send email notification
    # echo "$body" | mail -s "$subject" admin@example.com
    
    log_success "Notifications sent"
}

# Function to show help
show_help() {
    echo "Automated Performance Test Suite"
    echo ""
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  --full-suite              Run complete test suite (default)"
    echo "  --individual-only        Run only individual service tests"
    echo "  --ecommerce-only         Run only e-commerce system tests"
    echo "  --quick                  Run quick tests (shorter duration)"
    echo "  --cleanup                Clean up old test results"
    echo "  --help                   Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0                       # Run full test suite"
    echo "  $0 --individual-only     # Run only individual service tests"
    echo "  $0 --quick              # Run quick tests"
    echo "  $0 --cleanup            # Clean up old results"
}

# Main function
main() {
    local run_full_suite=true
    local run_individual_only=false
    local run_ecommerce_only=false
    local quick_mode=false
    local cleanup_only=false
    
    # Parse command line arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            --full-suite)
                run_full_suite=true
                shift
                ;;
            --individual-only)
                run_individual_only=true
                run_full_suite=false
                shift
                ;;
            --ecommerce-only)
                run_ecommerce_only=true
                run_full_suite=false
                shift
                ;;
            --quick)
                quick_mode=true
                shift
                ;;
            --cleanup)
                cleanup_only=true
                shift
                ;;
            --help)
                show_help
                exit 0
                ;;
            *)
                log_error "Unknown option: $1"
                show_help
                exit 1
                ;;
        esac
    done
    
    # Start logging
    log "Starting Automated Performance Test Suite"
    log "Timestamp: $TIMESTAMP"
    log "Log file: $LOG_FILE"
    
    # Check prerequisites
    check_prerequisites
    
    # Install dependencies
    install_dependencies
    
    # Cleanup old results if requested
    if [ "$cleanup_only" = true ]; then
        cleanup_old_results
        exit 0
    fi
    
    # Run tests based on options
    if [ "$run_individual_only" = true ]; then
        run_individual_service_tests
    elif [ "$run_ecommerce_only" = true ]; then
        run_ecommerce_tests
    elif [ "$run_full_suite" = true ]; then
        run_individual_service_tests
        run_ecommerce_tests
    fi
    
    # Generate reports
    generate_comprehensive_report
    
    # Analyze results
    analyze_results
    
    # Send notifications
    send_notifications
    
    # Cleanup old results
    cleanup_old_results
    
    log_success "Automated Performance Test Suite completed successfully!"
    log "Check the results directory for detailed reports and analysis"
}

# Run main function with all arguments
main "$@"
