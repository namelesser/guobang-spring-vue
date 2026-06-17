#!/bin/bash

# Start frontend dev server
cd "$(dirname "$0")/frontend" || exit 1

echo "Starting frontend dev server..."
echo "URL: http://localhost:5173"
echo ""

npm run dev
