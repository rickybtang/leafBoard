#!/bin/zsh
set -euo pipefail

IDENTITY_NAME="LeafBoard Local Signing"
LOGIN_KEYCHAIN="$HOME/Library/Keychains/login.keychain-db"

if security find-identity -v -p codesigning | grep -Fq "\"$IDENTITY_NAME\""; then
    echo "已存在代码签名身份：$IDENTITY_NAME"
    exit 0
fi

TEMP_DIR=$(mktemp -d /tmp/leafboard-local-signing.XXXXXX)

cleanup() {
    for file in "$TEMP_DIR/key.pem" "$TEMP_DIR/identity.p12"; do
        if [[ -f "$file" ]]; then
            size=$(stat -f%z "$file")
            dd if=/dev/zero of="$file" bs=1 count="$size" conv=notrunc status=none
            unlink "$file"
        fi
    done
    [[ -f "$TEMP_DIR/cert.pem" ]] && unlink "$TEMP_DIR/cert.pem"
    [[ -d "$TEMP_DIR" ]] && rmdir "$TEMP_DIR"
}
trap cleanup EXIT

P12_PASSWORD=$(openssl rand -hex 24)
openssl req -new -newkey rsa:2048 -nodes -x509 -days 3650 \
    -keyout "$TEMP_DIR/key.pem" \
    -out "$TEMP_DIR/cert.pem" \
    -subj "/CN=$IDENTITY_NAME/O=LeafBoard Local" \
    -addext "keyUsage=critical,digitalSignature" \
    -addext "extendedKeyUsage=codeSigning" >/dev/null 2>&1
openssl pkcs12 -export \
    -inkey "$TEMP_DIR/key.pem" \
    -in "$TEMP_DIR/cert.pem" \
    -name "$IDENTITY_NAME" \
    -passout "pass:$P12_PASSWORD" \
    -out "$TEMP_DIR/identity.p12"
security import "$TEMP_DIR/identity.p12" \
    -k "$LOGIN_KEYCHAIN" \
    -f pkcs12 \
    -P "$P12_PASSWORD" \
    -T /usr/bin/codesign \
    -T /usr/bin/security >/dev/null
security add-trusted-cert \
    -r trustRoot \
    -p codeSign \
    -k "$LOGIN_KEYCHAIN" \
    "$TEMP_DIR/cert.pem"

echo "已创建代码签名身份：$IDENTITY_NAME"
