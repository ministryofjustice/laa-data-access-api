#!/bin/bash

# Setup script for Git hooks
echo "Setting up pre-commit for LAA Data Access API"

# Install prek globally
echo "Installing prek globally"
curl --proto '=https' --tlsv1.2 \
-LsSf https://raw.githubusercontent.com/ministryofjustice/devsecops-hooks/e85ca6127808ef407bc1e8ff21efed0bbd32bb1a/prek/prek-installer.sh | sh

# Activate prek in the repository
export PATH="$HOME/.local/bin:$PATH"
echo "Installing prek within the repository"
prek install

echo "Git hooks setup complete!"
