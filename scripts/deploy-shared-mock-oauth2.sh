#!/usr/bin/env bash
#
# Deploy the SHARED mock-oauth2 server to UAT namespace
#
# ⚠️  IMPORTANT: This is a ONE-TIME deployment!
#
# Purpose:
#   - Creates a SINGLE shared mock-oauth2 instance for ALL PRs/branches
#   - This pod stays running permanently (not deleted with PRs)
#   - ALL PR deployments will use this shared instance (no per-PR mock-oauth2)
#   - Automatically deployed via CI/CD when merging to main
#
# Pod Name: laa-data-access-mock-oauth2-shared-<hash>-<id>
# Example:  laa-data-access-mock-oauth2-shared-7d8f9c4b5-xyz
#
# When to run:
#   - Usually NOT needed - CI/CD handles it automatically on main merge
#   - Only run manually if CI/CD is bypassed or for initial setup
#   - Can be re-run safely (Helm upgrade is idempotent)
#
# What PR deployments do:
#   - Point to this shared instance (via service name)
#   - DO NOT create their own mock-oauth2 pod
#   - Deploy faster (no mock-oauth2 startup time)
#
# See docs/FAQ-SHARED-MOCK-OAUTH2.md for more details
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
echo "   This is usually done automatically via CI/CD!"
echo "   Only run manually if needed for initial setup."
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
echo "   kubectl get pods -n $NAMESPACE -l app=mock-oauth2-shared"
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
echo "   ✅ Dev token also enabled (use 'swagger-caseworker-token')"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📚 Next Steps:"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "1️⃣  Verify deployment:"
echo "   kubectl get pods -n $NAMESPACE -l app=mock-oauth2-shared"
echo ""
echo "2️⃣  Deploy your PR (as normal - no changes needed)"
echo ""
echo "3️⃣  Verify PR doesn't create its own mock-oauth2:"
echo "   kubectl get pods -n $NAMESPACE | grep rc-<your-feature>"
echo "   (should see app pod only, no mock-oauth2 pod)"
echo ""
echo "4️⃣  Test authentication:"
echo ""
echo "   Option A - Dev Token (simplest):"
echo "     curl -H \"Authorization: Bearer swagger-caseworker-token\" \\"
echo "          https://laa-data-access-api-rc-<your-feature>-uat.../"
echo ""
echo "   Option B - Mock OAuth2 JWT:"
echo "     ./scripts/get-token.sh uat --copy"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📖 Documentation:"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "   📘 docs/FAQ-SHARED-MOCK-OAUTH2.md - Quick FAQ"
echo "   📗 docs/mock-oauth2-and-dev-token-setup.md - Complete guide"
echo ""
