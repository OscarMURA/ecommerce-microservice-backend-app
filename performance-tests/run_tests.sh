#!/bin/bash

# E-commerce Microservices Performance Testing Scripts
# This script provides easy execution of Locust performance tests

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
PERFORMANCE_TESTS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOCUST_HOST="http://localhost:8080"
RESULTS_DIR="${PERFORMANCE_TESTS_DIR}/results"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")

# Create results directory
mkdir -p "${RESULTS_DIR}"

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to check if services are running
check_services() {
    print_status "Checking if microservices are running..."
    
    local services=(
        "http://localhost:8080/app/actuator/health:API Gateway"
        "http://localhost:8700/user-service/actuator/health:User Service"
        "http://localhost:8500/product-service/actuator/health:Product Service"
        "http://localhost:8300/order-service/actuator/health:Order Service"
        "http://localhost:8400/payment-service/actuator/health:Payment Service"
        "http://localhost:8800/favourite-service/actuator/health:Favourite Service"
        "http://localhost:8600/shipping-service/actuator/health:Shipping Service"
    )
    
    local failed_services=()
    
    for service in "${services[@]}"; do
        local url=$(echo $service | cut -d: -f1-2)
        local name=$(echo $service | cut -d: -f3)
        
        if curl -s -f "$url" > /dev/null 2>&1; then
            print_success "$name is running"
        else
            print_error "$name is not responding"
            failed_services+=("$name")
        fi
    done
    
    if [ ${#failed_services[@]} -gt 0 ]; then
        print_error "Some services are not running. Please start them first:"
        print_status "Run: cd ../scripts && ./start-services.sh"
        exit 1
    fi
}

# Function to install Python dependencies
install_dependencies() {
    print_status "Installing Python dependencies..."
    
    if ! command -v python3 &> /dev/null; then
        print_error "Python3 is not installed. Please install Python 3.8+ first."
        exit 1
    fi
    
    if ! command -v pip3 &> /dev/null; then
        print_error "pip3 is not installed. Please install pip3 first."
        exit 1
    fi
    
    pip3 install -r "${PERFORMANCE_TESTS_DIR}/requirements.txt"
    print_success "Dependencies installed successfully"
}

# Function to run performance tests
run_performance_tests() {
    local test_type="$1"
    local users="$2"
    local spawn_rate="$3"
    local duration="$4"
    
    print_status "Starting $test_type performance tests..."
    print_status "Users: $users, Spawn Rate: $spawn_rate/sec, Duration: $duration"
    
    local results_file="${RESULTS_DIR}/${test_type}_${TIMESTAMP}.html"
    local csv_prefix="${RESULTS_DIR}/${test_type}_${TIMESTAMP}"
    
    case $test_type in
        "ecommerce")
            locust -f "${PERFORMANCE_TESTS_DIR}/ecommerce_performance_tests.py" \
                   --host="$LOCUST_HOST" \
                   --users="$users" \
                   --spawn-rate="$spawn_rate" \
                   --run-time="$duration" \
                   --html="$results_file" \
                   --csv="$csv_prefix" \
                   --headless
            ;;
        "user-service")
            locust -f "${PERFORMANCE_TESTS_DIR}/individual_service_tests.py" \
                   UserServiceUser \
                   --host="http://localhost:8700" \
                   --users="$users" \
                   --spawn-rate="$spawn_rate" \
                   --run-time="$duration" \
                   --html="$results_file" \
                   --csv="$csv_prefix" \
                   --headless
            ;;
        "product-service")
            locust -f "${PERFORMANCE_TESTS_DIR}/individual_service_tests.py" \
                   ProductServiceUser \
                   --host="http://localhost:8500" \
                   --users="$users" \
                   --spawn-rate="$spawn_rate" \
                   --run-time="$duration" \
                   --html="$results_file" \
                   --csv="$csv_prefix" \
                   --headless
            ;;
        "order-service")
            locust -f "${PERFORMANCE_TESTS_DIR}/individual_service_tests.py" \
                   OrderServiceUser \
                   --host="http://localhost:8300" \
                   --users="$users" \
                   --spawn-rate="$spawn_rate" \
                   --run-time="$duration" \
                   --html="$results_file" \
                   --csv="$csv_prefix" \
                   --headless
            ;;
        "payment-service")
            locust -f "${PERFORMANCE_TESTS_DIR}/individual_service_tests.py" \
                   PaymentServiceUser \
                   --host="http://localhost:8400" \
                   --users="$users" \
                   --spawn-rate="$spawn_rate" \
                   --run-time="$duration" \
                   --html="$results_file" \
                   --csv="$csv_prefix" \
                   --headless
            ;;
        "favourite-service")
            locust -f "${PERFORMANCE_TESTS_DIR}/individual_service_tests.py" \
                   FavouriteServiceUser \
                   --host="http://localhost:8800" \
                   --users="$users" \
                   --spawn-rate="$spawn_rate" \
                   --run-time="$duration" \
                   --html="$results_file" \
                   --csv="$csv_prefix" \
                   --headless
            ;;
        "shipping-service")
            locust -f "${PERFORMANCE_TESTS_DIR}/individual_service_tests.py" \
                   ShippingServiceUser \
                   --host="http://localhost:8600" \
                   --users="$users" \
                   --spawn-rate="$spawn_rate" \
                   --run-time="$duration" \
                   --html="$results_file" \
                   --csv="$csv_prefix" \
                   --headless
            ;;
        *)
            print_error "Unknown test type: $test_type"
            exit 1
            ;;
    esac
    
    print_success "Performance test completed!"
    print_status "Results saved to: $results_file"
    print_status "CSV data saved to: ${csv_prefix}_*.csv"
}

