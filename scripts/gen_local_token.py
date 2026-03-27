#!/usr/bin/env python3
"""
Generate a signed JWT for local development against the WireMock Entra stub.

The token is signed with the same private key that WireMock exposes via its
JWKS endpoint, so the running Spring application will accept it.

Usage:
    python3 scripts/gen_local_token.py [--sub SUB] [--aud AUD] [--roles ROLE1,ROLE2] [--ttl SECONDS]

Examples:
    # Default: sub=local-dev-user, aud=local-dev-audience, roles=LAA_CASEWORKER, ttl=3600s
    python3 scripts/gen_local_token.py

    # Custom subject, audience, and roles
    python3 scripts/gen_local_token.py --sub alice --aud my-api --roles LAA_CASEWORKER,LAA_ADMIN

Dependencies:
    pip3 install cryptography PyJWT
"""
import argparse
import base64
import json
import time
from pathlib import Path

try:
    from cryptography.hazmat.primitives import serialization
    from cryptography.hazmat.backends import default_backend
    import jwt  # PyJWT
except ImportError:
    print("Missing dependencies. Run:  pip3 install cryptography PyJWT")
    raise

SCRIPT_DIR = Path(__file__).parent
KEY_PATH = SCRIPT_DIR.parent / "wiremock" / "local-dev-private-key.pem"
ISSUER = "http://localhost:8181/issuer"


def main():
    parser = argparse.ArgumentParser(description="Generate a local-dev JWT")
    parser.add_argument("--sub", default="local-dev-user", help="Subject claim (default: local-dev-user)")
    parser.add_argument("--aud", default="local-dev-audience", help="Audience claim (default: local-dev-audience)")
    parser.add_argument("--roles", default="LAA_CASEWORKER", help="Comma-separated LAA_APP_ROLES (default: LAA_CASEWORKER)")
    parser.add_argument("--ttl", type=int, default=3600, help="Token lifetime in seconds (default: 3600)")
    args = parser.parse_args()

    if not KEY_PATH.exists():
        print(f"ERROR: Private key not found at {KEY_PATH}")
        print("Run the setup script first:  python3 scripts/gen_jwk.py")
        raise SystemExit(1)

    pem = KEY_PATH.read_bytes()
    private_key = serialization.load_pem_private_key(pem, password=None, backend=default_backend())

    now = int(time.time())
    payload = {
        "sub": args.sub,
        "iss": ISSUER,
        "aud": args.aud,
        "iat": now,
        "exp": now + args.ttl,
        "LAA_APP_ROLES": [r.strip() for r in args.roles.split(",") if r.strip()],
    }

    token = jwt.encode(
        payload,
        private_key,
        algorithm="RS256",
        headers={"kid": "local-dev-key"},
    )

    print(token)


if __name__ == "__main__":
    main()


