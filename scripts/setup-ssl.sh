#!/bin/bash

# SSL Certificate Setup Script for Fraud Detection System
# This script generates self-signed certificates for development
# For production, use Let's Encrypt or your organization's certificates

set -e

SSL_DIR="./docker/ssl"
CERT_NAME="fraud-detection"
DOMAIN="fraud-detection.example.com"

echo "Setting up SSL certificates for $DOMAIN..."

# Create SSL directory if it doesn't exist
mkdir -p $SSL_DIR

# Generate private key
echo "Generating private key..."
openssl genrsa -out $SSL_DIR/$CERT_NAME.key 2048

# Generate certificate signing request (CSR)
echo "Generating CSR..."
openssl req -new -key $SSL_DIR/$CERT_NAME.key -out $SSL_DIR/$CERT_NAME.csr -subj "/C=US/ST=CA/L=San Francisco/O=Fraud Detection/OU=IT/CN=$DOMAIN"

# Generate self-signed certificate (valid for 1 year)
echo "Generating self-signed certificate..."
openssl x509 -req -in $SSL_DIR/$CERT_NAME.csr -signkey $SSL_DIR/$CERT_NAME.key -out $SSL_DIR/$CERT_NAME.crt -days 365 -extensions v3_req -extfile <(cat <<EOF
[v3_req]
subjectAltName = @alt_names
[alt_names]
DNS.1 = $DOMAIN
DNS.2 = localhost
DNS.3 = *.fraud-detection.example.com
IP.1 = 127.0.0.1
IP.2 = ::1
EOF
)

# Generate default certificates for catch-all server
echo "Generating default certificates..."
openssl genrsa -out $SSL_DIR/default.key 2048
openssl req -new -key $SSL_DIR/default.key -out $SSL_DIR/default.csr -subj "/C=US/ST=CA/L=San Francisco/O=Default/OU=IT/CN=default"
openssl x509 -req -in $SSL_DIR/default.csr -signkey $SSL_DIR/default.key -out $SSL_DIR/default.crt -days 365

# Set appropriate permissions
chmod 600 $SSL_DIR/*.key
chmod 644 $SSL_DIR/*.crt
chmod 600 $SSL_DIR/*.csr

# Clean up CSR files
rm $SSL_DIR/*.csr

echo "SSL certificates generated successfully!"
echo ""
echo "Files created:"
echo "  - $SSL_DIR/$CERT_NAME.key (private key)"
echo "  - $SSL_DIR/$CERT_NAME.crt (certificate)"
echo "  - $SSL_DIR/default.key (default private key)"
echo "  - $SSL_DIR/default.crt (default certificate)"
echo ""
echo "For production, replace these with Let's Encrypt certificates:"
echo "  certbot certonly --webroot -w /var/www/certbot -d $DOMAIN"
echo ""
echo "Certificate details:"
openssl x509 -in $SSL_DIR/$CERT_NAME.crt -text -noout | grep -A 2 "Subject Alternative Name"
