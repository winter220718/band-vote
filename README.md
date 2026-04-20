# 합주곡 투표 웹사이트

모바일 최적화된 합주곡 투표 사이트입니다.

## 주요 기능
- 이름 입력 후 투표 페이지 이동
- 곡명 + 유튜브 링크 목록 표시
- 체크박스로 복수 선택 후 제출
- 모바일 반응형 UI
- 관리자 화면에서 선곡 추가, 수정, 삭제
- 관리자 화면에서 투표 결과 및 제출 데이터 확인


## 무료 웹 배포 추천
IP 입력 없이 공개하려면 Render + Neon Postgres 조합이 가장 간단합니다.

### 추천 조합
- 웹앱 배포: Render
- 무료 데이터베이스: Neon Postgres

### Render 배포 방법
1. 이 프로젝트를 GitHub 저장소에 올립니다.
2. Neon에서 무료 Postgres 프로젝트를 하나 만듭니다.
3. Neon의 connection string을 복사합니다.
4. Render에서 New + > Blueprint 또는 Web Service를 선택합니다.
5. 저장소를 연결한 뒤 DATABASE_URL 값에 Neon connection string을 넣습니다.
6. 배포가 끝나면 Render가 공개 URL을 발급합니다.

### 참고 사항
- 무료 플랜은 일정 시간 미사용 시 잠들 수 있습니다.
- 로컬에서는 SQLite 파일을 계속 사용합니다.
- 배포 환경에서는 DATABASE_URL이 있으면 Postgres를 사용합니다.

## 데이터 저장 위치
- 로컬 실행: data/bandvote.db
- 배포 환경: Neon Postgres
