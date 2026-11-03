# 흐름 코치 (가칭, FlowCoach)

> "지금 내 운전, 흐름에 맞을까?" 내 속도와 차로 습관이 교통 흐름에 맞는지 주행 중에는 음성으로, 주행 뒤에는 리포트로 알려주는 차량용 흐름 코치

현대자동차그룹 차량용 앱 개발 공모전 출품작 · Pleos Connect(Android Automotive) 앱 · Java

## 왜 필요한가

고속도로에서 운전자는 내 속도가 주변 흐름에 맞는지 알기 어렵습니다. 그래서 이런 순간이 생깁니다.

- 흐름보다 혼자 많이 느리거나 빨라서 뒤차가 급하게 줄이거나 끼어듭니다.
- 추월을 마치고도 1차로(추월차로)에 계속 머물러 뒤차가 바짝 따라붙습니다.
- 정체 구간에서 급제동을 반복해 동승자가 불편하고, 뒤로 정체가 번집니다.

흐름 코치는 차량 신호(차속·방향지시등·브레이크·기어)와 지금 위치의 실시간 교통 흐름을 함께 보고, 그 순간에 맞는 한 문장을 음성으로 알려줍니다.

## 스마트폰 앱과 다른 점

| 필요한 정보 | 흐름 코치(차량) | 스마트폰 |
|---|---|---|
| 실제 차속 | 차량 센서 값(`PERF_VEHICLE_SPEED`) | GPS로 추정 |
| 방향지시등 → 차로 변경 | Pleos Vehicle SDK `TurnSignal` | 알 수 없음 |
| 브레이크 페달 → 급제동 | Pleos Vehicle SDK `Brake` | 알 수 없음 |
| 기어 P/R/N/D → 자동 시작·종료 | AAOS `GEAR_SELECTION` | 알 수 없음 |
| 현재 도로 제한속도·진행 방향 | Pleos NaviHelper | 일부 |

## 핵심 기능

| 기능 | 설명 |
|---|---|
| 실시간 흐름 진단 | 내 속도를 구간 통행속도·제한속도와 비교합니다. 권장 속도는 항상 제한속도 이하이고, 도로 전체가 정체일 때는 "느리다"는 안내를 하지 않습니다. |
| ★ 차로 습관 | 방향지시등과 흐름으로 **추월 후 미복귀**, **잦은 차로 변경**을 알려줍니다. 흐름이 80km/h 미만이면 고속도로 1차로 예외 규정(도로교통법 시행규칙 별표 9)에 따라 추월차로 안내를 하지 않습니다. |
| 급감속 감지 | 급제동이 반복되면 차간 간격을 조언합니다(동승자 편안함, 유령 정체 예방). |
| 음성 중심 · 두 가지 모드 | 주행 중 화면은 게이지 하나와 버튼 하나. **코치 모드**는 음성으로, **조용한 모드**는 말 없이 화면에만, 위험(과속·급제동 반복)만 짧은 알림음으로 알려줍니다. 지속 시간·간격·쿨다운으로 잔소리를 줄였습니다. |
| D 자동 시작 · P 자동 종료 | 기어를 D로 바꾸면 저절로 시작하고, P에 5초 두면 끝내고 리포트를 띄웁니다. 0.5km 미만 짧은 주행은 리포트 없이 끝냅니다. 잠깐 P에 넣었다 다시 D로 가면 이어서 달립니다. |
| 기어 연동 | P·R·N에서는 판단·음성을 멈춥니다. 주차 중 브레이크를 급제동으로 세지 않습니다. |
| 주행 후 리포트 | 흐름 점수(링), 흐름에 맞춘 시간 비율, **점수에서 빠진 것**(항목별 감점), 가장 많이 깎인 항목으로 고른 **다음 주행 한 가지**. 정차 중에만 보이고, 새 주행이 시작되면 저절로 닫힙니다. |
| 백그라운드 동작 | 판단·음성은 화면이 아닌 `CoachRuntime`이 들고 있고, 포그라운드 서비스(`CoachService`)가 앱을 유지합니다. 내비를 앞에 띄워 둬도 코칭과 D 자동 시작이 동작합니다. 위험 경고는 화면이 안 보일 때 잠깐 상단 알림으로 뜹니다. |

화면은 내비 옆 1:1 반쪽 화면(세로)과 앱 단독 화면(가로)을 모두 지원하고, 시스템 설정에 따라 주간(밝은) / 야간(어두운) 테마로 바뀝니다.

## 사용한 SDK · API

