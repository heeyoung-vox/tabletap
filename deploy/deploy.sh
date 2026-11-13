#!/usr/bin/env bash
# Runs on the server as the `deploy` user. CI copies the new jar and this script to
# ~/incoming, then runs:  bash ~/incoming/deploy.sh ~/incoming/tabletap.jar
#
# Backs up the database, swaps the jar, restarts the service and waits for it to be
# healthy. If it never becomes healthy, puts the previous jar back.
set -euo pipefail

NEW_JAR="${1:?usage: deploy.sh path/to/tabletap.jar}"
APP_DIR=/opt/tabletap
BACKUP_DIR="$HOME/backups"
KEEP_BACKUPS=14

[ -f "$NEW_JAR" ] || { echo "no such file: $NEW_JAR" >&2; exit 1; }

healthy() {
    for _ in $(seq 1 45); do
        if curl -fsS http://127.0.0.1:8080/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
            return 0
        fi
        sleep 2
    done
    return 1
}

echo "== Back up the database"
mkdir -p "$BACKUP_DIR" && chmod 700 "$BACKUP_DIR"
sudo -u postgres /usr/bin/pg_dump tabletap | gzip > "$BACKUP_DIR/tabletap-$(date +%F-%H%M%S).sql.gz"
ls -1t "$BACKUP_DIR"/tabletap-*.sql.gz | tail -n +$((KEEP_BACKUPS + 1)) | xargs -r rm --

echo "== Install the new jar"
if [ -f "$APP_DIR/tabletap.jar" ]; then
    cp -p "$APP_DIR/tabletap.jar" "$APP_DIR/tabletap.jar.previous"
fi
install -m 644 "$NEW_JAR" "$APP_DIR/tabletap.jar.new"
mv -f "$APP_DIR/tabletap.jar.new" "$APP_DIR/tabletap.jar"

echo "== Restart and wait for health"
sudo /usr/bin/systemctl restart tabletap
if healthy; then
    echo "Deployed. The app is healthy."
    rm -f "$NEW_JAR"
    exit 0
fi

echo "The new version did not become healthy." >&2
if [ -f "$APP_DIR/tabletap.jar.previous" ]; then
    echo "== Roll back to the previous jar" >&2
    mv -f "$APP_DIR/tabletap.jar.previous" "$APP_DIR/tabletap.jar"
    sudo /usr/bin/systemctl restart tabletap
    if healthy; then
        echo "Rolled back. The previous version is running again." >&2
    else
        echo "The previous version is not healthy either. If the new version ran a migration," >&2
        echo "restore the backup in $BACKUP_DIR. Logs: sudo journalctl -u tabletap -n 100" >&2
    fi
fi
exit 1
