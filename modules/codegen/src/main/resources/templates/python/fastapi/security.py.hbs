import hmac

from fastapi import Depends, HTTPException
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.config import settings

_bearer = HTTPBearer(auto_error=False)


def require_admin(
    credentials: HTTPAuthorizationCredentials | None = Depends(_bearer),
) -> None:
    token = settings.admin_token.get_secret_value() if settings.admin_token else ""
    # No token configured (the production default): the surface does not exist.
    if not token:
        raise HTTPException(status_code=404, detail="Not Found")
    if credentials is None or not hmac.compare_digest(credentials.credentials, token):
        raise HTTPException(
            status_code=401,
            detail="Unauthorized",
            headers={"WWW-Authenticate": "Bearer"},
        )
