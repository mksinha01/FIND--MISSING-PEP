from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    POSTGRES_USER: str = "fmp_user"
    POSTGRES_PASSWORD: str = "fmp_pass"
    POSTGRES_DB: str = "fmp_db"
    POSTGRES_HOST: str = "localhost"
    POSTGRES_PORT: int = 5432
    
    REDIS_URL: str = "redis://localhost:6379/0"
    
    ADMIN_ENROLLMENT_KEY: str = "dev-enroll-secret-change-me"
    RTSP_SECRET_KEY: str = "32bytehexsecretforaesencryption00"
    
    UPLOAD_DIR: str = "./uploads"
    FIREBASE_CREDENTIALS_PATH: str = "./firebase-sa.json"
    AI_MODEL_DIR: str = "./ai_models"
    
    DEBUG: bool = False
    DATABASE_URL: str | None = None
    DATABASE_URL_OVERRIDE: str | None = None

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    @property
    def DATABASE_URL_ASYNC(self) -> str:
        if self.DATABASE_URL_OVERRIDE:
            return self.DATABASE_URL_OVERRIDE
        if self.DATABASE_URL:
            return self.DATABASE_URL
        return f"postgresql+asyncpg://{self.POSTGRES_USER}:{self.POSTGRES_PASSWORD}@{self.POSTGRES_HOST}:{self.POSTGRES_PORT}/{self.POSTGRES_DB}"

    @property
    def DATABASE_URL_RESOLVED(self) -> str:
        return self.DATABASE_URL_ASYNC

settings = Settings()
