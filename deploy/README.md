# azit-batch 배포 / 운영

배치는 상시 구동하지 않는다. **매일 서버 cron이 컨테이너를 원샷 실행**하고, 잡이 끝나면 프로세스가 종료된다.

```
GitHub Actions(deploy, profile=dev/prod)     빌드 → Docker Hub push → 해당 환경 서버에 SSH: .env 생성 + run-job.sh 갱신 + 이미지 pull
Gabia cron(매일)                             run-job.sh memberPurgeJob baseDate=오늘
GitHub Actions(수동, profile + jobName)      SSH → run-job.sh <jobName> <parameters>
```

`run-job.sh` 는 잡 이름과 파라미터를 받는 범용 런처다. 새 배치 잡이 생겨도 cron 한 줄 / 수동 워크플로우 입력값만 바꿔 재사용한다.

RDS 인바운드가 Gabia 서버 IP로만 열려 있으므로, 실제 컨테이너 실행은 항상 Gabia 서버에서 이뤄진다. GitHub 러너는 SSH로 명령만 내린다.

---

## 1. GitHub 설정

**저장소 공통 Secrets** (Settings → Secrets → Actions):

| Secret | 설명 |
|--------|------|
| `DOCKER_USERNAME` | Docker Hub 사용자명 (이미지: `<username>/azit-batch`) |
| `DOCKER_PASSWORD` | Docker Hub Access Token |
| `DISCORD_WEBHOOK` | 배포 결과 알림용 웹훅 (notify 잡) |

**환경별 Secrets** (Settings → Environments → `dev` / `prod` 각각):

| Secret | 설명 |
|--------|------|
| `GABIA_HOST` / `GABIA_USER` / `GABIA_SSH_KEY` / `GABIA_PORT` | SSH 접속 정보 |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | 대상 RDS (dev/prod 다름) |
| `AWS_S3_ACCESS_KEY` / `AWS_S3_SECRET_KEY` | S3 |
| `KAKAO_ADMIN_KEY` | 카카오 연동 해제 |
| `APPLE_TEAM_ID` / `APPLE_SERVICE_ID` / `APPLE_KEY_ID` / `APPLE_KEY_PATH` | 애플 연동 해제 (KEY_PATH는 컨테이너 내부 경로) |
| `DISCORD_WEBHOOK_URL` | 배치 결과 알림 (컨테이너 내부) |
| `NEW_RELIC_LICENSE_KEY` | 선택 |

## 2. 서버 최초 세팅 (환경별 서버마다 1회)

```bash
# Docker 설치 후
sudo mkdir -p /opt/azit-batch/apple-keys /opt/azit-batch/logs
sudo chown -R "$USER" /opt/azit-batch

# 애플 .p8 키 배치 (파일명은 APPLE_KEY_PATH 시크릿과 일치)
cp AuthKey_XXXX.p8 /opt/azit-batch/apple-keys/
chmod 600 /opt/azit-batch/apple-keys/*.p8

# Docker Hub private 이미지면 서버에서도 로그인
docker login -u <DOCKER_USERNAME>

# 서버 타임존 KST 확인 (cron/baseDate 기준)
timedatectl | grep "Time zone"   # Asia/Seoul 권장
```

`.env` / `run-job.sh` / 이미지는 **deploy 워크플로우가 자동으로 준비**한다. 최초 1회 배포를 먼저 돌린 뒤 cron을 등록한다.

## 3. cron 등록 (최초 배포 후, 서버에서 1회)

```bash
crontab -e
```

```cron
# 매일 04:10 KST 회원 개인정보 파기 배치
# 주의: crontab에서 %는 특수문자(개행)라 date의 %F를 반드시 \%F 로 이스케이프한다.
10 4 * * * /opt/azit-batch/run-job.sh memberPurgeJob "baseDate=$(date +\%F)" >> /opt/azit-batch/logs/cron.log 2>&1
```

서버 TZ가 KST가 아니면 `CRON_TZ=Asia/Seoul` 을 crontab 상단에 추가한다.

---

## 참고사항

- **수동 실행**: Actions → *Run batch job (manual)* → `profile` + `jobName`(예: `memberPurgeJob`) + `parameters`(예: `baseDate=2026-07-15`). 범위 재처리는 `parameters`에 `baseDate=... from=... to=...`.
- **같은 파라미터 재실행 불가**: 동일 파라미터 잡은 `JobInstanceAlreadyCompleteException` 으로 거부된다. memberPurgeJob을 같은 날 재처리하려면 `from`/`to` 로 파라미터를 다르게 준다.
- **실패 감지**: 잡이 FAILED로 끝나면 컨테이너가 exit 1 로 종료(`cron.log` 확인)되고 Discord 알림이 발송된다.
- **부분 실패 재시도**: revoke/S3 오류로 스킵된 회원은 `WITHDRAWN` 으로 남아 다음 날 cron에서 자동 재시도된다.
