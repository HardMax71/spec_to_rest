from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    database_url: str = "mysql+aiomysql://url_shortener:url_shortener@localhost:3306/url_shortener"
    base_url: str = "http://localhost:8000"
    log_level: str = "info"
    admin_token: str | None = None

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )


settings = Settings()
