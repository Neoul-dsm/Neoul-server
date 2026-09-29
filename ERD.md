현재 ERD 반영 내용

공공데이터 연동은 제외하고 다음 일곱 테이블을 현재 도메인 모델로 사용한다
인증용 이메일 검증과 토큰 폐기 테이블은 기존 기능을 위해 별도로 유지한다
사람 탐지 엔티티는 HumanLog이고 유해생물 탐지 엔티티는 MarineLifeLog다
클래스와 필드 이름은 기본 명명 규칙으로 테이블과 컬럼에 연결한다
복수형 테이블 이름 지정과 기본키 및 중복 방지 제약조건 설정은 유지한다
조회 성능을 위해 직접 선언한 인덱스는 제거했다

```text
beaches
  id PK
  name
  latitude nullable
  longitude nullable

users
  id PK
  email UNIQUE
  name nullable for legacy accounts
  password hash
  role

star_beaches
  id PK
  user_id FK users.id
  beach_id FK beaches.id
  UNIQUE(user_id, beach_id)

ships
  id PK
  beach_id FK beaches.id
  code UNIQUE
  name
  latitude
  longitude
  location_received_at
  last_communication_at
  api_key_hash nullable

solar_generation_logs
  id PK
  ship_id FK ships.id
  period_started_at
  received_at
  energy_wh DECIMAL(14,3)
  UNIQUE(ship_id, period_started_at)

human_log
  id PK
  ship_id FK ships.id
  event_id nullable for legacy logs
  UNIQUE(ship_id, event_id)
  image_url
  ai_result nullable
  detected_at
  latitude
  longitude

marine_life_log
  id PK
  ship_id FK ships.id
  event_id nullable for legacy logs
  UNIQUE(ship_id, event_id)
  marine_type
  count
  detected_at
```

등록 해수욕장

사용자와 해수욕장을 등록 테이블로 연결한다
같은 사용자가 같은 해수욕장을 중복 등록할 수 없다
회원가입은 기존 요청대로 해수욕장 하나를 받아 등록 행을 생성한다
안전요원의 무인배 목록은 등록된 모든 해수욕장의 배를 반환하며 해수욕장을 지정하면 해당 해수욕장만 반환한다
관리자의 기존 조회 권한은 유지한다
등록과 해제용 외부 API는 이번 변경 범위에 포함하지 않는다
등록 관계 변경은 트랜잭션 안에서 조회한 사용자에 적용하고 변경 감지로 저장한다
이미 영속 상태인 사용자를 다시 병합하는 호출은 피한다
현재 역할은 기존 GUARD와 ADMIN이며 일반 이용자와 해양경찰 전용 가입 흐름은 별도 구현 대상이다

알림

등록 해수욕장이 하나라면 기존 알림 요청을 그대로 사용할 수 있다
여러 개라면 아래처럼 구독 대상 해수욕장을 지정한다
등록되지 않은 해수욕장의 구독은 거절하며 등록 해제 후 기존 구독도 다음 조회에서 종료한다
재연결 커서는 해당 해수욕장에서 수신한 이벤트 식별자를 사용한다

```http
GET /alerts/drowning/stream?beachId=1
GET /alerts/marineanimal/stream?beachId=1
```

사람 탐지 결과는 참과 거짓과 미제공을 구분한다
새 탐지 기록의 eventId는 배가 만든 UUID 문자열을 반드시 전달한다
서버는 UUID를 소문자 표준 형식으로 저장한다
각 탐지 테이블에서 같은 배와 같은 eventId의 재전송은 기존 기록을 반환한다
재전송으로 기존 기록을 덮어쓰거나 새 SSE 알림을 생성하지 않는다
서로 다른 배에서는 같은 eventId를 사용할 수 있다
기본키 id와 SSE 재연결 커서는 기존처럼 서버에서 생성한 로그 식별자를 사용한다
기존 데이터의 eventId는 비워 두고 새 기록부터 서비스에서 필수로 검증한다
기존 로그와 결과 없이 기록한 로그는 미제공이며 거짓으로 취급하지 않는다
알림에도 AI 판정 값을 포함한다
AI 연동부는 최종 결과를 받은 뒤 판정 값을 포함하여 record를 호출하는 방식으로 연결한다
이미 전송한 로그를 나중에 수정하는 경우에는 현재 커서 방식으로 변경 알림이 재전송되지 않는다
AI 서버 호출과 분석 작업 큐는 이번 변경에 포함하지 않는다

