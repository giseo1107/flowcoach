#!/bin/bash
# ITS 교통소통정보: 고속도로(type=ex) 노선번호(routeNo) 형식 찾기 + 방향 정보 확인 (호출 최대 7회)
cd "$(dirname "$0")/.."
KEY="$(grep -E '^TRAFFIC_API_KEY=' local.properties | tail -1 | cut -d= -f2- | tr -d '[:space:]')"
[ -z "$KEY" ] && { echo "❌ TRAFFIC_API_KEY 없음"; exit 1; }
BOX="minX=127.08&maxX=127.12&minY=37.38&maxY=37.42"
PYF="$(mktemp)"
cat > "$PYF" <<'PY'
import json,sys,os,collections
label=os.environ["L"]
try:
    j=json.load(sys.stdin)
except Exception:
    print("%-28s -> JSON 아님" % label); sys.exit(1)
h=j.get("header",{})
body=j.get("body")
items=body.get("items",[]) if isinstance(body,dict) else []
if isinstance(items,dict): items=[items]
names=collections.Counter(i.get("roadName","") for i in items)
drc=collections.Counter(str(i.get("roadDrcType","")) for i in items)
print("%-28s -> code %s %s | %d개 | 도로 %s | 방향값 %s" % (label, h.get("resultCode"), str(h.get("resultMsg",""))[:20], len(items), dict(names.most_common(3)), dict(drc)))
by=collections.defaultdict(list)
for i in items:
    try: by[str(i.get("roadDrcType",""))].append(float(i.get("speed") or 0))
    except Exception: pass
for k,v in by.items():
    v=sorted(x for x in v if x>0)
    if v: print("      방향 %-4s 속도 %d개, 중앙값 %s, 범위 %s~%s" % (k or "''", len(v), v[len(v)//2], v[0], v[-1]))
sys.exit(0 if items else 1)
PY
FOUND=""
for R in 1 01 001 0010 10 0001; do
  BODY="$(curl -s -m 15 "https://openapi.its.go.kr:9443/trafficInfo?apiKey=$KEY&type=ex&routeNo=$R&drcType=all&$BOX&getType=json")"
  if echo "$BODY" | L="type=ex routeNo=$R" python3 "$PYF"; then FOUND="$R"; break; fi
done
if [ -n "$FOUND" ]; then
  BODY="$(curl -s -m 15 "https://openapi.its.go.kr:9443/trafficInfo?apiKey=$KEY&type=ex&routeNo=$FOUND&drcType=up&$BOX&getType=json")"
  echo "$BODY" | L="type=ex routeNo=$FOUND drcType=up" python3 "$PYF"
  echo ""; echo "✅ 노선번호 형식: routeNo=$FOUND"
else
  echo ""; echo "⚠️ 경부고속도로 노선번호 형식을 못 찾았어요"
fi
rm -f "$PYF"
