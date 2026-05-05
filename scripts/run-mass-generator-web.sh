#!/usr/bin/env bash
set -euo pipefail

# Runs the mass generator as a web server with REST API endpoints
PORT="${PORT:-8081}"

echo "========================================="
echo "  Mass Generator - Web Mode"
echo "========================================="
echo "Building JAR..."

./gradlew :data-access-mass-generator:bootJar

JAR_PATH="data-access-mass-generator/build/libs/data-access-mass-generator-*.jar"

echo ""
echo "Starting web server on port ${PORT}..."
echo ""
echo "API Endpoints:"
echo "  POST   http://localhost:${PORT}/api/mass-generator/generate?count=1000&cleanup=false"
echo "  GET    http://localhost:${PORT}/api/mass-generator/jobs/{jobId}"
echo "  GET    http://localhost:${PORT}/api/mass-generator/jobs"
echo "  DELETE http://localhost:${PORT}/api/mass-generator/jobs/{jobId}"
echo ""
echo "Press Ctrl+C to stop the server"
echo "========================================="

java -jar ${JAR_PATH} --web --spring.profiles.active=db,web --server.port=${PORT}