# Function to run stress tests
run_stress_tests() {
    local test_type="$1"
    local users="$2"
    local spawn_rate="$3"
    local duration="$4"
    
    print_status "Starting $test_type stress tests..."
    print_status "Users: $users, Spawn Rate: $spawn_rate/sec, Duration: $duration"
    
    local results_file="${RESULTS_DIR}/${test_type}_stress_${TIMESTAMP}.html"
    local csv_prefix="${RESULTS_DIR}/${test_type}_stress_${TIMESTAMP}"
    
    case $test_type in
        "ecommerce")
            locust -f "${PERFORMANCE_TESTS_DIR}/ecommerce_performance_tests.py" \
                   StressTestUser \
                   --host="$LOCUST_HOST" \
                   --users="$users" \
                   --spawn-rate="$spawn_rate" \
                   --run-time="$duration" \
                   --html="$results_file" \
                   --csv="$csv_prefix" \
                   --headless
            ;;
        "user-service")
            locust -f "${PERFORMANCE_TESTS_DIR}/individual_service_tests.py" \
                   UserServiceStressUser \
                   --host="http://localhost:8700" \
                   --users="$users" \
                   --spawn-rate="$spawn_rate" \
                   --run-time="$duration" \
                   --html="$results_file" \
                   --csv="$csv_prefix" \
                   --headless
            ;;
        "product-service")
            locust -f "${PERFORMANCE_TESTS_DIR}/individual_service_tests.py" \
                   ProductServiceStressUser \
                   --host="http://localhost:8500" \
                   --users="$users" \
                   --spawn-rate="$spawn_rate" \
                   --run-time="$duration" \
                   --html="$results_file" \
                   --csv="$csv_prefix" \
                   --headless
            ;;
        *)
            print_error "Unknown stress test type: $test_type"
            exit 1
            ;;
    esac
    
    print_success "Stress test completed!"
    print_status "Results saved to: $results_file"
    print_status "CSV data saved to: ${csv_prefix}_*.csv"
}

# Function to run interactive tests
run_interactive() {
    local test_file="$1"
    local host="$2"
    
    print_status "Starting interactive Locust test..."
    print_status "Test file: $test_file"
    print_status "Host: $host"
    print_status "Opening Locust web interface at http://localhost:8089"
    
    locust -f "$test_file" --host="$host"
}

