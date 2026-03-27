#!/usr/bin/env python3
"""
One-off setup script: generate a local-dev RSA key pair for the WireMock Entra stub.

Run this once after cloning the repo (or whenever you want to rotate the key).
The private key and JWKS response are git-ignored and must be generated locally.

What it does:
  1. Generates a fresh 2048-bit RSA key pair
  2. Writes the private key to  wiremock/local-dev-private-key.pem
  3. Writes the public JWKS to  wiremock/__files/jwks.json

After running, start WireMock with:
    docker compose up -d

And generate tokens with:
    python3 scripts/gen_local_token.py

Dependencies:
    pip3 install cryptography
"""
import base64
import json
from pathlib import Path

try:
    from cryptography.hazmat.primitives.asymmetric import rsa
    from cryptography.hazmat.primitives import serialization
    from cryptography.hazmat.backends import default_backend
except ImportError:
    print("Missing dependency. Run:  pip3 install cryptography")
    raise

REPO_ROOT = Path(__file__).parent.parent
WIREMOCK_DIR = REPO_ROOT / "wiremock"
PRIVATE_KEY_PATH = WIREMOCK_DIR / "local-dev-private-key.pem"
JWKS_PATH = WIREMOCK_DIR / "__files" / "jwks.json"
KID = "local-dev-key"


def to_base64url(n: int) -> str:
    length = (n.bit_length() + 7) // 8
    return base64.urlsafe_b64encode(n.to_bytes(length, "big")).rstrip(b"=").decode()


def main() -> None:
    print("Generating 2048-bit RSA key pair...")
    private_key = rsa.generate_private_key(
        public_exponent=65537,
        key_size=2048,
        backend=default_backend(),
    )

    # Write private key PEM (git-ignored)
    PRIVATE_KEY_PATH.parent.mkdir(parents=True, exist_ok=True)
    pem = private_key.private_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PrivateFormat.PKCS8,
        encryption_algorithm=serialization.NoEncryption(),
    )
    PRIVATE_KEY_PATH.write_bytes(pem)
    print(f"  Written: {PRIVATE_KEY_PATH.relative_to(REPO_ROOT)}")

    # Write JWKS response body (git-ignored)
    pub_numbers = private_key.public_key().public_numbers()
    jwks = {
        "keys": [
            {
                "kty": "RSA",
                "use": "sig",
                "alg": "RS256",
                "kid": KID,
                "n": to_base64url(pub_numbers.n),
                "e": to_base64url(pub_numbers.e),
            }
        ]
    }
    JWKS_PATH.parent.mkdir(parents=True, exist_ok=True)
    JWKS_PATH.write_text(json.dumps(jwks, indent=2) + "\n")
    print(f"  Written: {JWKS_PATH.relative_to(REPO_ROOT)}")

    print("\nDone. Next steps:")
    print("  docker compose up -d")
    print("  python3 scripts/gen_local_token.py")


if __name__ == "__main__":
    main()
