from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    model_config = SettingsConfigDict(case_sensitive=True)
    CORE_URL: str
    CORE_AGENT_TOKEN: str
    ELASTICSEARCH_URL: str
    ELASTICSEARCH_USERNAME: str
    ELASTICSEARCH_PASSWORD: str
    MODEL_PROVIDER: str
    MODEL_BASE_URL: str
    MODEL_API_KEY: str
    MODEL_NAME: str
    AGENT_MAX_CONTEXT_TOKENS: int = 30000
    APP_LOG_LEVEL: str = "INFO"

settings = Settings()

