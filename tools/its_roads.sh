#!/bin/bash
# ITS 교통소통정보: 시연 위치(경부고속도로 판교 부근)에 어떤 도로가 잡히는지 확인 (호출 1회)
cd "$(dirname "$0")/.."
KEY="$(grep -E '^TRAFFIC_API_KEY=' local.properties | tail -1 | cut -d= -f2- | tr -d '[:space:]')"
[ -z "$KEY" ] && { echo "❌ TRAFFIC_API_KEY 없음"; exit 1; }
BODY="$(curl -s -m 15 "https://openapi.its.go.kr:9443/trafficInfo?apiKey=$KEY&type=all&drcType=all&minX=127.08&maxX=127.12&minY=37.38&maxY=37.42&getType=json")"
echo "$BODY" | python3 -c '
import json,sys,collections
d=json.load(sys.stdin); items=d.get("body",{}).get("items",[])
print("총", len(items), "개 구간")
c=collections.Counter(i.get("roadName","") for i in items)
print("\n[도로 이름 상위 15개]")
for n,k in c.most_common(15): print(f"  {k:4d}  {n}")
g=[i for i in items if "경부" in i.get("roadName","")]
print(f"\n[\"경부\" 포함 구간: {len(g)}개]")
for i in g[:12]: print("  ", i.get("roadName"), "| 방향", i.get("roadDrcType"), "| 속도", i.get("speed"), "| 생성", i.get("createdDate"))
sp=sorted(float(i["speed"]) for i in g if float(i.get("speed") or 0)>0)
if sp: print("\n  → 경부 구간 속도 중앙값:", sp[len(sp)//2], "km/h")
'