| 출처 | API | 권한 | 용도 |
|---|---|---|---|
| Pleos Vehicle SDK | `TurnSignal.registerTurnSignalState` | `pleos.car.permission.CAR_LIGHTS` | 차로 변경 감지 |
| Pleos Vehicle SDK | `Brake.registerBrakePedalStatus` | `pleos.car.permission.CAR_DRIVE` | 급감속 감지 |
| Pleos Vehicle SDK | `DrivingMode.registerCruiseControlTargetSpeed` | `pleos.car.permission.CAR_DRIVE` | 콘솔 연동 주행(차속 신호가 없을 때 대신 계산) |
| Pleos NaviHelper | `getCurrentLocationInfo`, `onCurrentRoadSpeedLimit` | `pleos.car.permission.NAVI_ROUTE` | 현재 위치·진행 방향, 현재 도로 제한속도 |
| AAOS Car API | `PERF_VEHICLE_SPEED` | `android.car.permission.CAR_SPEED` | 실제 차속(Pleos SDK에 차속 API가 없어 표준 속성 사용) |
| AAOS Car API | `GEAR_SELECTION` | `android.car.permission.CAR_POWERTRAIN` | 기어: 코칭 쉼, D 자동 시작·P 자동 종료(Pleos Gear API는 deprecated) |
| 국가교통정보센터 OpenAPI | `trafficInfo` | 인증키 | 현재 위치 주변 도로의 실시간 통행속도 |
| 카카오 로컬 API | 키워드 장소 검색 | REST 키 | 실제 도로 시연 '내 경로'의 출발·도착 검색 |
| OSRM(공개 서버) | 경로 탐색 | 없음 | '내 경로'의 실제 도로 좌표·도로 이름 |

TextToSpeech·SpeechToText SDK는 아직 준비 중이라 Android 기본 API(TextToSpeech, SpeechRecognizer)로 같은 역할을 구현했고, 인터페이스(`Speaker`, `VoiceQuery`, `VehicleSignalSource`)로 분리해 SDK가 열리면 구현체만 바꿉니다. 에뮬레이터에는 TTS 엔진이 없어서 Mac 음성으로 미리 녹음한 파일(`res/raw/vc_*.m4a`)로 안내합니다.

## 시연 방법

주차(P) 중에 **[시연 주행]**을 누르면 시연 종류를 고릅니다. 시연 중에는 화면 위에 "실제 주행이 아닌, 시연 주행입니다"가 표시됩니다.

| 시연 | 설명 |
|---|---|
| 장면별 시연 | 흐름에 맞음 · 추월 후 미복귀 · 흐름보다 느림 · 급제동 반복 · 잦은 차로 변경 · 제한속도 초과를 하나씩 짧게 보여줍니다. |
| 실제 도로 시연 | 실제 도로 좌표 위를 1초씩 달리며, 그 위치의 **실시간 교통정보**로 판단합니다. 경로를 따라 상황이 자동 배치되고, 차로 뷰(내 차·주변 차)와 경로 미니맵이 함께 보입니다. |

실제 도로 시연 준비 화면
- **경로**: 경부고속도로 판교→양재(11.3km, 상황 6개) · 기능별 짧은 경로 6개(약 3~4km) · **내 경로**(카카오 검색으로 출발·도착을 고르면 실제 경로를 만들어, 고속도로·자동차전용도로 구간에만 상황을 놓습니다)
- **기준값**: 자동 / 실제 운전 / 보통 / 빠르게 / 직접 조절(항목별). 경로가 짧으면 '자동'이 상황이 모두 들어가는 가장 실제에 가까운 단계를 고릅니다.
- **시작 전 교통 확인**: 경로의 실제 흐름이 상황과 안 맞으면(예: 정체인데 추월 상황) 알려 주고, 실제 교통대로 갈지 시나리오 흐름으로 갈지 고릅니다.

### Virtual Console 연동
에뮬레이터 실행 후 `adb forward tcp:8088 tcp:8088` → 브라우저에서 `http://127.0.0.1:8088`
- **콘솔 연동 주행** 또는 **D 자동 시작**: Gear Selection으로 D/P, 화면 왼쪽 **ACCEL / BRAKE** 페달로 실제 차속을 바꾸면 앱이 그대로 읽습니다.
- Convenience › Turn Signal State: 좌/우 Blink → Off(1초 이상)로 차로 변경
- 시연(장면별·실제 도로) 중에는 앱이 에뮬레이터 차의 속도·기어를 직접 움직입니다. 이때 Virtual Console 창이 앞에 떠 있으면 그 창의 주행 계산과 속도가 서로 덮어써져 계기판 속도가 튑니다. **시연 중에는 Virtual Console 창을 닫거나 뒤 탭으로** 두세요(앱도 감지하면 화면에 알려 줍니다).
- 방향지시등은 시연 중 차에 보내지 않습니다(켜면 Pleos 후측방 카메라 창이 앱 화면을 가림). 시연의 방향지시등은 앱 게이지의 ◀ ▶로만 보여줍니다.

## 실행 방법

1. Android Studio(최신)에서 이 폴더를 엽니다. Gradle JDK는 21을 씁니다.
2. Pleos Connect Emulator(v2.1.0, API 34)를 설치하고 실행합니다.
   - SDK Manager → SDK Update Sites → `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml` (Apple Silicon)
   - Device Manager → Automotive → Pleos Connect v2.1.0, RAM 4GB, Cold boot