# Function to generate test report
generate_report() {
    local test_type="$1"
    local timestamp="$2"
    
    print_status "Generating test report for $test_type..."
    
    local csv_files=(
        "${RESULTS_DIR}/${test_type}_${timestamp}_requests.csv"
        "${RESULTS_DIR}/${test_type}_${timestamp}_stats.csv"
        "${RESULTS_DIR}/${test_type}_${timestamp}_failures.csv"
    )
    
    # Check if CSV files exist
    for csv_file in "${csv_files[@]}"; do
        if [ ! -f "$csv_file" ]; then
            print_warning "CSV file not found: $csv_file"
        fi
    done
    
    print_success "Test report generation completed!"
    print_status "Check the HTML report for detailed results"
}

# Function to show help
show_help() {
    echo "E-commerce Microservices Performance Testing Script"
    echo ""
    echo "Usage: $0 [COMMAND] [OPTIONS]"
    echo ""
    echo "Commands:"
    echo "  install                     Install Python dependencies"
    echo "  check                       Check if all services are running"
    echo "  performance [TYPE] [USERS] [SPAWN_RATE] [DURATION]"
    echo "                              Run performance tests"
    echo "  stress [TYPE] [USERS] [SPAWN_RATE] [DURATION]"
    echo "                              Run stress tests"
    echo "  interactive [TYPE]          Run interactive tests"
    echo "  report [TYPE] [TIMESTAMP]   Generate test report"
    echo ""
    echo "Test Types:"
    echo "  ecommerce                   Full e-commerce system tests"
    echo "  user-service                User service tests"
    echo "  product-service             Product service tests"
    echo "  order-service               Order service tests"
    echo "  payment-service            Payment service tests"
    echo "  favourite-service           Favourite service tests"
    echo "  shipping-service            Shipping service tests"
    echo ""
    echo "Examples:"
    echo "  $0 install"
    echo "  $0 check"
    echo "  $0 performance ecommerce 50 5 5m"
    echo "  $0 stress user-service 100 10 3m"
    echo "  $0 interactive ecommerce"
    echo ""
    echo "Parameters:"
    echo "  USERS                      Number of concurrent users"
    echo "  SPAWN_RATE                 Users spawned per second"
    echo "  DURATION                   Test duration (e.g., 5m, 30s, 1h)"
}

# Main script logic
main() {
    case "$1" in
        "install")
            install_dependencies
            ;;
        "check")
            check_services
            ;;
        "performance")
            if [ $# -lt 5 ]; then
                print_error "Usage: $0 performance [TYPE] [USERS] [SPAWN_RATE] [DURATION]"
                exit 1
            fi
            check_services
            run_performance_tests "$2" "$3" "$4" "$5"
            generate_report "$2" "$TIMESTAMP"
            ;;
        "stress")
            if [ $# -lt 5 ]; then
                print_error "Usage: $0 stress [TYPE] [USERS] [SPAWN_RATE] [DURATION]"
                exit 1
            fi
            check_services
            run_stress_tests "$2" "$3" "$4" "$5"
            generate_report "${2}_stress" "$TIMESTAMP"
            ;;
        "interactive")
            if [ $# -lt 2 ]; then
                print_error "Usage: $0 interactive [TYPE]"
                exit 1
            fi
            check_services
            case "$2" in
                "ecommerce")
                    run_interactive "${PERFORMANCE_TESTS_DIR}/ecommerce_performance_tests.py" "$LOCUST_HOST"
                    ;;
                "user-service")
                    run_interactive "${PERFORMANCE_TESTS_DIR}/individual_service_tests.py" "http://localhost:8700"
                    ;;
                "product-service")
                    run_interactive "${PERFORMANCE_TESTS_DIR}/individual_service_tests.py" "http://localhost:8500"
                    ;;
                *)
                    print_error "Unknown interactive test type: $2"
                    exit 1
                    ;;
            esac
            ;;
        "report")
            if [ $# -lt 3 ]; then
                print_error "Usage: $0 report [TYPE] [TIMESTAMP]"
                exit 1
            fi
            generate_report "$2" "$3"
            ;;
        "help"|"-h"|"--help")
            show_help
            ;;
        *)
            print_error "Unknown command: $1"
            show_help
            exit 1
            ;;
    esac
}

# Run main function with all arguments
main "$@"
