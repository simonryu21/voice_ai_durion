# OAuth 인증 구현 가이드

## 개요

이 프로젝트는 Google, Naver, Kakao OAuth 2.0을 사용한 인증 시스템을 구현했습니다.

## 백엔드 설정

### 1. 데이터베이스 스키마 생성

먼저 PostgreSQL 데이터베이스에 users 테이블을 생성해야 합니다:

```sql
-- auth/schema.sql 파일의 내용을 실행하세요
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider VARCHAR(50) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    name VARCHAR(255),
    profile_image_url TEXT,
    user_id VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(provider, provider_user_id)
);

-- user_id: MCP 서버(gportal 등)에 X-User-Id 헤더로 전달할 값 (예: "U000001")
CREATE INDEX IF NOT EXISTS idx_users_provider_user_id ON users(provider, provider_user_id);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_user_id ON users(user_id);
```

### 2. 환경 변수 설정

`.env` 파일에 다음 환경 변수를 추가하세요:

```bash
# Database
DATABASE_URL=postgresql://user:password@localhost:5432/dbname

# JWT Settings
JWT_SECRET_KEY=your-secret-key-change-in-production
JWT_ALGORITHM=HS256
JWT_ACCESS_TOKEN_EXPIRE_MINUTES=1440

# Google OAuth
GOOGLE_CLIENT_ID=your_google_client_id
GOOGLE_CLIENT_SECRET=your_google_client_secret
GOOGLE_REDIRECT_URI=http://localhost:7860/api/auth/google/callback

# Naver OAuth
NAVER_CLIENT_ID=your_naver_client_id
NAVER_CLIENT_SECRET=your_naver_client_secret
NAVER_REDIRECT_URI=http://localhost:7860/api/auth/naver/callback

# Kakao OAuth
KAKAO_CLIENT_ID=your_kakao_client_id
KAKAO_CLIENT_SECRET=your_kakao_client_secret
KAKAO_REDIRECT_URI=http://localhost:7860/api/auth/kakao/callback

# Base URLs
BASE_URL=http://localhost:7860
FRONTEND_URL=http://localhost:3000

# ngrok 사용 시: OAuth 콜백 후 앱이 /client/ 에 있으므로 리다이렉트 경로 지정
# FRONTEND_URL=https://xxxx.ngrok-free.app
# OAUTH_REDIRECT_PATH=client
```

### 3. OAuth 제공자 설정

