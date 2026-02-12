#!/usr/bin/env bash
set -euo pipefail

DURATION_SECONDS=60
POLL_INTERVAL_SECONDS=1
OUT_DIR="build/metrics"
RECORDING_NAME="app-profile"
JFR_SETTINGS="profile"
TARGET_PID=""
MATCH_PATTERN=""

usage() {
  cat <<'EOF'
Usage:
  scripts/profile_jcmd.sh [options] -- <start command>
  scripts/profile_jcmd.sh [options] --pid <pid>
  scripts/profile_jcmd.sh [options] --match <pattern>

Options:
  --duration SEC   Recording duration in seconds (default: 60)
  --poll SEC       PID polling interval in seconds (default: 0.1)
  --out DIR        Output directory (default: build/metrics)
  --name NAME      JFR recording name (default: app-profile)
  --settings NAME  JFR settings (default: profile)
  --pid PID        Attach to an existing Java PID
  --match PATTERN  Attach to first Java process whose command line matches PATTERN

Examples:
  scripts/profile_jcmd.sh --duration 30 -- ./gradlew run
  scripts/profile_jcmd.sh --out build/metrics-fast -- java -cp build/classes/java/main com.boris.fundingarbitrage.App
  scripts/profile_jcmd.sh --pid 12345
  scripts/profile_jcmd.sh --match "fundingarbitrage"
EOF
}

if [[ $# -eq 0 ]]; then
  usage
  exit 1
fi

while [[ $# -gt 0 ]]; do
  case "$1" in
    --duration)
      DURATION_SECONDS="$2"
      shift 2
      ;;
    --poll)
      POLL_INTERVAL_SECONDS="$2"
      shift 2
      ;;
    --out)
      OUT_DIR="$2"
      shift 2
      ;;
    --name)
      RECORDING_NAME="$2"
      shift 2
      ;;
    --settings)
      JFR_SETTINGS="$2"
      shift 2
      ;;
    --pid)
      TARGET_PID="$2"
      shift 2
      ;;
    --match)
      MATCH_PATTERN="$2"
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    --)
      shift
      break
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage
      exit 1
      ;;
  esac
done

if [[ -z "$TARGET_PID" && -z "$MATCH_PATTERN" && $# -eq 0 ]]; then
  echo "Missing start command after -- or attach options." >&2
  usage
  exit 1
fi

if ! command -v jcmd >/dev/null 2>&1; then
  echo "jcmd not found in PATH. Ensure a JDK is installed and active." >&2
  exit 1
fi

mkdir -p "$OUT_DIR"
RUN_LOG="$OUT_DIR/run.log"
JFR_FILE="$OUT_DIR/${RECORDING_NAME}-$(date +%Y%m%d-%H%M%S).jfr"

LAUNCHER_PID=""
JAVA_PID=""

if [[ -n "$TARGET_PID" ]]; then
  JAVA_PID="$TARGET_PID"
elif [[ -n "$MATCH_PATTERN" ]]; then
  JAVA_PID="$(pgrep -f "${MATCH_PATTERN}" | head -n1 || true)"
  if [[ -z "$JAVA_PID" ]]; then
    echo "No process matched pattern: $MATCH_PATTERN" >&2
    exit 1
  fi
else
  start_cmd=("$@")

  echo "Starting command: ${start_cmd[*]}" | tee "$RUN_LOG"
  ("${start_cmd[@]}" >> "$RUN_LOG" 2>&1) &
  LAUNCHER_PID=$!

  pick_java_pid() {
    local root_pid="$1"
    local comm
    comm="$(ps -p "$root_pid" -o comm= 2>/dev/null | awk '{$1=$1;print}')"
    if [[ "$comm" == "java" ]]; then
      echo "$root_pid"
      return 0
    fi

    local candidate
    candidate="$(pgrep -P "$root_pid" -x java 2>/dev/null | tail -n1 || true)"
    if [[ -n "${candidate:-}" ]]; then
      echo "$candidate"
      return 0
    fi

    return 1
  }

  for _ in $(seq 1 200); do
    if ! kill -0 "$LAUNCHER_PID" 2>/dev/null; then
      echo "Process exited before Java PID was detected. See $RUN_LOG" >&2
      exit 1
    fi

    if JAVA_PID="$(pick_java_pid "$LAUNCHER_PID")"; then
      break
    fi
    sleep "$POLL_INTERVAL_SECONDS"
  done

  if [[ -z "$JAVA_PID" ]]; then
    echo "Failed to locate Java process under launcher PID $LAUNCHER_PID." >&2
    echo "If your command uses a wrapper, consider passing direct java command." >&2
    kill "$LAUNCHER_PID" 2>/dev/null || true
    exit 1
  fi
fi

if ! kill -0 "$JAVA_PID" 2>/dev/null; then
  echo "Target Java PID is not running: $JAVA_PID" >&2
  exit 1
fi

echo "Profiling Java PID: $JAVA_PID"

echo "Starting JFR recording: $RECORDING_NAME" | tee -a "$RUN_LOG"
jcmd "$JAVA_PID" JFR.start name="$RECORDING_NAME" settings="$JFR_SETTINGS" filename="$JFR_FILE" >> "$RUN_LOG" 2>&1

END_EPOCH=$(( $(date +%s) + DURATION_SECONDS ))
while [[ $(date +%s) -lt "$END_EPOCH" ]]; do
  if ! kill -0 "$JAVA_PID" 2>/dev/null; then
    echo "Java process exited; stopping early." | tee -a "$RUN_LOG"
    break
  fi
  sleep 0.2
done

if kill -0 "$JAVA_PID" 2>/dev/null; then
  jcmd "$JAVA_PID" JFR.stop name="$RECORDING_NAME" >> "$RUN_LOG" 2>&1 || true
fi

echo "Finished. JFR output: $JFR_FILE"

if [[ -n "$LAUNCHER_PID" ]] && kill -0 "$LAUNCHER_PID" 2>/dev/null; then
  kill "$LAUNCHER_PID" 2>/dev/null || true
fi