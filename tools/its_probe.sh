#!/bin/bash
# =========================================================
#  ITS 교통소통정보 API 요청 형식 점검 (macOS 터미널에서 실행)
#  키는 local.properties의 TRAFFIC_API_KEY를 읽고, 화면에는 출력하지 않는다.
#  실행: bash ~/Desktop/"HYUNDAI Project"/flowcoach/tools/its_probe.sh
# =========================================================
cd "$(dirname "$0")/.."
KEY="$(grep -E '^TRAFFIC_API_KEY=' local.properties | tail -1 | cut -d= -f2- | tr -d '[:space:]')"
if [ -z "$KEY" ]; then echo "❌ local.properties에 TRAFFIC_API_KEY가 없어요"; exit 1; fi

BASE="https://openapi.its.go.kr:9443/trafficInfo"
BOX="minX=127.08&maxX=127.12&minY=37.38&maxY=37.42"   # 경부고속도로 판교 부근

try() {
  local name="$1" query="$2"
  local body code
  body="$(curl -s -m 10 -w '\n%{http_code}' "$BASE?apiKey=$KEY&$query")"
  code="$(echo "$body" | tail -1)"
  body="$(echo "$body" | sed '$d' | tr -d '\n' )"
  local msg="$(echo "$body" | grep -oE '"resultMsg" *: *"[^"]*"' | head -1)"
  local rc="$(echo "$body" | grep -oE '"resultCode" *: *"?[0-9]+"?' | head -1)"
  local cnt="$(echo "$body" | grep -oE '"speed"' | wc -l | tr -d ' ')"
  printf '%-32s HTTP %s  %s %s  speed항목 %s개\n' "$name" "$code" "$rc" "$msg" "$cnt"
  if [ "$cnt" -gt 0 ] && [ -z "$FIRST_OK" ]; then
    FIRST_OK="$name"
    echo "   ↳ 응답 앞부분: $(echo "$body" | cut -c1-300)"
  fi
}

echo "== ITS trafficInfo 요청 형식 점검 =="
try "A. 현재 앱 형식"            "type=ex&drcType=all&$BOX&getType=json"
try "B. routeNo=all 추가"        "type=ex&routeNo=all&drcType=all&$BOX&getType=json"
try "C. drcType 없이"            "type=ex&$BOX&getType=json"
try "D. type=all"               "type=all&drcType=all&$BOX&getType=json"
try "E. type=all+routeNo=all"   "type=all&routeNo=all&drcType=all&$BOX&getType=json"
try "F. XML 응답"                "type=ex&routeNo=all&drcType=all&$BOX&getType=xml"
echo ""
if [ -n "$FIRST_OK" ]; then echo "✅ 성공한 형식: $FIRST_OK"; else echo "⚠️  성공한 형식 없음 — 위 결과를 그대로 알려주세요"; fi
