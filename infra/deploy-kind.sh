#!/usr/bin/env bash
# Creates a local kind cluster and deploys the service plus the observability stack.
# Requirements: docker, kind, helm, kubectl.
set -euo pipefail

CLUSTER="${CLUSTER:-mdp}"
RELEASE="${RELEASE:-kube-prometheus-stack}"
NAMESPACE="${NAMESPACE:-mdp}"
NODE_IMAGE="${NODE_IMAGE:-kindest/node:v1.31.4}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KIND="${KIND_BIN:-kind}"

# Some Windows setups export HOME=/z/ (a read-only drive). Fall back to the
# Windows profile so kind can write .kube/config.
if [ -z "${KUBECONFIG:-}" ]; then
	for candidate in "${HOME:-}" "${USERPROFILE:-}"; do
		[ -n "$candidate" ] || continue
		if mkdir -p "$candidate/.kube" 2>/dev/null; then
			export KUBECONFIG="$candidate/.kube/config"
			export HOME="$candidate"
			break
		fi
	done
fi

if ! "$KIND" get clusters 2>/dev/null | grep -qx "$CLUSTER"; then
	echo "==> creating kind cluster $CLUSTER"
	"$KIND" create cluster --name "$CLUSTER" --image "$NODE_IMAGE" --wait 180s
fi

"$KIND" export kubeconfig --name "$CLUSTER"

echo "==> building image"
docker build -t market-data-platform:local "$ROOT/service"

echo "==> loading image into $CLUSTER"
"$KIND" load docker-image market-data-platform:local --name "$CLUSTER"

echo "==> installing $RELEASE (this can take a few minutes)"
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts >/dev/null 2>&1 || true
helm repo update >/dev/null
helm upgrade --install "$RELEASE" prometheus-community/kube-prometheus-stack \
	--namespace monitoring --create-namespace \
	--set grafana.adminPassword=admin \
	--timeout 15m

echo "==> applying app manifests"
kubectl apply -k "$ROOT/infra/k8s"

echo "==> registering Grafana dashboard"
kubectl create configmap mdp-overview-dashboard -n "$NAMESPACE" \
	--from-file=mdp-overview.json="$ROOT/infra/grafana/dashboards/mdp-overview.json" \
	--dry-run=client -o yaml \
	| kubectl label --local -f - grafana_dashboard=1 -o yaml \
	| kubectl apply -f -

echo "==> waiting for rollout"
kubectl -n "$NAMESPACE" rollout status deployment/mdp-service --timeout=180s

cat <<EOF

Deployment ready. Port-forward to access:

  kubectl -n $NAMESPACE port-forward svc/mdp-service 8080:80
  kubectl -n monitoring port-forward svc/$RELEASE-grafana 3000:80
  kubectl -n monitoring port-forward svc/$RELEASE-prometheus 9090:9090

Grafana: admin / admin
EOF
