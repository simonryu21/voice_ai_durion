#!/bin/bash

# 로컬 개발 환경에서 mem0 서버 실행 스크립트

# 색상 정의
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${GREEN}=== Mem0 Server 로컬 실행 스크립트 ===${NC}\n"

# .env 파일 확인
if [ ! -f .env ]; then
    echo -e "${YELLOW}⚠️  .env 파일이 없습니다.${NC}"
    echo -e "${YELLOW}다음 명령으로 .env.example을 복사하여 .env 파일을 생성하세요:${NC}"
    echo -e "${YELLOW}  cp .env.example .env${NC}"
    echo -e "${YELLOW}그리고 .env 파일을 편집하여 OPENAI_API_KEY 등을 설정하세요.${NC}\n"
    exit 1
fi

# Docker Compose로 DB만 실행 (선택사항)
echo -e "${GREEN}PostgreSQL과 Neo4j를 Docker Compose로 실행하시겠습니까? (y/n)${NC}"
read -r response
if [[ "$response" =~ ^([yY][eE][sS]|[yY])$ ]]; then
    echo -e "${GREEN}Docker Compose로 PostgreSQL과 Neo4j 시작 중...${NC}"
    docker-compose up postgres neo4j -d
    
    # DB가 준비될 때까지 대기
    echo -e "${YELLOW}PostgreSQL이 준비될 때까지 대기 중...${NC}"
    until docker-compose exec -T postgres pg_isready -U postgres > /dev/null 2>&1; do
        sleep 1
    done
    echo -e "${GREEN}✓ PostgreSQL 준비 완료${NC}"
    
    echo -e "${YELLOW}Neo4j가 준비될 때까지 대기 중...${NC}"
    sleep 5
    echo -e "${GREEN}✓ Neo4j 준비 완료${NC}\n"
fi

# Python 가상 환경 확인
if [ ! -d "../venv" ] && [ ! -d "venv" ]; then
    echo -e "${YELLOW}⚠️  가상 환경이 없습니다.${NC}"
    echo -e "${YELLOW}다음 명령으로 가상 환경을 생성하세요:${NC}"
    echo -e "${YELLOW}  python3 -m venv venv${NC}"
    echo -e "${YELLOW}  source venv/bin/activate${NC}\n"
fi

# 서버 실행
echo -e "${GREEN}서버 시작 중...${NC}"
echo -e "${GREEN}서버 URL: http://localhost:8000${NC}"
echo -e "${GREEN}API 문서: http://localhost:8000/docs${NC}\n"

uvicorn main:app --host 0.0.0.0 --port 8000 --reload

