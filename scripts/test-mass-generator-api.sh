#!/bin/bash

# Test the Mass Data Generator API endpoint

BASE_URL="${BASE_URL:-http://localhost:8080}"
TOKEN="${AUTH_TOKEN:-}" # Set AUTH_TOKEN environment variable if authentication is required

echo "🚀 Testing Mass Data Generator API"
echo "=================================="
echo ""

# Test with minimal request (uses defaults)
echo "Test 1: Generate 10 applications (minimal request)..."
response=$(curl -s -X POST "${BASE_URL}/api/v0/admin/generate-mass-data" \
  -H "Content-Type: application/json" \
  -H "X-Service-Name: CIVIL_APPLY" \
  ${TOKEN:+-H "Authorization: Bearer $TOKEN"} \
  -d '{"count": 10}')

echo "Response: $response"
echo ""

# Test with custom parameters
echo "Test 2: Generate 5 applications with high decision rate..."
response=$(curl -s -X POST "${BASE_URL}/api/v0/admin/generate-mass-data" \
  -H "Content-Type: application/json" \
  -H "X-Service-Name: CIVIL_APPLY" \
  ${TOKEN:+-H "Authorization: Bearer $TOKEN"} \
  -d '{
    "count": 5,
    "batchSize": 100,
    "decisionRate": 0.8,
    "linkRate": 0.2
  }')

echo "Response: $response"
echo ""

echo "✅ Test complete! Check the responses above."
echo ""
echo "To view in Swagger UI:"
echo "  ${BASE_URL}/swagger-ui.html"
echo ""
echo "Look for: POST /api/v0/admin/generate-mass-data"