태양광 발전 이력

한 행은 정각부터 다음 정각까지 한 시간 동안 생산한 에너지다
측정 구간 시작 시각과 서버 수신 시각을 분리해서 지연 수신도 원래 날짜에 집계할 수 있다
종료된 구간만 저장하며 발전량 단위는 Wh이다
무인배의 순간 발전 전력은 저장하지 않으며 이력에는 구간 발전량만 저장한다
같은 배의 같은 시간 구간은 중복 저장할 수 없다
수신 재시도 시 저장된 구간과 값을 확인해서 같은 값을 중복 합산하지 않아야 한다

오늘 발전량은 한국 시간 오늘 자정부터 다음 자정 사이의 구간 발전량 합계다
최근 일주일 추이는 한국 시간 오늘과 이전 여섯 날짜로 묶어 각각 합산한다
최근 일주일 합계는 이 일곱 날짜의 합계다
일 평균의 분모는 화면 정책에 따라 일곱 날짜 또는 완전하게 수집된 날짜 수로 명시해야 한다
누락된 구간은 발전량 영과 구분하고 오늘처럼 수집 중인 날짜는 부분 집계로 표시해야 한다
태양광 이력 저장 모델과 기간 조회 저장소를 추가했으며 수집 API와 통계 API는 별도 구현 대상이다

기존 데이터베이스 이전

기본 스키마 설정을 validate로 바꾸어 테이블 이름 변경 시 빈 테이블이 자동으로 생성되는 것을 방지했다
기존 MySQL 데이터베이스는 백업하고 애플리케이션을 정지한 상태에서 아래 스크립트를 순서대로 한 번 실행한 뒤 재시작한다
첫 스크립트는 기존 테이블 이름과 외래키 컬럼을 변경하고 사용자별 기존 해수욕장을 등록 테이블로 복사한다
두 번째 스크립트는 동명이 가능한 해수욕장 이름의 기존 고유 인덱스를 제거한다
세 번째 스크립트는 무인배의 속도와 전압과 신호 세기와 배터리 잔량과 순간 발전 전력 컬럼을 삭제한다

```text
src/main/resources/db/manual/01-erd-alignment.sql
src/main/resources/db/manual/02-beach-name-index.mysql.sql
src/main/resources/db/manual/03-remove-unused-ship-measurements.sql
```

이미 이전한 데이터베이스에 첫 스크립트를 다시 실행하면 안 된다
앞의 두 스크립트를 적용한 DB는 세 번째 스크립트만 실행한다
세 번째 스크립트는 삭제할 다섯 컬럼이 있는 기존 DB에 한 번만 적용한다
현재 엔티티로 새로 생성한 DB에는 해당 컬럼이 없으므로 세 번째 스크립트를 실행하지 않는다
세 번째 스크립트를 실행하면 다섯 측정값의 기존 데이터가 삭제되므로 먼저 백업한다
MySQL의 구조 변경은 트랜잭션 롤백이 보장되지 않으므로 실패한 경우 현재 적용 상태를 확인해야 한다
기존 사용자 해수욕장 컬럼과 환경 관측 컬럼과 구역 테이블은 기존 데이터를 보존하기 위해 이전 스크립트에서 삭제하지 않는다
새 애플리케이션은 이 항목들을 사용하지 않는다
비어 있는 개발용 데이터베이스를 처음 만들 때만 DDL_AUTO를 update로 지정해 생성하고 이후 validate로 실행한다
실제 데이터베이스에는 이 작업에서 스크립트를 실행하지 않았다
공통 이전 스크립트는 H2 테스트 대상으로 포함하고 MySQL 전용 인덱스 제거 스크립트는 실제 MySQL에서 별도 검증해야 한다

