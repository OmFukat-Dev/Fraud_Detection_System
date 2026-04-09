#!/bin/sh
set -eu

CERT_DIR=/etc/letsencrypt/live/fraud-detection.example.com
CERT_FILE=$CERT_DIR/fullchain.pem
KEY_FILE=$CERT_DIR/privkey.pem

if [ ! -s "$CERT_FILE" ] || [ ! -s "$KEY_FILE" ]; then
  mkdir -p "$CERT_DIR"
  openssl req -x509 -nodes -newkey rsa:2048 -days 3650 -subj /CN=fraud-detection.example.com -keyout "$KEY_FILE" -out "$CERT_FILE" >/dev/null 2>&1
fi

exec "$@"
