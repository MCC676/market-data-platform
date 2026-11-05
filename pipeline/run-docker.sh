#!/usr/bin/env bash
# Builds the job and runs it with the official Spark image (Linux, Java 21).
# The local JDK is not used: Spark 4.x does not officially support Java 23.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE="${SPARK_IMAGE:-apache/spark:4.1.2-scala2.13-java21-python3-ubuntu}"
ICEBERG_VERSION="${ICEBERG_VERSION:-1.11.0}"
EXTRACT="${EXTRACT:-/opt/mdp/extracts/shipments_extract.csv}"
SPARK_MASTER="${SPARK_MASTER:-local[2]}"

export MAVEN_USER_HOME="${MAVEN_USER_HOME:-${USERPROFILE:-$HOME}/.m2}"

echo "==> building pipeline jar"
(cd "$ROOT/pipeline" && ./mvnw -q -Pspark -DskipTests package)

echo "==> running Spark job in $IMAGE"
MSYS_NO_PATHCONV=1 docker run --rm --user root \
	-v "$ROOT/pipeline:/opt/mdp" \
	-v mdp-ivy-cache:/root/.ivy2 \
	-w /opt/mdp \
	"$IMAGE" \
	/opt/spark/bin/spark-submit \
		--master "$SPARK_MASTER" \
		--conf spark.ui.enabled=false \
		--conf spark.jars.ivy=/root/.ivy2 \
		--packages "org.apache.iceberg:iceberg-spark-runtime-4.1_2.13:${ICEBERG_VERSION}" \
		--class com.mccanto.mdp.pipeline.IngestShipmentsJob \
		/opt/mdp/target/mdp-pipeline.jar \
		--extract "$EXTRACT" \
		--warehouse /opt/mdp/warehouse
