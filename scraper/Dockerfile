FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
# Playwright's Python package installs fine, but its browsers are only needed
# for the optional NSE fallback; run `playwright install chromium` to enable it.
RUN pip install --no-cache-dir -r requirements.txt
COPY *.py ./
EXPOSE 8000
CMD ["uvicorn", "api_server:app", "--host", "0.0.0.0", "--port", "8000"]
