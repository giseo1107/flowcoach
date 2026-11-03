#!/bin/bash
# ITS 교통소통정보: drcType(방향)으로 상행/하행을 나눠 받을 수 있는지 확인 (호출 3회)
cd "$(dirname "$0")/.."
KEY="$(grep -E '^TRAFFIC_API_KEY=' local.properties | tail -1 | cut -d= -f2- | tr -d '[:space:]')"
[ -z "$KEY" ] && { echo "❌ TRAFFIC_API_KEY 없음"; exit 1; }
PYF="$(mktemp)"
cat > "$PYF" <<'PY'
import json,sys,os
d=os.environ["D"]
try:
    j=json.load(sys.stdin)
except Exception:
    print("drcType=%-6s -> JSON 아님" % d); sys.exit()
h=j.get("header",{})
body=j.get("body")
items=body.get("items",[]) if isinstance(body,dict) else []
if isinstance(items,dict): items=[items]
g=[i for i in items if "경부" in i.get("roadName","")]
sp=sorted(float(i["speed"]) for i in g if float(i.get("speed") or 0)>0)
med=sp[len(sp)//2] if sp else None
code=h.get("resultCode"); msg=str(h.get("resultMsg",""))[:30]
print("drcType=%-6s -> code %s %s | 전체 %d개, 경부 %d개, 중앙값 %s" % (d, code, msg, len(items), len(g), med))
print("    경부 속도:", sp)
PY
for D in up down start; do
  BODY="$(curl -s -m 15 "https://openapi.its.go.kr:9443/trafficInfo?apiKey=$KEY&type=all&drcType=$D&minX=127.08&maxX=127.12&minY=37.38&maxY=37.42&getType=json")"
  echo "$BODY" | D="$D" python3 "$PYF"
done
rm -f "$PYF"
