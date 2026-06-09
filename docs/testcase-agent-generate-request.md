# 테스트케이스 생성 에이전트 요청 명세

## 목적

테스트케이스 생성 에이전트가 선택된 생성 대상 API뿐 아니라 같은 도메인의 목록 조회 API를 함께 참고할 수 있도록 요청에 `domainApis` 컨텍스트를 포함한다.

예를 들어 선택 대상이 `POST /orders`이고 같은 도메인에 `GET /orders`, `GET /orders/{orderId}`가 있으면, 에이전트는 `domainApis`를 보고 생성한 리소스를 조회하거나 검증하는 테스트케이스 초안을 만들 수 있다.

## Endpoint

`POST /api/v1/agents/testcase/generate`

## Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `agent` | string | 아니오 | 호출 에이전트 이름. 내부 호출은 `TEST_CASE_GENERATOR`를 사용한다. |
| `requestId` | string | 아니오 | 요청 추적 ID. |
| `requestedBy` | string | 아니오 | 요청자 식별자. |
| `project` | object | 아니오 | 프로젝트/앱 컨텍스트. |
| `environment` | object | 아니오 | 실행 환경, base URL, 인증, 공통 헤더 정보. |
| `metadata` | object | 아니오 | 언어, 생성 시각, 요청 출처. |
| `generationContext` | object | 아니오 | 생성 작업 ID, 모드, 테스트 레벨, 커버리지, 사용자 요약. |
| `apis` | array | 예 | 테스트케이스를 생성할 선택 대상 API 목록. 비어 있으면 400을 반환한다. |
| `domainApis` | array | 아니오 | 선택 대상 API와 같은 도메인에 속한 API 목록. 목록 조회, 상세 조회, 정리/검증 흐름 생성을 위한 참고 컨텍스트다. |
| `existingTestCases` | array | 아니오 | 중복 방지와 커버리지 보완을 위한 기존 테스트케이스 요약. |
| `failureContext` | object | 아니오 | 실패 기반 생성 컨텍스트. 현재 `FROM_FAILURE` 모드는 지원하지 않는다. |

## `apis` / `domainApis` 항목

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `apiId` | string | FlowOps API ID 또는 endpoint 식별자. |
| `method` | string | HTTP method. 예: `GET`, `POST`. |
| `path` | string | API path. 예: `/orders/{orderId}`. |
| `domainTag` | string | API 도메인 태그. 예: `ORDERS`. |
| `request_body_schema` | object | 요청 body/parameter/header 스키마. |
| `response_schema` | object | 응답 스키마와 상태 코드 정보. |
| `expectedStatusCodes` | array[number] | 정상 응답 상태 코드 목록. |
| `errorStatusCodes` | array[number] | 오류 응답 상태 코드 목록. |
| `errorCodes` | array[string] | 응답 body에서 추출된 오류 코드 목록. |
| `authRequired` | boolean | 인증 필요 여부. |
| `deprecated` | boolean | deprecated API 여부. |

## 내부 자동 구성 규칙

1. 생성 대상 API는 기존처럼 `apis`에 담는다.
2. 각 선택 API의 `domainTag`를 수집한다.
3. 같은 앱에서 동일한 `domainTag`를 가진 API endpoint를 모두 조회해 `domainApis`에 담는다.
4. 여러 도메인이 선택되면 각 도메인의 API 목록을 합치고, `METHOD:path` 기준으로 중복을 제거한다.
5. 선택 API에 `domainTag`가 없으면 도메인 목록을 특정할 수 없으므로 선택 API 목록을 `domainApis`에 그대로 담는다.

## 예시

```json
{
  "agent": "TEST_CASE_GENERATOR",
  "requestId": "0e1b7d66-5e37-47ef-9c42-47f5b70f82a3",
  "requestedBy": "tester",
  "generationContext": {
    "generationId": "57",
    "mode": "SELECTED_APIS",
    "testLevel": "REGRESSION",
    "contextSummary": "주문 생성 테스트를 보강해줘."
  },
  "apis": [
    {
      "apiId": "2059",
      "method": "POST",
      "path": "/orders",
      "domainTag": "orders",
      "request_body_schema": {
        "type": "object"
      },
      "response_schema": {
        "status": 201
      },
      "expectedStatusCodes": [201],
      "errorStatusCodes": [400, 401],
      "errorCodes": ["ORDER-400"],
      "authRequired": true,
      "deprecated": false
    }
  ],
  "domainApis": [
    {
      "apiId": "2060",
      "method": "GET",
      "path": "/orders",
      "domainTag": "orders",
      "request_body_schema": null,
      "response_schema": {
        "status": 200
      },
      "expectedStatusCodes": [200],
      "errorStatusCodes": [],
      "errorCodes": [],
      "authRequired": null,
      "deprecated": false
    },
    {
      "apiId": "2059",
      "method": "POST",
      "path": "/orders",
      "domainTag": "orders",
      "request_body_schema": {
        "type": "object"
      },
      "response_schema": {
        "status": 201
      },
      "expectedStatusCodes": [201],
      "errorStatusCodes": [400, 401],
      "errorCodes": ["ORDER-400"],
      "authRequired": true,
      "deprecated": false
    }
  ],
  "existingTestCases": [],
  "failureContext": null
}
```
