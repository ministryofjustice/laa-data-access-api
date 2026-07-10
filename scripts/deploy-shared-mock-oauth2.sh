#!/usr/bin/env bash
#
# Deploy the SHARED mock-oauth2 server to UAT namespace
#
# ⚠️  IMPORTANT: This is automatically deployed via CI/CD!
#
# Purpose:
#   - Creates a SINGLE shared mock-oauth2 instance for ALL PRs/branches
#   - This pod stays running permanently (not deleted with PRs)
#   - ALL PR deployments will use this shared instance (no per-PR mock-oauth2)
#   - Automatically deployed when merging to main (see .github/workflows/build-main.yml)
#
# Pod Name: laa-data-access-mock-oauth2-shared-<hash>-<id>
# Example:  laa-data-access-mock-oauth2-shared-7d8f9c4b5-xyz
#
# When to run manually:
#   - Usually NOT needed - CI/CD handles it automatically on main merge
#   - Only run manually if you need to update the shared instance outside of CI/CD
#   - Can be re-run safely (Helm upgrade is idempotent)
#
# What PR deployments do:
#   - Point to this shared instance (via service name)
#   - DO NOT create their own mock-oauth2 pod
#   - Deploy faster (no mock-oauth2 startup time)
#
# CI/CD Integration:
#   - Job: deploy-shared-mock-oauth2 in .github/workflows/build-main.yml
#   - Runs after UAT deployment succeeds
#   - Uses the same Helm chart as this script
#

set -euo pipefail

NAMESPACE="${1:-laa-data-access-api-uat}"
RELEASE_NAME="laa-data-access-mock-oauth2-shared"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CHART_DIR="${SCRIPT_DIR}/../.helm/shared-mock-oauth2"

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🚀 Deploying SHARED mock-oauth2 server"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "   ℹ️  This is usually done automatically via CI/CD!"
echo "   Only run manually if needed outside of the normal workflow."
echo ""
echo "   Namespace: $NAMESPACE"
echo "   Release:   $RELEASE_NAME"
echo "   Chart:     $CHART_DIR"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Check if Helm is installed
if ! command -v helm &> /dev/null; then
    echo "❌ Error: Helm is not installed"
    echo "   Please install Helm: https://helm.sh/docs/intro/install/"
    exit 1
fi

# Check if kubectl is configured
if ! kubectl cluster-info &> /dev/null; then
    echo "❌ Error: kubectl is not configured or cannot connect to cluster"
    echo "   Please configure kubectl first"
    exit 1
fi

# Check if namespace exists
if ! kubectl get namespace "$NAMESPACE" &> /dev/null; then
    echo "❌ Error: Namespace '$NAMESPACE' does not exist"
    echo "   Please create it or specify the correct namespace"
    exit 1
fi

# Deploy using Helm
echo "📦 Deploying using Helm..."
echo ""

helm upgrade "$RELEASE_NAME" "$CHART_DIR" \
    --namespace "$NAMESPACE" \
    --install \
    --wait

if [ $? -eq 0 ]; then
    DEPLOYMENT_RESULT="success"
else
    DEPLOYMENT_RESULT="failed"
    exit 1
fi

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ Shared mock-oauth2 server deployed successfully!"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "📋 Deployment Details:"
echo "   Namespace:     $NAMESPACE"
echo "   Service:       $RELEASE_NAME"
echo "   Internal URL:  http://$RELEASE_NAME.$NAMESPACE.svc.cluster.local:9999"
echo ""
echo "🔍 Check Status:"
echo "   kubectl get pods -n $NAMESPACE -l app.kubernetes.io/name=shared-mock-oauth2"
echo ""
echo "📦 Pod Name Pattern:"
echo "   $RELEASE_NAME-<hash>-<id>"
echo "   Example: $RELEASE_NAME-7d8f9c4b5-xyz"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🎉 IMPORTANT: This pod will stay running!"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "   ✅ All PR deployments will use this shared instance"
echo "   ✅ PRs will NOT create their own mock-oauth2 pods"
echo "   ✅ This pod stays running even when PRs are closed"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📚 Next Steps:"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "1️⃣  Verify deployment:"
echo "   kubectl get pods -n $NAMESPACE -l app.kubernetes.io/name=shared-mock-oauth2"
echo ""
echo "2️⃣  Deploy a PR (as normal - no changes needed)"
echo "   PRs will automatically use this shared mock-oauth2 instance"
echo ""
echo "3️⃣  Verify PR doesn't create its own mock-oauth2:"
echo "   kubectl get pods -n $NAMESPACE | grep pr-<number>"
echo "   (should see app pod only, no mock-oauth2 pod)"
echo ""
echo "4️⃣  Test authentication with a PR deployment:"
echo ""
echo "   # Port-forward the shared mock-oauth2"
echo "   kubectl -n $NAMESPACE port-forward svc/$RELEASE_NAME 9999:9999"
echo ""
echo "   # Get a token (in another terminal)"
echo "   ./scripts/get-token.sh uat --copy"
echo ""
echo "   # Use the token with your PR deployment"
echo "   # See README.md for full authentication examples"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📖 More Information:"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "   📘 README.md - Authentication section"
echo "   📗 docs/deployment.md - Full deployment documentation"
echo ""
