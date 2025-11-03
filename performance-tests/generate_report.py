#!/usr/bin/env python3
"""
Performance Test Report Generator

This script generates comprehensive reports from Locust test results,
including charts, statistics, and performance analysis.
"""

import argparse
import json
import os
import sys
from datetime import datetime
from typing import Dict, List, Optional

import matplotlib.pyplot as plt
import pandas as pd
import seaborn as sns

# Set up plotting style
plt.style.use('seaborn-v0_8')
sns.set_palette("husl")

class PerformanceReportGenerator:
    """Generates comprehensive performance test reports"""
    
    def __init__(self, results_dir: str):
        self.results_dir = results_dir
        self.timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        self.reports_dir = os.path.join(results_dir, "reports")
        os.makedirs(self.reports_dir, exist_ok=True)
    
    def load_test_data(self, test_name: str, timestamp: str) -> Dict:
        """Load test data from CSV files"""
        data = {}
        
        # Load requests data
        requests_file = os.path.join(self.results_dir, f"{test_name}_{timestamp}_requests.csv")
        if os.path.exists(requests_file):
            data['requests'] = pd.read_csv(requests_file)
        
        # Load stats data
        stats_file = os.path.join(self.results_dir, f"{test_name}_{timestamp}_stats.csv")
        if os.path.exists(stats_file):
            data['stats'] = pd.read_csv(stats_file)
        
        # Load failures data
        failures_file = os.path.join(self.results_dir, f"{test_name}_{timestamp}_failures.csv")
        if os.path.exists(failures_file):
            data['failures'] = pd.read_csv(failures_file)
        
        return data
    
    def generate_response_time_chart(self, data: Dict, test_name: str) -> str:
        """Generate response time distribution chart"""
        if 'requests' not in data:
            return None
        
        df = data['requests']
        
        fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(12, 10))
        
        # Response time over time
        df['timestamp'] = pd.to_datetime(df['timestamp'])
        ax1.plot(df['timestamp'], df['response_time'], alpha=0.7, linewidth=1)
        ax1.set_title(f'{test_name} - Response Time Over Time')
        ax1.set_xlabel('Time')
        ax1.set_ylabel('Response Time (ms)')
        ax1.grid(True, alpha=0.3)
        
        # Response time histogram
        ax2.hist(df['response_time'], bins=50, alpha=0.7, edgecolor='black')
        ax2.set_title(f'{test_name} - Response Time Distribution')
        ax2.set_xlabel('Response Time (ms)')
        ax2.set_ylabel('Frequency')
        ax2.grid(True, alpha=0.3)
        
        plt.tight_layout()
        
        chart_path = os.path.join(self.reports_dir, f"{test_name}_response_time_chart.png")
        plt.savefig(chart_path, dpi=300, bbox_inches='tight')
        plt.close()
        
        return chart_path
    
    def generate_throughput_chart(self, data: Dict, test_name: str) -> str:
        """Generate throughput chart"""
        if 'stats' not in data:
            return None
        
        df = data['stats']
        
        fig, ax = plt.subplots(figsize=(12, 6))
        
        # Calculate throughput (requests per second)
        df['timestamp'] = pd.to_datetime(df['timestamp'])
        df['throughput'] = df['Request Count'] / df['User Count'].replace(0, 1)
        
        ax.plot(df['timestamp'], df['throughput'], linewidth=2, marker='o', markersize=4)
        ax.set_title(f'{test_name} - Throughput Over Time')
        ax.set_xlabel('Time')
        ax.set_ylabel('Requests per Second')
        ax.grid(True, alpha=0.3)
        
        plt.tight_layout()
        
        chart_path = os.path.join(self.reports_dir, f"{test_name}_throughput_chart.png")
        plt.savefig(chart_path, dpi=300, bbox_inches='tight')
        plt.close()
        
        return chart_path
    
    def generate_error_analysis_chart(self, data: Dict, test_name: str) -> str:
        """Generate error analysis chart"""
        if 'stats' not in data:
            return None
        
        df = data['stats']
        
        fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(15, 6))
        
        # Error rate over time
        df['error_rate'] = (df['Failure Count'] / df['Request Count']) * 100
        ax1.plot(df['timestamp'], df['error_rate'], linewidth=2, color='red', marker='o', markersize=4)
        ax1.set_title(f'{test_name} - Error Rate Over Time')
        ax1.set_xlabel('Time')
        ax1.set_ylabel('Error Rate (%)')
        ax1.grid(True, alpha=0.3)
        
        # Request success/failure pie chart
        total_requests = df['Request Count'].sum()
        total_failures = df['Failure Count'].sum()
        total_success = total_requests - total_failures
        
        labels = ['Success', 'Failure']
        sizes = [total_success, total_failures]
        colors = ['lightgreen', 'lightcoral']
        
        ax2.pie(sizes, labels=labels, colors=colors, autopct='%1.1f%%', startangle=90)
        ax2.set_title(f'{test_name} - Request Success/Failure Distribution')
        
        plt.tight_layout()
        
        chart_path = os.path.join(self.reports_dir, f"{test_name}_error_analysis_chart.png")
        plt.savefig(chart_path, dpi=300, bbox_inches='tight')
        plt.close()
        
        return chart_path
    
    def generate_percentile_chart(self, data: Dict, test_name: str) -> str:
        """Generate percentile analysis chart"""
        if 'stats' not in data:
            return None
        
        df = data['stats']
        
        fig, ax = plt.subplots(figsize=(12, 6))
        
        # Calculate percentiles
        percentiles = [50, 90, 95, 99]
        percentile_data = {}
        
        for p in percentiles:
            percentile_data[f'P{p}'] = df[f'95%'].quantile(p/100)
        
        # Create bar chart
        bars = ax.bar(percentile_data.keys(), percentile_data.values(), 
                     color=['skyblue', 'lightgreen', 'orange', 'red'])
        
        # Add value labels on bars
        for bar, value in zip(bars, percentile_data.values()):
            ax.text(bar.get_x() + bar.get_width()/2, bar.get_height() + 5,
                   f'{value:.0f}ms', ha='center', va='bottom')
        
        ax.set_title(f'{test_name} - Response Time Percentiles')
        ax.set_xlabel('Percentile')
        ax.set_ylabel('Response Time (ms)')
        ax.grid(True, alpha=0.3)
        
        plt.tight_layout()
        
        chart_path = os.path.join(self.reports_dir, f"{test_name}_percentile_chart.png")
        plt.savefig(chart_path, dpi=300, bbox_inches='tight')
        plt.close()
        
        return chart_path
    
    def calculate_statistics(self, data: Dict) -> Dict:
        """Calculate comprehensive test statistics"""
        stats = {}
        
        if 'stats' in data:
            df = data['stats']
            
            # Basic statistics
            stats['total_requests'] = df['Request Count'].sum()
            stats['total_failures'] = df['Failure Count'].sum()
            stats['total_users'] = df['User Count'].max()
            stats['test_duration'] = (df['timestamp'].max() - df['timestamp'].min()).total_seconds()
            
            # Performance metrics
            stats['avg_response_time'] = df['Average Response Time'].mean()
            stats['max_response_time'] = df['Max Response Time'].max()
            stats['min_response_time'] = df['Min Response Time'].min()
            
            # Percentiles
            stats['p50_response_time'] = df['50%'].quantile(0.5)
            stats['p90_response_time'] = df['90%'].quantile(0.9)
            stats['p95_response_time'] = df['95%'].quantile(0.95)
            stats['p99_response_time'] = df['99%'].quantile(0.99)
            
            # Throughput
            stats['avg_throughput'] = (df['Request Count'] / df['User Count'].replace(0, 1)).mean()
            stats['max_throughput'] = (df['Request Count'] / df['User Count'].replace(0, 1)).max()
            
            # Error rate
            stats['error_rate'] = (stats['total_failures'] / stats['total_requests']) * 100 if stats['total_requests'] > 0 else 0
        
        return stats
    
    def generate_html_report(self, test_name: str, timestamp: str, stats: Dict, charts: List[str]) -> str:
        """Generate comprehensive HTML report"""
        
        html_content = f"""
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Performance Test Report - {test_name}</title>
            <style>
                body {{
                    font-family: Arial, sans-serif;
                    margin: 0;
                    padding: 20px;
                    background-color: #f5f5f5;
                }}
                .container {{
                    max-width: 1200px;
                    margin: 0 auto;
                    background-color: white;
                    padding: 30px;
                    border-radius: 10px;
                    box-shadow: 0 2px 10px rgba(0,0,0,0.1);
                }}
                .header {{
                    text-align: center;
                    border-bottom: 2px solid #333;
                    padding-bottom: 20px;
                    margin-bottom: 30px;
                }}
                .stats-grid {{
                    display: grid;
                    grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
                    gap: 20px;
                    margin-bottom: 30px;
                }}
                .stat-card {{
                    background-color: #f8f9fa;
                    padding: 20px;
                    border-radius: 8px;
                    border-left: 4px solid #007bff;
                }}
                .stat-value {{
                    font-size: 2em;
                    font-weight: bold;
                    color: #007bff;
                }}
                .stat-label {{
                    color: #666;
                    margin-top: 5px;
                }}
                .chart-section {{
                    margin: 30px 0;
                }}
                .chart-title {{
                    font-size: 1.5em;
                    margin-bottom: 15px;
                    color: #333;
                }}
                .chart-image {{
                    width: 100%;
                    max-width: 800px;
                    height: auto;
                    border: 1px solid #ddd;
                    border-radius: 8px;
                }}
                .summary {{
                    background-color: #e9ecef;
                    padding: 20px;
                    border-radius: 8px;
                    margin-top: 30px;
                }}
                .timestamp {{
                    color: #666;
                    font-size: 0.9em;
                    text-align: center;
                    margin-top: 20px;
                }}
            </style>
        </head>
        <body>
            <div class="container">
                <div class="header">
                    <h1>Performance Test Report</h1>
                    <h2>{test_name}</h2>
                    <p>Generated on {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}</p>
                </div>
                
                <div class="stats-grid">
                    <div class="stat-card">
                        <div class="stat-value">{stats.get('total_requests', 0):,}</div>
                        <div class="stat-label">Total Requests</div>
                    </div>
                    <div class="stat-card">
                        <div class="stat-value">{stats.get('total_failures', 0):,}</div>
                        <div class="stat-label">Total Failures</div>
                    </div>
                    <div class="stat-card">
                        <div class="stat-value">{stats.get('total_users', 0)}</div>
                        <div class="stat-label">Max Users</div>
                    </div>
                    <div class="stat-card">
                        <div class="stat-value">{stats.get('test_duration', 0):.1f}s</div>
                        <div class="stat-label">Test Duration</div>
                    </div>
                    <div class="stat-card">
                        <div class="stat-value">{stats.get('avg_response_time', 0):.0f}ms</div>
                        <div class="stat-label">Avg Response Time</div>
                    </div>
                    <div class="stat-card">
                        <div class="stat-value">{stats.get('p95_response_time', 0):.0f}ms</div>
                        <div class="stat-label">95th Percentile</div>
                    </div>
                    <div class="stat-card">
                        <div class="stat-value">{stats.get('avg_throughput', 0):.1f}</div>
                        <div class="stat-label">Avg Throughput (req/s)</div>
                    </div>
                    <div class="stat-card">
                        <div class="stat-value">{stats.get('error_rate', 0):.2f}%</div>
                        <div class="stat-label">Error Rate</div>
                    </div>
                </div>
                
                <div class="chart-section">
                    <div class="chart-title">Response Time Analysis</div>
                    {"".join([f'<img src="{chart}" class="chart-image" alt="Response Time Chart"><br>' for chart in charts if 'response_time' in chart])}
                </div>
                
                <div class="chart-section">
                    <div class="chart-title">Throughput Analysis</div>
                    {"".join([f'<img src="{chart}" class="chart-image" alt="Throughput Chart"><br>' for chart in charts if 'throughput' in chart])}
                </div>
                
                <div class="chart-section">
                    <div class="chart-title">Error Analysis</div>
                    {"".join([f'<img src="{chart}" class="chart-image" alt="Error Analysis Chart"><br>' for chart in charts if 'error' in chart])}
                </div>
                
                <div class="chart-section">
                    <div class="chart-title">Percentile Analysis</div>
                    {"".join([f'<img src="{chart}" class="chart-image" alt="Percentile Chart"><br>' for chart in charts if 'percentile' in chart])}
                </div>
                
                <div class="summary">
                    <h3>Test Summary</h3>
                    <p><strong>Test Name:</strong> {test_name}</p>
                    <p><strong>Timestamp:</strong> {timestamp}</p>
                    <p><strong>Total Requests:</strong> {stats.get('total_requests', 0):,}</p>
                    <p><strong>Success Rate:</strong> {100 - stats.get('error_rate', 0):.2f}%</p>
                    <p><strong>Average Response Time:</strong> {stats.get('avg_response_time', 0):.0f}ms</p>
                    <p><strong>95th Percentile Response Time:</strong> {stats.get('p95_response_time', 0):.0f}ms</p>
                    <p><strong>Average Throughput:</strong> {stats.get('avg_throughput', 0):.1f} requests/second</p>
                </div>
                
                <div class="timestamp">
                    Report generated on {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}
                </div>
            </div>
        </body>
        </html>
        """
        
        report_path = os.path.join(self.reports_dir, f"{test_name}_report_{timestamp}.html")
        with open(report_path, 'w') as f:
            f.write(html_content)
        
        return report_path
    
    def generate_report(self, test_name: str, timestamp: str) -> str:
        """Generate complete performance test report"""
        print(f"Generating report for {test_name} (timestamp: {timestamp})")
        
        # Load test data
        data = self.load_test_data(test_name, timestamp)
        
        if not data:
            print(f"No data found for test {test_name} with timestamp {timestamp}")
            return None
        
        # Generate charts
        charts = []
        
        response_time_chart = self.generate_response_time_chart(data, test_name)
        if response_time_chart:
            charts.append(response_time_chart)
        
        throughput_chart = self.generate_throughput_chart(data, test_name)
        if throughput_chart:
            charts.append(throughput_chart)
        
        error_chart = self.generate_error_analysis_chart(data, test_name)
        if error_chart:
            charts.append(error_chart)
        
        percentile_chart = self.generate_percentile_chart(data, test_name)
        if percentile_chart:
            charts.append(percentile_chart)
        
        # Calculate statistics
        stats = self.calculate_statistics(data)
        
        # Generate HTML report
        report_path = self.generate_html_report(test_name, timestamp, stats, charts)
        
        print(f"Report generated: {report_path}")
        return report_path


def main():
    parser = argparse.ArgumentParser(description='Generate performance test reports')
    parser.add_argument('--test-name', required=True, help='Name of the test')
    parser.add_argument('--timestamp', required=True, help='Timestamp of the test results')
    parser.add_argument('--results-dir', default='results', help='Directory containing test results')
    
    args = parser.parse_args()
    
    generator = PerformanceReportGenerator(args.results_dir)
    report_path = generator.generate_report(args.test_name, args.timestamp)
    
    if report_path:
        print(f"Report successfully generated: {report_path}")
    else:
        print("Failed to generate report")
        sys.exit(1)


if __name__ == "__main__":
    main()
