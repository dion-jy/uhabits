# HabitLoop Agent Setup

## 1. 환경 설정

`tools/.env` 파일 생성:

```
SUPABASE_URL=https://vzhkkxqwqtqajnutpjmt.supabase.co
SUPABASE_SERVICE_ROLE_KEY=<service_role_key>
```

키는 프로젝트 메모리 `memory/reference_supabase.md` 참조.

## 2. 사용자 연결 (첫 1회)

```bash
python3 tools/habits link
```

토큰이 생성되면 앱에서 Settings → Link Agent → 토큰 입력.

## 3. 사용법

```bash
habits list              # 습관 목록
habits summary           # streak/완료 통계
habits entries --days 7  # 최근 기록
habits check Algo YES    # 습관 체크
habits check sleep NO    # 습관 언체크
habits status            # 앱 vs DB 동기화 상태
habits coach "메시지"     # 코칭 메시지 전송
habits whoami            # 연결 정보
```

## 주의

- `tools/.env`와 `tools/.user`는 gitignore됨
- service_role key는 절대 공유 금지
- 앱 재설치 시 다시 `habits link` 필요
