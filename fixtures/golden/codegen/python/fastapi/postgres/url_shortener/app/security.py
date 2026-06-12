import hmac

from fastapi import Depends, HTTPException
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.config import settings

_bearer = HTTPBearer(auto_error=False)


def require_admin(
    credentials: HTTPAuthorizationCredentials | None = Depends(_bearer),
) -> None:
    # No token configured (the production default): the surface does not exist.
    if not settings.admin_token:
        raise HTTPException(status_code=404, detail="Not Found")
    if credentials is None or not hmac.compare_digest(
        credentials.credentials, settings.admin_token
    ):
        raise HTTPException(
            status_code=401,
            detail="Unauthorized",
            headers={"WWW-Authenticate": "Bearer"},
        )
