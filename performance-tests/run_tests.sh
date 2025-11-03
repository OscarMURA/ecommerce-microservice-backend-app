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

# Detect environment mode
PERF_TEST_ENV="${PERF_TEST_ENV:-development}"
IS_MINIKUBE=false
if [ "$PERF_TEST_ENV" = "minikube" ]; then
    IS_MINIKUBE=true
    print_status "Running in Minikube mode"
fi

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
    
    if [ "$IS_MINIKUBE" = true ]; then
        # Minikube mode - check services with Minikube ports
        local services=(
            "http://localhost:8081/order-service/actuator/health:Order Service"
            "http://localhost:8082/payment-service/actuator/health:Payment Service"
            "http://localhost:8083/product-service/actuator/health:Product Service"
            "http://localhost:8084/shipping-service/actuator/health:Shipping Service"
            "http://localhost:8085/user-service/actuator/health:User Service"
            "http://localhost:8086/favourite-service/actuator/health:Favourite Service"
        )
        
        print_status "Verificando que los port-forwards estén configurados..."
        if ! pgrep -f "kubectl port-forward.*ecommerce" > /dev/null; then
            print_warning "No se encontraron port-forwards activos"
            print_status "Ejecutando setup-minikube-ports.sh..."
            "${PERFORMANCE_TESTS_DIR}/setup-minikube-ports.sh" || {
                print_error "No se pudieron configurar los port-forwards"
                print_status "Ejecuta manualmente: ./setup-minikube-ports.sh"
                exit 1
            }
        fi
    else
        # Development mode - check services with development ports
        local services=(
            "http://localhost:8080/app/actuator/health:API Gateway"
            "http://localhost:8700/user-service/actuator/health:User Service"
            "http://localhost:8500/product-service/actuator/health:Product Service"
            "http://localhost:8300/order-service/actuator/health:Order Service"
            "http://localhost:8400/payment-service/actuator/health:Payment Service"
            "http://localhost:8800/favourite-service/actuator/health:Favourite Service"
            "http://localhost:8600/shipping-service/actuator/health:Shipping Service"
        )
    fi
    
    local failed_services=()
    
    for service in "${services[@]}"; do
        # Parse URL and service name correctly
        # Format: "http://localhost:PORT/SERVICE_PATH/actuator/health:Service Name"
        # Extract everything before the last colon as URL, and after as name
        local url=$(echo "$service" | sed 's/:[^:]*$//')
        local name=$(echo "$service" | sed 's/.*://')
        
        if curl -s -f "$url" > /dev/null 2>&1; then
            print_success "$name is running"
        else
            print_error "$name is not responding at $url"
            failed_services+=("$name")
        fi
    done
    
    if [ ${#failed_services[@]} -gt 0 ]; then
        print_error "Some services are not running."
        if [ "$IS_MINIKUBE" = true ]; then
            print_status "Para Minikube, ejecuta: ./setup-minikube-ports.sh"
            print_status "Y verifica que los servicios estén desplegados: kubectl get pods -n ecommerce"
        else
            print_status "Run: cd ../scripts && ./start-services.sh"
        fi
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
    
    # Determine host URLs based on environment
    if [ "$IS_MINIKUBE" = true ]; then
        local USER_SERVICE_HOST="http://localhost:8085"
        local PRODUCT_SERVICE_HOST="http://localhost:8083"
        local ORDER_SERVICE_HOST="http://localhost:8081"
        local PAYMENT_SERVICE_HOST="http://localhost:8082"
        local FAVOURITE_SERVICE_HOST="http://localhost:8086"
        local SHIPPING_SERVICE_HOST="http://localhost:8084"
    else
        local USER_SERVICE_HOST="http://localhost:8700"
        local PRODUCT_SERVICE_HOST="http://localhost:8500"
        local ORDER_SERVICE_HOST="http://localhost:8300"
        local PAYMENT_SERVICE_HOST="http://localhost:8400"
        local FAVOURITE_SERVICE_HOST="http://localhost:8800"
        local SHIPPING_SERVICE_HOST="http://localhost:8600"
    fi
    
    case $test_type in
        "ecommerce")
            if [ "$IS_MINIKUBE" = true ]; then
                print_warning "E-commerce tests require API Gateway. Minikube deployment doesn't include API Gateway."
                print_status "Running individual service tests instead..."
                print_error "Use individual service tests for Minikube: user-service, product-service, etc."
                exit 1
            else
                locust -f "${PERFORMANCE_TESTS_DIR}/ecommerce_performance_tests.py" \
                       --host="$LOCUST_HOST" \
                       --users="$users" \
                       --spawn-rate="$spawn_rate" \
                       --run-time="$duration" \
                       --html="$results_file" \
                       --csv="$csv_prefix" \
                       --headless
            fi
            ;;
        "user-service")
            locust -f "${PERFORMANCE_TESTS_DIR}/individual_service_tests.py" \
                   UserServiceUser \
                   --host="$USER_SERVICE_HOST" \
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
                   --host="$PRODUCT_SERVICE_HOST" \
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
                   --host="$ORDER_SERVICE_HOST" \
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
                   --host="$PAYMENT_SERVICE_HOST" \
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
                   --host="$FAVOURITE_SERVICE_HOST" \
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
                   --host="$SHIPPING_SERVICE_HOST" \
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
            if [ "$IS_MINIKUBE" = true ]; then
                local USER_SERVICE_HOST="http://localhost:8085"
            else
                local USER_SERVICE_HOST="http://localhost:8700"
            fi
            locust -f "${PERFORMANCE_TESTS_DIR}/individual_service_tests.py" \
                   UserServiceStressUser \
                   --host="$USER_SERVICE_HOST" \
                   --users="$users" \
                   --spawn-rate="$spawn_rate" \
                   --run-time="$duration" \
                   --html="$results_file" \
                   --csv="$csv_prefix" \
                   --headless
            ;;
        "product-service")
            if [ "$IS_MINIKUBE" = true ]; then
                local PRODUCT_SERVICE_HOST="http://localhost:8083"
            else
                local PRODUCT_SERVICE_HOST="http://localhost:8500"
            fi
            locust -f "${PERFORMANCE_TESTS_DIR}/individual_service_tests.py" \
                   ProductServiceStressUser \
                   --host="$PRODUCT_SERVICE_HOST" \
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
    echo "Minikube Mode:"
    echo "  PERF_TEST_ENV=minikube $0 check"
    echo "  PERF_TEST_ENV=minikube $0 performance user-service 30 3 5m"
    echo "  # First setup port-forwarding: ./setup-minikube-ports.sh"
    echo ""
    echo "Parameters:"
    echo "  USERS                      Number of concurrent users"
    echo "  SPAWN_RATE                 Users spawned per second"
    echo "  DURATION                   Test duration (e.g., 5m, 30s, 1h)"
    echo ""
    echo "Environment Variables:"
    echo "  PERF_TEST_ENV              Environment mode: development, minikube, staging, production"
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
            if [ "$IS_MINIKUBE" = true ]; then
                local USER_SERVICE_HOST="http://localhost:8085"
                local PRODUCT_SERVICE_HOST="http://localhost:8083"
            else
                local USER_SERVICE_HOST="http://localhost:8700"
                local PRODUCT_SERVICE_HOST="http://localhost:8500"
            fi
            
            case "$2" in
                "ecommerce")
                    if [ "$IS_MINIKUBE" = true ]; then
                        print_error "E-commerce tests require API Gateway. Minikube doesn't include API Gateway."
                        print_status "Use individual service tests instead."
                        exit 1
                    else
                        run_interactive "${PERFORMANCE_TESTS_DIR}/ecommerce_performance_tests.py" "$LOCUST_HOST"
                    fi
                    ;;
                "user-service")
                    run_interactive "${PERFORMANCE_TESTS_DIR}/individual_service_tests.py" "$USER_SERVICE_HOST"
                    ;;
                "product-service")
                    run_interactive "${PERFORMANCE_TESTS_DIR}/individual_service_tests.py" "$PRODUCT_SERVICE_HOST"
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
