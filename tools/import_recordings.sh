#!/bin/bash
# =========================================================
#  흐름 코치 - 직접 녹음한 안내 음성을 앱에 넣기 (macOS)
#  1) HYUNDAI Project/voice_recordings/ 에 키 이름으로 저장
#     (예: passing_lane_hold.wav / .aif / .m4a / .mp3)
#  2) bash tools/import_recordings.sh
#  녹음한 파일만 바뀌고, 없는 문장은 기존 음성을 그대로 둔다.
# =========================================================
set -e
cd "$(dirname "$0")/.."
SRC="${1:-../voice_recordings}"
OUT="app/src/main/res/raw"
mkdir -p "$OUT"

KEYS="demo_start console_start smooth_praise passing_lane_hold too_slow too_slow_passing over_limit repeated_hard_brake frequent_lane_change now_smooth now_too_slow now_over_limit now_congested now_unknown trip_end"

ok=0
missing=""
for k in $KEYS; do
  f=""
  for ext in wav WAV aif aiff m4a mp3 caf; do
    for name in "$k" "vc_$k"; do
      if [ -f "$SRC/$name.$ext" ]; then f="$SRC/$name.$ext"; break 2; fi
    done
  done
  if [ -n "$f" ]; then
    afconvert -f m4af -d aac -c 1 "$f" "$OUT/vc_$k.m4a"
    echo "  ✓ $k  ← $(basename "$f")"
    ok=$((ok+1))
  else
    missing="$missing $k"
  fi
done

echo ""
echo "✅ 직접 녹음 $ok개 적용"
if [ -n "$missing" ]; then
  echo "⏭  아직 녹음 안 한 문장(기존 음성 유지):$missing"
fi
echo "Android Studio에서 ▶ Run 하면 바로 들을 수 있어요."
