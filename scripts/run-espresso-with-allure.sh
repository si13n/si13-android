#!/usr/bin/env sh

set +e

./gradlew connectedDebugAndroidTest --console=plain
test_exit_code=$?

mkdir -p allure-results
adb exec-out run-as com.si13.app sh -c 'cd /data/data/com.si13.app/files && tar cf - allure-results 2>/dev/null' > allure-results.tar || true

if [ -s allure-results.tar ]; then
  tar xf allure-results.tar || true
fi

exit "$test_exit_code"
