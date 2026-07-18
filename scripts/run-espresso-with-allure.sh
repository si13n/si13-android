#!/usr/bin/env sh

set +e

APP_PACKAGE="com.si13.app"
TEST_PACKAGE="com.si13.app.test"

./gradlew connectedDebugAndroidTest --console=plain
test_exit_code=$?

mkdir -p allure-results

collect_run_as_results() {
  package_name="$1"
  remote_dir="$2"
  archive_name="allure-results-${package_name}-$(echo "$remote_dir" | tr / -).tar"

  echo "Looking for Allure results in ${remote_dir}"
  adb shell run-as "$package_name" sh -c "[ -d '${remote_dir}' ] && find '${remote_dir}' -type f | head -20" || true

  adb exec-out run-as "$package_name" sh -c "
    if [ -d '${remote_dir}' ]; then
      cd '${remote_dir}' && tar cf - .
    fi
  " > "$archive_name" || true

  if [ -s "$archive_name" ]; then
    tar xf "$archive_name" -C allure-results || true
  fi
}

collect_external_results() {
  remote_dir="$1"
  archive_name="allure-results-$(echo "$remote_dir" | tr / -).tar"

  echo "Looking for Allure results in ${remote_dir}"
  adb shell "[ -d '${remote_dir}' ] && find '${remote_dir}' -type f | head -20" || true

  adb exec-out sh -c "
    if [ -d '${remote_dir}' ]; then
      cd '${remote_dir}' && tar cf - .
    fi
  " > "$archive_name" || true

  if [ -s "$archive_name" ]; then
    tar xf "$archive_name" -C allure-results || true
  fi
}

collect_run_as_results "$APP_PACKAGE" "/data/data/${APP_PACKAGE}/allure-results"
collect_run_as_results "$APP_PACKAGE" "/data/data/${APP_PACKAGE}/files/allure-results"
collect_run_as_results "$TEST_PACKAGE" "/data/data/${TEST_PACKAGE}/allure-results"
collect_run_as_results "$TEST_PACKAGE" "/data/data/${TEST_PACKAGE}/files/allure-results"
collect_external_results "/sdcard/allure-results"
collect_external_results "/sdcard/googletest/test_outputfiles/allure-results"

echo "Collected Allure result files:"
find allure-results -type f | sort || true

exit "$test_exit_code"
