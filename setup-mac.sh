#!/bin/bash
# ─────────────────────────────────────────────────────────────────
# setup-mac.sh
# Removes macOS quarantine flags from Chrome for Testing and
# ChromeDriver binaries downloaded by Selenium Manager and
# WebDriverManager so Gatekeeper does not block them.
# ─────────────────────────────────────────────────────────────────

set -e

CACHE_DIRS=(
    "$HOME/.cache/selenium"
    "$HOME/Library/Caches/selenium"
    "$HOME/.cache/selenium-manager"
    "$HOME/.m2/repository/webdriver"
)

cleared=0

for dir in "${CACHE_DIRS[@]}"; do
    if [ -d "$dir" ]; then
        echo "Clearing quarantine flags in $dir ..."
        xattr -cr "$dir" 2>/dev/null && cleared=$((cleared + 1))
    fi
done

if [ $cleared -eq 0 ]; then
    echo "No cached browser directories found."
    echo "Run your tests once (they may fail), then re-run this script."
else
    echo "Done — quarantine flags removed from $cleared cache location(s)."
fi