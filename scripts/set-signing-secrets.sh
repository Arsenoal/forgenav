#!/usr/bin/env bash
# Sets SIGNING_IN_MEMORY_KEY* secrets on Arsenoal/forgenav from local GPG.
# Usage: ./scripts/set-signing-secrets.sh
set -euo pipefail
export PATH="$HOME/.local/bin:$PATH"
REPO="${REPO:-Arsenoal/forgenav}"
KEY_FPR="${KEY_FPR:-0DB9E12BC38324179AD85EA1F94E03F01DF1CDEB}"
KEY_ID_SHORT="${KEY_ID_SHORT:-1DF1CDEB}"

read -r -s -p "GPG passphrase for SyncForge Release key: " PASS
echo

TMP_ASC="$(mktemp)"
TMP_B64="$(mktemp)"
cleanup() { rm -f "$TMP_ASC" "$TMP_B64"; }
trap cleanup EXIT

gpg --batch --yes --pinentry-mode loopback --passphrase "$PASS" \
  --armor --export-secret-keys "$KEY_FPR" > "$TMP_ASC"

grep -q "BEGIN PGP PRIVATE KEY BLOCK" "$TMP_ASC"

base64 -w0 "$TMP_ASC" > "$TMP_B64"
gh secret set SIGNING_IN_MEMORY_KEY_B64 --repo "$REPO" < "$TMP_B64"
gh secret set SIGNING_IN_MEMORY_KEY --repo "$REPO" < "$TMP_ASC"
printf '%s' "$PASS" | gh secret set SIGNING_IN_MEMORY_KEY_PASSWORD --repo "$REPO"
printf '%s' "$KEY_ID_SHORT" | gh secret set SIGNING_IN_MEMORY_KEY_ID --repo "$REPO"

echo "Signing secrets set on $REPO:"
gh secret list --repo "$REPO"
