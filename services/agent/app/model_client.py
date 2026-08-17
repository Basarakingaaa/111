import json
import httpx
from .config import settings

class ModelClient:
    async def complete_json(self, system: str, payload: dict) -> dict:
        url = settings.MODEL_BASE_URL.rstrip("/") + "/chat/completions"
        body = {
            "model": settings.MODEL_NAME,
            "response_format": {"type": "json_object"},
            "messages": [
                {"role": "system", "content": system},
                {"role": "user", "content": json.dumps(payload, ensure_ascii=False)},
            ],
            "temperature": 0.1,
        }
        headers = {"Authorization": f"Bearer {settings.MODEL_API_KEY}"}
        async with httpx.AsyncClient(timeout=60) as client:
            response = await client.post(url, json=body, headers=headers)
            response.raise_for_status()
            content = response.json()["choices"][0]["message"]["content"]
            return json.loads(content)

