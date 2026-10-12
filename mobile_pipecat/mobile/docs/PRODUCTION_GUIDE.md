# Production Mode 실행 가이드

## Production Mode란?

Production mode는 프로덕션 환경에서 안전하고 효율적으로 봇을 실행하기 위한 모드입니다.

### Production Mode 특징

- **HTTPS 강제**: SSL 인증서가 필수입니다
- **디버그 모드 비활성화**: 성능 최적화 및 보안 강화
- **최소 로깅**: 불필요한 로그 출력 최소화
- **안정성 우선**: 프로덕션 환경에 적합한 설정

## 사전 준비

### 1. SSL 인증서 생성

Production mode는 HTTPS가 필수이므로 SSL 인증서가 필요합니다:

```bash
# Self-signed 인증서 생성 (개발/테스트용)
openssl req -x509 -newkey rsa:4096 -nodes -keyout key.pem -out cert.pem -days 365

# 또는 Let's Encrypt 등으로 실제 인증서 발급 (프로덕션 권장)
```

인증서 파일은 `mobile/` 디렉토리에 `key.pem`과 `cert.pem`으로 저장되어야 합니다.

### 2. 환경변수 설정

`.env` 파일에 필요한 API 키를 설정합니다:

```bash
# 필수 API 키
OPENAI_API_KEY=your_openai_api_key
SONIOX_API_KEY=your_soniox_api_key  # 또는 GOOGLE_APPLICATION_CREDENTIALS

# 선택적 설정
OPENAI_MODEL=gpt-4o
STT_PROVIDER=soniox
TTS_PROVIDER=openai
MEM0_API_KEY=your_mem0_api_key
MEM0_HOST=http://localhost:8888
```

## 실행 방법

### 기본 Production Mode 실행 (로컬)

```bash
cd mobile
./start_bot.sh --production
```

또는 짧은 옵션:

```bash
./start_bot.sh -p
```

### 성능 최적화 옵션

Production mode는 자동으로 다음 최적화를 적용합니다:
- **워커 수**: CPU 코어 수 (기본값, `--workers`로 변경 가능)
- **Access Log 비활성화**: 성능 향상
- **로그 레벨**: INFO (최소 로깅)

워커 수를 직접 지정하려면:

```bash
./start_bot.sh --production --workers 8
```

또는 환경변수로:

```bash
export UVICORN_WORKERS=8
./start_bot.sh --production
```

### Docker로 실행 (권장)

Docker를 사용하면 더 높은 성능과 안정성을 얻을 수 있습니다:

```bash
# 프로젝트 루트에서
docker build -f mobile/Dockerfile -t pipecat-mobile-bot:latest .
cd mobile
docker-compose up -d
```

자세한 내용은 [DOCKER_GUIDE.md](./DOCKER_GUIDE.md)를 참조하세요.

### Production Mode + 커스텀 포트

```bash
./start_bot.sh --production --port 8080
```

### Production Mode + 커스텀 호스트

```bash
./start_bot.sh --production --host 0.0.0.0 --port 443
```

## Production Mode vs Development Mode

| 항목 | Production Mode | Development Mode |
|------|----------------|------------------|
| HTTPS | 필수 (SSL 인증서 필요) | 선택적 |
| 디버그 모드 | 비활성화 | 활성화 가능 |
| 로깅 레벨 | 최소 (INFO 이상) | 상세 (DEBUG 포함) |
| 성능 | 최적화 | 개발 편의성 우선 |
| 보안 | 강화 | 개발 편의성 우선 |

## Production Mode 요구사항

1. **SSL 인증서**: `key.pem`과 `cert.pem` 파일이 `mobile/` 디렉토리에 있어야 합니다
2. **API 키**: 모든 필수 API 키가 `.env` 파일에 설정되어 있어야 합니다
3. **포트 접근**: 방화벽에서 선택한 포트가 열려 있어야 합니다

## Production 배포 권장사항

### 1. 프로세스 관리자 사용

Production 환경에서는 프로세스 관리자를 사용하는 것을 권장합니다:

```bash
# systemd 예시
sudo systemctl start pipecat-bot
sudo systemctl enable pipecat-bot
```

### 2. 리버스 프록시 사용

Nginx나 Apache 같은 리버스 프록시를 사용하면 더 안정적입니다:

```nginx
# Nginx 예시
server {
    listen 443 ssl;
    server_name your-domain.com;
    
    ssl_certificate /path/to/cert.pem;
    ssl_certificate_key /path/to/key.pem;
    
    location / {
        proxy_pass http://localhost:7860;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

### 3. 로깅 설정

Production 환경에서는 로그를 파일로 저장하는 것을 권장합니다:

```bash
./start_bot.sh --production 2>&1 | tee bot.log
```

또는 systemd를 사용하는 경우:

```ini
[Service]
StandardOutput=append:/var/log/pipecat/bot.log
StandardError=append:/var/log/pipecat/bot-error.log
```

### 4. 모니터링

Production 환경에서는 모니터링 도구를 사용하는 것을 권장합니다:

- CPU/메모리 사용량 모니터링
- API 응답 시간 모니터링
- 에러 로그 모니터링
- 자동 재시작 설정

## 문제 해결

### SSL 인증서 오류

```
✗ Error: Production mode requires SSL certificates (key.pem and cert.pem)
```

**해결 방법**: SSL 인증서를 생성하거나 `--http` 옵션을 사용하세요 (하지만 production에서는 권장하지 않음).

### 포트 접근 불가

포트가 이미 사용 중이거나 방화벽에서 차단된 경우:

```bash
# 포트 확인
lsof -i :7860

# 다른 포트 사용
./start_bot.sh --production --port 8080
```

## 추가 옵션

Production mode와 함께 사용할 수 있는 옵션:

```bash
# STT/TTS 제공자 변경
./start_bot.sh --production --stt google --tts cartesia

# 커스텀 호스트/포트
./start_bot.sh --production --host 0.0.0.0 --port 443
```

## 보안 권장사항

1. **실제 SSL 인증서 사용**: Self-signed 인증서는 개발용이며, 프로덕션에서는 Let's Encrypt 등 실제 인증서 사용 권장
2. **API 키 보안**: `.env` 파일 권한 설정 (`chmod 600 .env`)
3. **방화벽 설정**: 필요한 포트만 열기
4. **정기 업데이트**: 의존성 및 보안 패치 정기 업데이트

