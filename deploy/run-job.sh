#!/usr/bin/env bash
#
# 배치 잡 범용 실행 스크립트. 잡 이름과 파라미터를 받아 컨테이너를 원샷 실행한다.
# 새 잡이 추가돼도 이 스크립트/수동 워크플로우를 그대로 재사용한다.
#
# 사용법:
#   ./run-job.sh <jobName> [param=value ...]
# 예시:
#   ./run-job.sh memberPurgeJob baseDate=2026-07-15
#   ./run-job.sh memberPurgeJob baseDate=2026-07-15 from=2026-06-01T00:00:00 to=2026-06-05T00:00:00
#
# 파일 규약 (배포 워크플로우가 생성):
#   $APP_DIR/.env         : 컨테이너 환경변수 + 배포 메타(DOCKER_USERNAME, IMAGE_TAG, TARGET_PROFILE)
#   $APP_DIR/keys/         : 소셜/인증용 키 파일(애플 .p8 등, 향후 추가 가능). 컨테이너 /app/keys 로 마운트, 수동 배치
#
set -euo pipefail

APP_DIR="/opt/azit-batch"

JOB_NAME="${1:-}"
if [ -z "$JOB_NAME" ]; then
    echo "[run-job] 사용법: run-job.sh <jobName> [param=value ...]" >&2
    exit 1
fi
shift

if [ ! -f "$APP_DIR/.env" ]; then
    echo "[run-job] $APP_DIR/.env 가 없습니다. 배포(deploy 워크플로우)를 먼저 실행하세요." >&2
    exit 1
fi

read_env() { grep -E "^$1=" "$APP_DIR/.env" | head -1 | cut -d '=' -f2- ; }
DOCKER_USERNAME="$(read_env DOCKER_USERNAME)"
IMAGE_TAG="$(read_env IMAGE_TAG)"
TARGET_PROFILE="$(read_env TARGET_PROFILE)"
IMAGE="${DOCKER_USERNAME}/azit-batch:${IMAGE_TAG:-latest}"

echo "[run-job] image=$IMAGE profile=${TARGET_PROFILE:-prod} job=$JOB_NAME params=$*"

docker run --rm \
    --env-file "$APP_DIR/.env" \
    -e SPRING_PROFILES_ACTIVE="${TARGET_PROFILE:-prod}" \
    -e NEW_RELIC_APP_NAME="azit-batch-${TARGET_PROFILE:-prod}" \
    -v "$APP_DIR/keys:/app/keys:ro" \
    "$IMAGE" \
    --spring.batch.job.name="$JOB_NAME" "$@"
