#!/usr/bin/env bash
set -euo pipefail

DURATION_SECONDS=60
SAMPLE_INTERVAL_SECONDS=1
OUT_DIR="build/metrics"

usage() {
  cat <<'EOF'
Usage:
  scripts/monitor_metrics.sh [--duration SEC] [--interval SEC] [--out DIR] -- <start command>

Examples:
  scripts/monitor_metrics.sh --duration 180 -- ./gradlew run
  scripts/monitor_metrics.sh --out build/metrics-50 -- java -cp build/classes/java/main com.boris.fundingarbitrage.App

Outputs:
  <out>/process.csv   # timestamp,cpu_pct,rss_kb,vsz_kb,threads,elapsed
  <out>/gcutil.csv    # jstat -gcutil output sampled every interval (if available)
  <out>/run.log       # stdout/stderr of started command
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
    --interval)
      SAMPLE_INTERVAL_SECONDS="$2"
      shift 2
      ;;
    --out)
      OUT_DIR="$2"
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

if [[ $# -eq 0 ]]; then
  echo "Missing start command after --" >&2
  usage
  exit 1
fi

mkdir -p "$OUT_DIR"
PROCESS_CSV="$OUT_DIR/process.csv"
GCUTIL_CSV="$OUT_DIR/gcutil.csv"
RUN_LOG="$OUT_DIR/run.log"

echo "timestamp,cpu_pct,rss_kb,vsz_kb,threads,elapsed" > "$PROCESS_CSV"
echo "timestamp,S0,S1,E,O,M,CCS,YGC,YGCT,FGC,FGCT,CGC,CGCT,GCT" > "$GCUTIL_CSV"

descendants() {
  local root="$1"
  local kids child
  kids="$(pgrep -P "$root" || true)"
  for child in $kids; do
    echo "$child"
    descendants "$child"
  done
}

pick_java_pid() {
  local root_pid="$1"
  local comm
  comm="$(ps -p "$root_pid" -o comm= 2>/dev/null | awk '{$1=$1;print}')"
  if [[ "$comm" == "java" ]]; then
    echo "$root_pid"
    return 0
  fi

  local candidate
  candidate="$(
    descendants "$root_pid" \
    | xargs -I{} sh -c 'ps -p "{}" -o comm= 2>/dev/null | grep -q "^java$" && echo "{}"' \
    | tail -n1
  )"

  if [[ -n "${candidate:-}" ]]; then
    echo "$candidate"
    return 0
  fi

  return 1
}

echo "Starting command: $*" | tee "$RUN_LOG"
("$@" >> "$RUN_LOG" 2>&1) &
LAUNCHER_PID=$!

JAVA_PID=""
for _ in $(seq 1 60); do
  if ! kill -0 "$LAUNCHER_PID" 2>/dev/null; then
    echo "Process exited before Java PID was detected. See $RUN_LOG" >&2
    exit 1
  fi

  if JAVA_PID="$(pick_java_pid "$LAUNCHER_PID")"; then
    break
  fi
  sleep 1
done

if [[ -z "$JAVA_PID" ]]; then
  echo "Failed to locate Java process under launcher PID $LAUNCHER_PID." >&2
  echo "If your command uses a wrapper, consider passing direct java command." >&2
  kill "$LAUNCHER_PID" 2>/dev/null || true
  exit 1
fi

echo "Monitoring Java PID: $JAVA_PID"
echo "Logs directory: $OUT_DIR"

HAS_JSTAT=1
if ! command -v jstat >/dev/null 2>&1; then
  HAS_JSTAT=0
  echo "jstat not found; GC metrics will be skipped." >&2
fi

START_EPOCH="$(date +%s)"
SAMPLES=$(( DURATION_SECONDS / SAMPLE_INTERVAL_SECONDS ))
if [[ "$SAMPLES" -lt 1 ]]; then
  SAMPLES=1
fi

for _ in $(seq 1 "$SAMPLES"); do
  if ! kill -0 "$JAVA_PID" 2>/dev/null; then
    echo "Java process exited; stopping monitor loop."
    break
  fi

  TS="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  PS_LINE="$(ps -p "$JAVA_PID" -o %cpu=,rss=,vsz=,nlwp=,etime= | awk '{$1=$1;print}')"

  if [[ -n "$PS_LINE" ]]; then
    CPU="$(echo "$PS_LINE" | awk '{print $1}')"
    RSS="$(echo "$PS_LINE" | awk '{print $2}')"
    VSZ="$(echo "$PS_LINE" | awk '{print $3}')"
    NLWP="$(echo "$PS_LINE" | awk '{print $4}')"
    ELAPSED="$(echo "$PS_LINE" | awk '{print $5}')"
    echo "$TS,$CPU,$RSS,$VSZ,$NLWP,$ELAPSED" >> "$PROCESS_CSV"
  fi

  if [[ "$HAS_JSTAT" -eq 1 ]]; then
    GC_LINE="$(jstat -gcutil "$JAVA_PID" 2>/dev/null | tail -n1 | awk '{$1=$1;print}')"
    if [[ -n "$GC_LINE" ]]; then
      echo "$TS,$(echo "$GC_LINE" | tr -s ' ' ',')" >> "$GCUTIL_CSV"
    fi
  fi

  sleep "$SAMPLE_INTERVAL_SECONDS"
done

if kill -0 "$LAUNCHER_PID" 2>/dev/null; then
  kill "$LAUNCHER_PID" 2>/dev/null || true
fi

echo "Finished. Collected metrics in $OUT_DIR"
