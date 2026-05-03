FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
ENV DATA_DIR=/data
RUN mkdir -p static/img instance /data/uploads /data/wa_backups
EXPOSE 5000
CMD ["gunicorn","--workers","2","--bind","0.0.0.0:5000","--timeout","120","app:app"]
