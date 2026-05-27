# HabitLoop (P18)

Loop Habit Tracker fork with Supabase sync for AI agent access.

## Habit CLI

Use `tools/habits` to read/write the user's habit data. Setup is automatic.

### Auto-setup

If `tools/.env` doesn't exist, create it from project memory (`memory/reference_supabase.md`):
```
SUPABASE_URL=<from memory>
SUPABASE_SERVICE_ROLE_KEY=<from memory>
```

If `tools/.user` doesn't exist, the user needs to link first:
1. Run `python3 tools/habits link`
2. Give the token to the user
3. User enters it in app: Settings → Link Agent

### Commands

```bash
python3 tools/habits list              # 습관 목록
python3 tools/habits summary           # streak/완료 통계
python3 tools/habits entries --days 7  # 최근 기록
python3 tools/habits check <name> YES  # 체크 (부분 매치 가능)
python3 tools/habits check <name> NO   # 언체크
python3 tools/habits status            # 앱 vs DB 동기화 상태
python3 tools/habits coach "메시지"     # 코칭 메시지
```

### When user asks about habits

- "내 습관 확인해줘" → `habits summary`
- "오늘 운동 체크해" → `habits check exercise YES`
- "최근 기록 보여줘" → `habits entries`
- "앱 상태 어때" → `habits status`

### Notes

- All queries are scoped to the linked user (user_id based)
- `tools/.env` and `tools/.user` are gitignored
- service_role key is in project memory, never commit to source
