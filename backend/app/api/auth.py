"""Authentication routes for Firebase Auth verification and token testing."""
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import get_current_user, get_db
from app.models.user import User
from app.schemas.user import UserResponse

router = APIRouter(prefix="/auth", tags=["auth"])


@router.post("/verify", response_model=UserResponse)
async def verify_auth_token(
    current_user: User = Depends(get_current_user),
):
    """
    Verifies the client's Firebase Auth ID token and returns the current user profile.
    Auto-provisions the user record in PostgreSQL if this is their first login.
    """
    return current_user


@router.get("/me", response_model=UserResponse)
async def get_authenticated_user(
    current_user: User = Depends(get_current_user),
):
    """Returns the profile of the currently authenticated user."""
    return current_user
