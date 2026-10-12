# CodeEngine
이 소스는 Android OS가 포함된 프로젝트를 하시는 분들을 위한 android reference code입니다.

## Getting started
1. JWT 로컬 서버 설정
    - ./test-server/main.exe 파일이 있는지 확인 (Linux의 경우 "./go build main.go"와 같이 빌드 후 ./main 실행).
    - 휴대폰과 JWT 서버를 실행시킨 Local Server를 같은 WiFi로 접속.
2. MongoDB 설정
    - 본인의 MongoDB 인스턴스를 생성하고 test-server/.env에 연결 정보를 설정합니다.
3. JWT 로컬 서버 실행
    - ./test-server/main.exe 실행.
4. build.gradle 변경 후 앱실행
    - ./app/build.gradle에서 BASE_URL을 실행시킨 로컬 서버 IP로 변경.
    - 앱 실행하여 JWTAuthPlugin을 통해 JWT 기능 확인.

## App Function Test
   - 앱을 실행하면 처음에 SplashActivity에서 Splash 이미지를 띄우고 메인 화면을 띄우기 전에 필요한 확인 작업을 합니다.
   - 이후 LoginActivity를 통해 JWT 서버 접속 및 token 관련 테스트를 진행합니다.
   - 로그인 후 "NEXT" 버튼을 누르거나 비로그인 시에는 "SKIP" 버튼을 눌러 다음 테스트로 진행합니다.
   - LocalDataSource 테스트를 위해 TMap의 Location 서비스를 통해 목적지의 주소 등의 정보를 가져와 room db에 
   저장하고 list up하는 테스트를 진행합니다.
   - List up된 목적지 중 아무거나 하나를 선택하면 RemoteDataSource 테스트를 위해 build.gradle의 REMOTE_TEST_URL에 
   있는 서버에 접속 후 사용자 리스트를 가져옵니다.
   - 그 중에 아무거나 하나를 선택하면 WebView JS plugin을 테스트할 수 있는 페이지로 이동합니다.
   - Local 웹 서버가 있는 경우 IP를 입력해서 테스트를 할 수도 있고 "플러그인" 버튼을 클릭해서 Plugin별 API를 
   테스트할 수도 있습니다.

