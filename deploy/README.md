# azit-batch 배포 / 운영

배치는 상시 구동하지 않는다. **매일 서버 cron이 컨테이너를 원샷 실행**하고, 잡이 끝나면 프로세스가 종료된다.

```
GitHub Actions(deploy, profile=dev/prod)     [해당 서버의 self-hosted runner에서 직접 실행] .env 생성 + run-job.sh 설치 + docker pull
Gabia cron(매일)                             run-job.sh memberPurgeJob baseDate=오늘
GitHub Actions(수동, profile + jobName)      [해당 서버의 self-hosted runner에서 직접 실행] run-job.sh <jobName> <parameters>
```

`run-job.sh` 는 잡 이름과 파라미터를 받는 범용 런처다. 새 배치 잡이 생겨도 cron 한 줄 / 수동 워크플로우 입력값만 바꿔 재사용한다.

**SSH를 쓰지 않는다.** dev/prod 서버 각각에 GitHub Actions **self-hosted runner** 를 설치해두면, `deploy`/`run-job` 워크플로우가 그 서버 위에서 직접 실행된다. `profile` 입력값이 곧 러너를 고르는 라벨이라(`runs-on: [self-hosted, dev]` 또는 `prod`), 원하는 서버로 자동 라우팅된다. 이 방식이면 GitHub 러너의 (매번 바뀌는) IP를 서버 방화벽에 허용해줄 필요가 없다.

> **보안 참고**: self-hosted runner는 보통 "외부 PR이 워크플로우를 트리거해 서버에서 임의 코드를 실행"하는 게 위험 포인트다. 여기 두 워크플로우는 모두 `workflow_dispatch`(수동 실행)만 트리거로 두고 있어 저장소에 write 권한이 있는 사람만 실행할 수 있다 — 이 구조를 유지하는 한 `pull_request` 등 외부에서 자동으로 트리거되는 이벤트를 이 러너에 연결하지 않는다.

---

## 1. self-hosted runner 등록 (환경별 서버마다 1회)

GitHub 저장소 → **Settings → Actions → Runners → New self-hosted runner** 에서 Linux/x64를 선택하면 해당 서버에서 실행할 등록 명령이 표시된다. 그대로 실행하되, `./config.sh` 단계에서 **환경에 맞는 라벨을 반드시 지정**한다.

```bash
mkdir actions-runner && cd actions-runner
# 아래 curl/tar 줄은 GitHub UI가 보여주는 최신 버전 명령을 그대로 사용
curl -o actions-runner-linux-x64.tar.gz -L https://github.com/actions/runner/releases/download/vX.X.X/actions-runner-linux-x64-X.X.X.tar.gz
tar xzf ./actions-runner-linux-x64.tar.gz

# dev 서버라면 --labels dev, prod 서버라면 --labels prod
./config.sh --url https://github.com/<org>/azit-batch --token <GitHub UI가 발급한 토큰> --labels dev --name azit-batch-dev-runner

# 서비스로 등록해 재부팅 후에도 자동 시작
sudo ./svc.sh install
sudo ./svc.sh start
```

러너를 실행하는 계정이 `docker` 그룹에 속해 있어야 `docker pull`/`docker run` 이 동작한다:

```bash
sudo usermod -aG docker $(whoami)
# 적용을 위해 세션 재접속 또는 서버 재부팅
```

## 2. 서버 최초 세팅 (환경별 서버마다 1회)

```bash
# Docker 설치 후
sudo mkdir -p /opt/azit-batch/keys /opt/azit-batch/logs
sudo chown -R "$USER" /opt/azit-batch

# 소셜/인증용 키 파일 배치 (애플 .p8 등, 향후 다른 키 추가 시 같은 디렉토리에 함께 둔다)
# 파일명은 APPLE_KEY_PATH 시크릿과 일치해야 한다
cp AuthKey_XXXX.p8 /opt/azit-batch/keys/
chmod 644 /opt/azit-batch/keys/*

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
