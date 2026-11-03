#!/bin/bash
# 에뮬레이터 위치(GPS)를 경로 파일대로 1초에 한 점씩 옮긴다. '내비 경로 따라 시연' 테스트용.
# 경로 파일: 한 줄에 "위도,경도,속도(노트)". #으로 시작하는 줄은 설명.
# 사용: tools/play_route.sh tools/demo_routes/gyeongbu_pangyo_up.csv   (멈추기: Ctrl+C)
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
FILE="${1:?경로 파일을 지정하세요. 예) tools/play_route.sh tools/demo_routes/gyeongbu_pangyo_up.csv}"
[ -x "$ADB" ] || { echo "adb를 찾을 수 없어요: $ADB (ADB=경로 로 지정 가능)"; exit 1; }
total=$(grep -vc '^#' "$FILE")
with_speed=1
n=0
while IFS=, read -r lat lon kn; do
  case "$lat" in \#*|"") continue;; esac
  if [ $with_speed = 1 ]; then
    out=$("$ADB" emu geo fix "$lon" "$lat" 50 12 "$kn" 2>&1)
    # 오래된 에뮬레이터는 속도 값을 모른다 → 위도·경도만 보내기로 바꾼다
    case "$out" in *KO*|*rror*) with_speed=0; out=$("$ADB" emu geo fix "$lon" "$lat" 2>&1);; esac
  else
    out=$("$ADB" emu geo fix "$lon" "$lat" 2>&1)
  fi
  case "$out" in *KO*|*rror*|*"no emulator"*) echo; echo "위치 보내기 실패: $out"; exit 1;; esac
  n=$((n+1))
  printf "\r%3d/%d  %s,%s  %.0fkm/h   " "$n" "$total" "$lat" "$lon" "$(echo "$kn*1.852" | bc -l)"
  sleep 1
done < "$FILE"
echo
echo "경로 끝"
