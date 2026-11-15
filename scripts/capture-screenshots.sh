#!/usr/bin/env bash
# Capture the current app with deterministic demo fixtures on a fresh disposable emulator.
set -euo pipefail
cd "$(dirname "$0")/.."

adb_args=()
if [[ -n "${ANDROID_SERIAL:-}" ]]; then
    adb_args=(-s "$ANDROID_SERIAL")
fi
adb "${adb_args[@]}" install -r app/build/outputs/apk/debug/app-debug.apk
adb "${adb_args[@]}" install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
capture_dir=$(mktemp -d)
trap 'rm -rf "$capture_dir"' EXIT
adb "${adb_args[@]}" shell am instrument -w \
    -e documentationScreenshots true \
    -e class com.sujoy.nextset.DocumentationScreenshotsTest \
    com.sujoy.nextset.test/androidx.test.runner.AndroidJUnitRunner | tee "$capture_dir/result.txt"
python3 - "$capture_dir/result.txt" <<'PYRESULT'
from pathlib import Path
import sys
result = Path(sys.argv[1]).read_text()
if 'OK (1 test)' not in result or any(error in result for error in ('FAILURES!!!', 'INSTRUMENTATION_FAILED', 'Process crashed')):
    raise SystemExit('Screenshot capture failed. Use a fresh disposable emulator and inspect the test output.')
PYRESULT
names=(home workouts week sequence sequence-preview weekly-analysis sound-settings duration-settings settings-dark exercise rest-complete)
for name in "${names[@]}"; do
    adb "${adb_args[@]}" exec-out run-as com.sujoy.nextset \
        cat "files/documentation-screenshots/$name.png" > "$capture_dir/$name.png"
done
# Never replace the checked-in gallery with empty files or shell error output.
python3 - "$capture_dir" <<'PY'
from pathlib import Path
import sys
for path in Path(sys.argv[1]).glob('*.png'):
    data = path.read_bytes()
    if not data.startswith(b'\x89PNG\r\n\x1a\n') or not data.endswith(b'IEND\xaeB`\x82'):
        raise SystemExit(f'Invalid screenshot: {path.name}')
PY
mkdir -p docs/images
cp "$capture_dir"/*.png docs/images/
printf 'Updated %s screenshots in docs/images/\n' "${#names[@]}"
