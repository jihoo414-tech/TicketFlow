# 부하 테스트 중 앱 내부 지표는 Actuator로 수집한다

부하 테스트에서 품질 기준(응답 시간·오류)을 넘었을 때 원인을 찾으려면, 컨테이너 CPU·메모리만으로는 부족하다. 요청이 DB 앞의 커넥션 풀에서 기다리는지, GC로 멈추는지는 앱 안에서만 보인다.

`spring-boot-starter-actuator`를 추가하고, `perf` 프로파일에서만 `health`, `metrics` 엔드포인트를 웹에 노출한다. 부하 테스트 중 `hikaricp.connections.active`, `hikaricp.connections.pending`, `hikaricp.connections.acquire`, `jvm.gc.pause` 등을 1초 간격으로 수집해 CSV로 남긴다. 이 지표는 병목 탐색에만 쓰고 통과/실패 판정에는 쓰지 않는다.

커넥션 풀 크기는 기본값(10)으로 먼저 측정한다. 풀이 병목인지 숫자로 확인한 뒤, 크기를 바꿔 전후를 비교한다.

**선택 이유 (사용자)**: 대화에서 AI 추천에 동의했다. 직접 쓴 이유로 바꿀 것.

## Considered Options

- **Prometheus + Grafana**: 대시보드로 보기 좋지만 컨테이너 두 개가 더 필요하다. 실행 한 번이 수십 초라 CSV로 충분하다.
- **측정하지 않음**: 기준을 넘었을 때 원인이 DB인지 풀 대기인지 구분할 수 없다.
