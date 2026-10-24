FROM python:3.11-slim

WORKDIR /srv/ultra-backend

# Run as non-root user for container security
RUN groupadd -r ultra && useradd -r -g ultra ultra

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app
USER ultra

EXPOSE 8443
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8443"]