#### Google OAuth
1. [Google Cloud Console](https://console.cloud.google.com/)에서 프로젝트 생성
2. OAuth 2.0 클라이언트 ID 생성 (웹 애플리케이션)
3. **승인된 리디렉션 URI**에 콜백 URI를 추가:
   - 로컬 HTTPS: `https://localhost:7860/api/auth/google/callback`
   - **주의**: Google은 **IP 주소**(예: `https://10.54.224.30:7860/...`)를 리디렉션 URI로 허용하지 않습니다.  
     "공개 최상위 도메인(.com, .org 등) 또는 유효한 비공개 도메인"이 필요합니다.
4. **다른 기기(휴대폰 등)에서 접속**하려면 아래 중 하나를 사용하세요.
   - **호스트명 사용**: 서버 IP에 호스트명을 붙여 접속하고, 그 호스트명으로 콜백 URI 등록.
     - 예: 서버/공유기에서 `bot.local` → 10.54.224.30 DNS 설정 후, `https://bot.local:7860/client`로 접속.
     - Google Console에 `https://bot.local:7860/api/auth/google/callback` 추가.
   - **터널 서비스(ngrok 등)**: `ngrok https 7860` 등으로 공개 URL(예: `https://xxxx.ngrok-free.app`)을 만들고, 해당 콜백 URI를 Google에 등록한 뒤 그 URL로 접속.
   - **같은 기기에서만 테스트**: 브라우저를 `https://localhost:7860/client`로만 접속하면 IP 등록 없이 동작합니다.
5. `redirect_uri_mismatch` 오류 시: 브라우저 주소창의 scheme·host와 **완전히 동일한** 콜백 URI를 Console에 등록했는지 확인.

##### 안드로이드(휴대폰)에서 접속할 때 (Google은 IP 허용 안 함)

휴대폰에서 `https://10.54.224.30:7860` 같은 **IP로 접속하면** Google 로그인 redirect URI를 IP로 등록할 수 없어서 로그인이 안 됩니다. **ngrok**으로 도메인 URL을 만들어 쓰면 됩니다.

1. **ngrok 설치 및 실행** (봇 서버 돌아가는 PC에서)
   - [ngrok](https://ngrok.com/) 가입 후 설치.
   - 봇이 **이미** `https://0.0.0.0:7860` 에 떠 있는 상태에서, **새 터미널**에서:
     ```bash
     ngrok http https://localhost:7860
     ```
     또는 HTTP로만 떠 있다면: `ngrok http 7860`
   - 터미널에 나오는 **HTTPS URL**을 복사 (예: `https://abc12-def3.ngrok-free.app`).

2. **Google Console에 터널 콜백 URI 추가**
   - 승인된 리디렉션 URI에 **한 줄 추가**:
     ```
     https://<ngrok에서 나온 주소>/api/auth/google/callback
     ```
     예: `https://abc12-def3.ngrok-free.app/api/auth/google/callback`  
   - 저장.

3. **환경 변수 설정 (OAuth 콜백 후 빈 화면 방지)**
   - 봇 서버의 `.env`에 ngrok URL과 리다이렉트 경로를 설정:
     ```bash
     FRONTEND_URL=https://abc12-def3.ngrok-free.app
     OAUTH_REDIRECT_PATH=client
     ```
   - 이렇게 하면 로그인 후 `https://xxx.ngrok-free.app/client/?success=true` 로 돌아가서 앱이 정상 표시됩니다.

4. **안드로이드에서 접속**
   - 휴대폰 브라우저에서 ngrok URL로 접속:
     ```
     https://abc12-def3.ngrok-free.app/client
     ```
   - (같은 Wi‑Fi일 필요 없음. ngrok이 PC의 7860 포트를 인터넷으로 열어줌.)

5. **참고**
   - ngrok 무료 플랜은 재시작할 때마다 URL이 바뀝니다. URL이 바뀌면 Google Console의 리디렉션 URI를 새 URL에 맞게 다시 추가해야 합니다.
   - 유료 플랜이면 고정 도메인 사용 가능.

#### Naver OAuth
1. [Naver Developers](https://developers.naver.com/)에서 애플리케이션 등록
2. Callback URL에 `http://localhost:7860/api/auth/naver/callback` 추가

#### Kakao OAuth
1. [Kakao Developers](https://developers.kakao.com/)에서 애플리케이션 등록
2. Redirect URI에 `http://localhost:7860/api/auth/kakao/callback` 추가

## 프론트엔드 설정

프론트엔드는 자동으로 인증 상태를 확인하고, 로그인이 필요하면 로그인 페이지를 표시합니다.

## API 엔드포인트

### 인증 엔드포인트
- `GET /api/auth/google/login` - Google OAuth 로그인 시작
- `GET /api/auth/google/callback` - Google OAuth 콜백 처리
- `GET /api/auth/naver/login` - Naver OAuth 로그인 시작
- `GET /api/auth/naver/callback` - Naver OAuth 콜백 처리
- `GET /api/auth/kakao/login` - Kakao OAuth 로그인 시작
- `GET /api/auth/kakao/callback` - Kakao OAuth 콜백 처리
- `GET /api/auth/verify` - 현재 인증 상태 확인
- `POST /api/auth/logout` - 로그아웃

### 보호된 엔드포인트
- `POST /api/offer` - WebRTC 연결 (인증 필요)

## 동작 방식

1. 사용자가 `/api/offer`에 접근하려고 시도
2. 미들웨어가 JWT 토큰(httpOnly cookie) 확인
3. 토큰이 없거나 유효하지 않으면 401 에러 반환
4. 프론트엔드가 로그인 페이지로 리다이렉트
5. 사용자가 OAuth 제공자 선택 및 로그인
6. OAuth 콜백에서 JWT 토큰을 httpOnly cookie로 설정
7. 사용자가 다시 `/api/offer`에 접근 가능

## 보안 고려사항

- JWT 토큰은 httpOnly cookie에 저장되어 XSS 공격으로부터 보호됩니다
- 프로덕션 환경에서는 `secure=True`로 설정하여 HTTPS만 허용해야 합니다
- `JWT_SECRET_KEY`는 강력한 랜덤 문자열로 설정해야 합니다
- CSRF 보호를 위해 추가적인 보안 조치가 필요할 수 있습니다
