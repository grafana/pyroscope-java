#!/bin/bash
set -euo pipefail

usage() {
    echo "Usage: $0 -v VERSION -r REPO_URL -d DIST_DIR"
    echo "  -v VERSION   async-profiler version (e.g., 4.3.0.0)"
    echo "  -r REPO_URL  GitHub repository URL (e.g., https://github.com/grafana/async-profiler)"
    echo "  -d DIST_DIR  Output directory for extracted files"
    exit 1
}

while getopts "v:r:d:" opt; do
    case $opt in
        v) VERSION="$OPTARG" ;;
        r) REPO="$OPTARG" ;;
        d) DIST_DIR="$OPTARG" ;;
        *) usage ;;
    esac
done

if [[ -z "${VERSION:-}" || -z "${REPO:-}" || -z "${DIST_DIR:-}" ]]; then
    usage
fi

rm -rf "$DIST_DIR"
mkdir -p "$DIST_DIR/lib"

# Download and extract linux-x64
echo "Downloading linux-x64..."
curl -sL "$REPO/releases/download/v$VERSION/async-profiler-$VERSION-linux-x64.tar.gz" | \
    tar -xzf - -C "$DIST_DIR" --strip-components=1
mv "$DIST_DIR/lib/libasyncProfiler.so" "$DIST_DIR/lib/libasyncProfiler-linux-x64.so"

# Download and extract linux-arm64
echo "Downloading linux-arm64..."
curl -sL "$REPO/releases/download/v$VERSION/async-profiler-$VERSION-linux-arm64.tar.gz" | \
    tar -xzf - -O --wildcards "*/lib/libasyncProfiler.so" > "$DIST_DIR/lib/libasyncProfiler-linux-arm64.so"

# Download and extract macos
echo "Downloading macos..."
TMP_ZIP=$(mktemp)
curl -sL "$REPO/releases/download/v$VERSION/async-profiler-$VERSION-macos.zip" -o "$TMP_ZIP"
unzip -o -j "$TMP_ZIP" "*/lib/libasyncProfiler.dylib" -d "$DIST_DIR/lib"
rm "$TMP_ZIP"
mv "$DIST_DIR/lib/libasyncProfiler.dylib" "$DIST_DIR/lib/libasyncProfiler-macos.so"

# Move async-profiler.jar to lib (if exists)
if [[ -f "$DIST_DIR/jar/async-profiler.jar" ]]; then
    mv "$DIST_DIR/jar/async-profiler.jar" "$DIST_DIR/lib/"
fi

# Compute SHA1 checksums
echo "Computing checksums..."
for platform in linux-x64 linux-arm64 macos; do
    sha1sum "$DIST_DIR/lib/libasyncProfiler-$platform.so" | cut -d' ' -f1 > "$DIST_DIR/lib/libasyncProfiler-$platform.so.sha1"
done

# Keep only lib directory
rm -rf "$DIST_DIR/bin" "$DIST_DIR/jar" "$DIST_DIR/include" "$DIST_DIR/licenses" \
       "$DIST_DIR"/*.html "$DIST_DIR/CHANGELOG.md" "$DIST_DIR/LICENSE" "$DIST_DIR/README.md" 2>/dev/null || true

echo "Done. Files in $DIST_DIR/lib:"
ls -la "$DIST_DIR/lib/"
