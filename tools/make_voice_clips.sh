#!/bin/bash
# =========================================================
#  흐름 코치 - 안내 음성 파일 만들기 (macOS 전용)
#  Pleos 에뮬레이터에는 TTS 엔진이 없어서, Mac의 한국어 음성으로
#  안내 문장을 미리 녹음해 app/src/main/res/raw/ 에 넣는다.
#  문장 목록은 VoicePrompts.java 와 같아야 한다.
#
#  실행:  bash tools/make_voice_clips.sh
#  직접 녹음한 파일로 바꾸고 싶으면 같은 이름(vc_키.m4a)으로 덮어쓰면 된다.
# =========================================================
set -e
cd "$(dirname "$0")/.."
OUT="app/src/main/res/raw"
TMP="$(mktemp -d)"
mkdir -p "$OUT"

KO_VOICES="$(say -v '?' | grep -E 'ko[_-]KR' || true)"
if [ -z "$KO_VOICES" ]; then
  echo "❌ 한국어 음성이 없어요."
  echo "   시스템 설정 → 손쉬운 사용 → 읽기 및 말하기 → 시스템 음성 → 음성 관리에서"
  echo "   한국어(예: 유나)를 다운로드한 뒤 다시 실행해 주세요."
  exit 1
fi
# 고품질(Premium/Enhanced) 음성이 있으면 우선 사용
VOICE_LINE="$(echo "$KO_VOICES" | grep -iE 'premium|enhanced' | head -1)"
[ -z "$VOICE_LINE" ] && VOICE_LINE="$(echo "$KO_VOICES" | head -1)"
VOICE="$(echo "$VOICE_LINE" | sed -E 's/[[:space:]]+ko[_-]KR.*$//')"
echo "🎙  사용할 음성: $VOICE"

make_clip() {
  say -v "$VOICE" -o "$TMP/$1.aiff" "$2"
  afconvert -f m4af -d aac "$TMP/$1.aiff" "$OUT/vc_$1.m4a"
  echo "  ✓ vc_$1.m4a  \"$2\""
}
make_clip demo_start "시연 주행을 시작할게요."
make_clip console_start "콘솔 연동 주행을 시작할게요."
make_clip auto_start "주행을 시작했어요. 흐름에 맞춰 함께 갈게요."
make_clip smooth_praise "지금 흐름 잘 타고 있어요. 이대로 가면 돼요."
make_clip passing_lane_hold "추월을 마쳤다면 오른쪽 차로로 돌아가 볼까요?"
make_clip too_slow "지금 흐름보다 조금 느려요. 흐름에 맞춰 볼까요?"
make_clip too_slow_passing "흐름보다 조금 느려요. 편하게 가고 싶다면 오른쪽 차로가 좋아요."
make_clip over_limit "제한속도를 넘었어요. 속도를 조금 줄여주세요."
make_clip repeated_hard_brake "급제동이 반복되고 있어요. 앞차와 간격을 조금 더 두면 흐름이 부드러워져요."
make_clip frequent_lane_change "차로 변경이 잦아요. 한 차로를 유지하는 게 흐름에 더 좋아요."
make_clip now_smooth "지금 흐름에 잘 맞춰 달리고 있어요."
make_clip now_too_slow "지금 흐름보다 조금 느려요."
make_clip now_over_limit "지금 제한속도보다 빨라요."
make_clip now_congested "지금 도로가 정체 중이에요. 앞차와 간격만 여유 있게 두세요."
make_clip now_unknown "지금은 판단할 정보가 부족해요."
make_clip trip_end "주행이 끝났어요. 흐름 리포트를 확인해 보세요."

rm -rf "$TMP"
echo ""
echo "✅ 완료! Android Studio에서 ▶ Run 하면 에뮬레이터에서도 음성 안내가 나와요."