3. (선택) API 키를 `local.properties`에 넣습니다. **이 파일은 Git에 올라가지 않습니다.** 키가 없으면 가정 흐름값으로 동작합니다.
   ```
   TRAFFIC_API_KEY=국가교통정보센터_인증키
   KAKAO_REST_API_KEY=카카오_REST_API_키
   ```
   카카오 키는 카카오 디벨로퍼스 앱 설정에서 **카카오맵 사용 설정을 켜야** 장소 검색이 됩니다.
4. (선택) 안내 음성 파일 다시 만들기(macOS): `bash tools/make_voice_clips.sh`
5. `app`을 실행합니다. 처음 한 번 [콘솔 연동 주행]을 눌러 차량 권한을 허용하면, 그다음부터 D로 바꿀 때 저절로 시작합니다.

## 구조

```
app/src/main/java/com/giseo/flowcoach/
├─ domain/            판단 로직(순수 Java, 단위 테스트)
│   ├─ FlowAnalyzer         흐름 진단
│   ├─ LaneHabitDetector    ★ 차로 습관 감지
│   ├─ HardBrakeDetector    급감속 감지
│   ├─ CoachEngine          안내 결정(우선순위·쿨다운·모드)
│   ├─ AlertPolicy          모드별 전달 방식(음성 / 화면 / 알림음 / 상단 알림)
│   ├─ AutoDrivePolicy      D 자동 시작 · P 자동 종료
│   ├─ CoachTuning          기준값 단계(빠르게 / 보통 / 실제 운전)
│   └─ TripReport           흐름 점수 · 항목별 감점
├─ data/
│   ├─ vehicle/       차량 신호(Pleos SDK, AAOS 속성), Virtual Console 연동
│   ├─ location/      위치(NaviHelper → GPS)
│   ├─ traffic/       실시간 교통(ITS), 주변 도로 고르기, 상·하행
│   ├─ route/         실제 도로 시연: 경로·구간·상황 배치·교통 확인, 카카오 검색, OSRM
│   └─ demo/          장면별 시연 시나리오
├─ session/           1초 주기 실행(CoachRuntime, 주행·시연 세션, 포그라운드 서비스)
├─ voice/             음성 안내, 알림음, "지금 어때?" 음성 질문
└─ ui/                코치 화면(흐름 게이지·차로 뷰·미니맵), 실제 도로 시연 준비, 리포트
```

## 위치 → 도로 → 흐름 속도

1. **위치**: Pleos 내비(NaviHelper) → Android GPS 순서. 둘 다 없거나 국내 좌표가 아니면 시연 위치를 씁니다.
2. **도로 찾기**: 위치 주변 약 400m(없으면 1km)의 ITS 구간에서 도로를 고릅니다(`RoadMatcher`). 실제 도로 시연에서는 지금 구간의 도로 이름을 먼저 고르고, 시내 구간에서는 옆을 지나는 고속도로를 고르지 않습니다.
3. **방향**: 고속도로는 진행 방향이 서울 쪽이면 상행, 반대면 하행(`ExpresswayRoutes`)으로 그 방향 구간만 씁니다. 방향을 모르거나 일반도로면 양방향 구간 중앙값을 씁니다.
4. **제한속도**: 내비 값이 있으면 도로 종류 추정값 대신 씁니다.

ITS 호출은 월 한도를 아끼려고 처음 / 2km 넘게 이동 / 도로·방향이 바뀜 / 10분 경과일 때만, 1분에 한 번 이하로 합니다. 25분 넘게 갱신이 없으면 가정값으로 돌아갑니다. 확인용 스크립트 `tools/its_*.sh`는 키를 `local.properties`에서 읽고 출력하지 않습니다.

## 한계와 확장 계획

- **차로 위치**: 지금은 방향지시등으로 차로 변경을 추정합니다. 실차의 ADAS 차로 정보가 SDK로 열리면 실제 차로 위치로 판단할 수 있습니다.
- **도로 매칭**: ITS 응답에 도로 좌표가 없어 주변 도로 이름으로 추정합니다. 국토부 표준노드링크로 정확한 맵매칭을 할 계획입니다.
- **경로 탐색**: 카카오모빌리티 길찾기는 제휴가 필요해, 시연용 '내 경로'는 OSRM 공개 서버를 씁니다(실서비스에는 맞지 않음).
- **에뮬레이터 내비**: Pleos 내비는 에뮬레이터 GPS를 따라 움직이지 않아서, 실제 도로 시연은 앱 안의 차로 뷰·미니맵으로 경로를 보여줍니다.

## 테스트

```
./gradlew :app:testDebugUnitTest
```
판단 로직·교통 파싱·도로 고르기·상황 배치·리포트 감점·자동 시작/종료 규칙을 단위 테스트 108개로 확인합니다.

테스트 환경: Android Studio, AGP 8.9.0, Gradle 8.11.1, Gradle JDK 21 · compileSdk 35 / minSdk 28 / targetSdk 34 · Pleos Connect Emulator 2.1.0(Android 14, API 34) · Pleos Vehicle SDK 2.3.2
