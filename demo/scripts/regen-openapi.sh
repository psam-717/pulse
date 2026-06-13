#!/bin/bash
# Regenerate OpenAPI documentation snapshot
# Run this whenever endpoints change
# Requires: Spring Boot server running on port 8080

set -e
cd "$(dirname "$0")/.."

echo "Generating OpenAPI spec from running server..."
curl -s http://localhost:8080/v3/api-docs | python -m json.tool > docs/openapi.json
echo "→ docs/openapi.json ($(wc -c < docs/openapi.json) bytes)"

echo "Done! Commit and push docs/ to update GitHub Pages."