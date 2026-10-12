#!/bin/bash

# Sync updates from upstream (GitHub) to origin (Bitbucket)
# This script fetches updates from the original GitHub repository
# and merges them into the current branch

set -e

echo "🔄 Fetching updates from upstream (GitHub)..."
git fetch upstream

echo "📥 Merging upstream/main into current branch..."
git merge upstream/main

echo "✅ Successfully synced updates from upstream!"
echo ""
echo "To push to Bitbucket, run:"
echo "  git push origin main"