운영 정보

무인배 API 키

관리자는 기존 로그인 토큰으로 배의 키를 발급하거나 교체하거나 폐기한다

```http
POST /ships/{shipId}/api-key
Authorization: Bearer ADMIN_ACCESS_TOKEN

DELETE /ships/{shipId}/api-key
Authorization: Bearer ADMIN_ACCESS_TOKEN
```

발급 응답의 data에는 shipId와 apiKey가 포함된다
원문 키는 발급 응답에서만 제공하며 배에 저장해서 사용한다
DB에는 무작위 키의 SHA256 해시만 저장한다
재발급하면 이전 키는 사용할 수 없고 폐기하면 새 키를 발급할 때까지 수신 요청이 거절된다
키가 없는 기존 배도 수신 요청이 거절된다
실제 통신은 HTTPS를 사용한다

배는 자신의 키로 위치와 통신 상태를 전송한다

```http
POST /ships/{shipId}/status
X-API-Key: ISSUED_SHIP_API_KEY
Content-Type: application/json

{
  "latitude": 35.1587,
  "longitude": 129.1604
}
```

빈 JSON 객체를 보내면 마지막 통신 시각만 갱신하고 기존 위치는 유지한다
좌표는 위도와 경도를 함께 보내며 서버에서 유효 범위를 검사한다
시각은 서버에서 기록하고 성공 응답의 data는 null이다
다른 배의 키와 잘못된 키와 폐기된 키는 HTTP 401로 거절한다
사용자 로그인 토큰만으로 배의 상태를 전송할 수 없다
배의 키로 사용자 조회 API나 키 발급 API에 접근할 수 없다
현재 장치 인증을 연결한 수신 API는 상태 수신이며 탐지와 발전량 수신은 추후 구현 대상이다

기존 DB에는 다음 스크립트를 한 번 적용한다

```text
src/main/resources/db/manual/07-ship-api-key.sql
```

새 엔티티로 생성한 DB에는 적용하지 않는다
실제 DB에는 실행하지 않았다

기존 탐지 테이블의 eventId 추가는 아래 스크립트를 한 번 적용한다

```text
src/main/resources/db/manual/05-detection-event-id.sql
```

새 엔티티로 생성한 DB에는 적용하지 않는다
실제 DB에는 실행하지 않았으며 이전 스크립트는 H2 테스트에서 확인한다

기존 DB의 추가 인덱스 제거는 아래 스크립트로 별도 적용한다

```text
src/main/resources/db/manual/04-remove-custom-indexes.mysql.sql
```

삭제 대상은 기존에 직접 선언한 탐지 로그 인덱스 두 개와 토큰 만료 시각 인덱스다
대상이 없는 새 DB에서는 아무 작업도 하지 않는다
기본키와 중복 방지 제약조건과 외래키 유지에 필요한 인덱스는 보존한다
탐지 로그 인덱스가 외래키를 지탱하는 유일한 인덱스라면 배 식별자 인덱스로 대체한다
실제 MySQL에는 실행하지 않았으며 적용 전 해당 DB에서 검증해야 한다

무인배의 식별 코드와 이름과 위치 및 수신 시각은 유지한다
위치와 수신 시각은 Ship에 직접 저장하고 발전량은 별도 이력으로 적재한다
사용하지 않는 충전 상태와 상태 측정 시각은 엔티티에서 제거했다
기존 데이터베이스의 charging_status와 status_measured_at 컬럼은 보존하며 애플리케이션에서는 사용하지 않는다
배터리 잔량과 순간 발전 전력 조회 API는 제거했다
로그의 해수욕장은 ERD처럼 무인배를 통해 조회한다
무인배의 소속을 바꾸면 이전 탐지 로그도 새 소속으로 조회되므로 재배치를 구현할 때 탐지 당시 해수욕장 보존 방식을 추가해야 한다
