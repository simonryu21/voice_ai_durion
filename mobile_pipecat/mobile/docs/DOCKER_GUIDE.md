# Docker 실행 가이드

## Docker로 Production 실행

### 1. Dockerfile 빌드

프로젝트 루트에서 빌드:

```bash
# 프로젝트 루트에서 실행
docker build -f mobile/Dockerfile -t pipecat-mobile-bot:latest .
```

### 2. Docker Compose로 실행 (권장)

```bash
# 프로젝트 루트에서 실행
cd mobile
docker-compose up -d

# 또는 프로젝트 루트에서
docker-compose -f mobile/docker-compose.yml up -d
```

### 3. 직접 Docker 실행

```bash
cd mobile
docker run -d \
  --name pipecat-bot \
  -p 7860:7860 \
  -e OPENAI_API_KEY=your_key \
  -e SONIOX_API_KEY=your_key \
  -e UVICORN_WORKERS=4 \
  -v $(pwd)/key.pem:/app/mobile/key.pem:ro \
  -v $(pwd)/cert.pem:/app/mobile/cert.pem:ro \
  pipecat-mobile-bot:latest
```

## 성능 최적화

### 워커 수 설정

기본값은 CPU 코어 수입니다. 환경변수로 조정 가능:

```bash
# docker-compose.yml에서
UVICORN_WORKERS=8

# 또는 직접 실행 시
docker run -e UVICORN_WORKERS=8 ...
```

### 리소스 제한

`docker-compose.yml`에서 리소스 제한을 조정할 수 있습니다:

```yaml
deploy:
  resources:
    limits:
      cpus: '8'      # 최대 CPU
      memory: 8G    # 최대 메모리
```

## Production Mode vs Docker

### Production Mode (로컬 실행)
- 단일 프로세스 실행
- 개발 환경에 적합
- 빠른 시작

### Docker (프로덕션 배포)
- 멀티 워커 프로세스
- 리소스 격리
- 자동 재시작
- 스케일링 용이

## 성능 비교

| 방식 | 워커 수 | 동시 처리 | 메모리 사용 | 시작 시간 |
|------|---------|-----------|------------|----------|
| 일반 실행 | 1 | 낮음 | 낮음 | 빠름 |
| Production Mode | 1 | 낮음 | 낮음 | 빠름 |
| Docker (4 workers) | 4 | 높음 | 중간 | 중간 |
| Docker (8 workers) | 8 | 매우 높음 | 높음 | 느림 |

## 권장 설정

### 소규모 배포
- 워커: 2-4개
- 메모리: 2-4GB

### 중규모 배포
- 워커: 4-8개
- 메모리: 4-8GB

### 대규모 배포
- 워커: 8-16개
- 메모리: 8-16GB
- 로드 밸런서 사용 권장

