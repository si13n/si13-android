#!/usr/bin/env sh

set +e

ADDITIONAL_OUTPUT_DIR="app/build/outputs/connected_android_test_additional_output"

./gradlew connectedDebugAndroidTest --console=plain
test_exit_code=$?

mkdir -p allure-results

echo "Looking for Allure results in ${ADDITIONAL_OUTPUT_DIR}"
find "$ADDITIONAL_OUTPUT_DIR" -type f | sort || true
find "$ADDITIONAL_OUTPUT_DIR" -path "*/allure-results/*" -type f -exec cp {} allure-results/ \; || true

echo "Collected Allure result files:"
find allure-results -type f | sort || true

exit "$test_exit_code"
