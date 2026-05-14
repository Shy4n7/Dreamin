from fastapi.testclient import TestClient
from backend.app import app

client = TestClient(app)

response = client.get("/admin/users?token=shyan-admin-2025")
print(response.status_code)
print(response.text)
