# Voxy port.9: 저장소 생성 실패 뒤 잠금 누락 수정

2026-10-13. Voxy 본체의 문제를 수정했으며 VoxyServer port.1이나 LOD Only의 생성 코드를 우회 패치하지 않았습니다. 운영 Minecraft/서버 JAR과 캐시에는 적용하지 않았습니다.

## 원인과 변경

`VoxyInstance.getOrCreate(WorldIdentifier, boolean)`은 `activeWorldLock.writeLock()`을 얻은 뒤 `createStorage` 또는 `WorldEngine` 생성이 던진 예외를 받으면 `unlockWrite`를 실행하지 않았습니다. 다음 조회/생성/종료가 같은 잠금을 기다리게 됩니다. 수정 전 저장소 생성 예외를 주입했을 때 다음 `getNullable`이 5초 타임아웃으로 실패하는 것을 확인했습니다.

- `src/main/java/me/cortex/voxy/commonImpl/VoxyInstance.java`: 잠금 해제를 `finally`로 보장합니다. WorldEngine 생성 실패 시 전달받은 저장소를 닫으며, 정리 오류는 원래 예외에 suppressed로 첨부합니다. 생성에 실패한 엔진은 활성 월드나 identifier 캐시에 등록되지 않습니다.
- `src/main/java/me/cortex/voxy/common/world/WorldEngine.java`: 성공한 생성만 자원 추적기에 등록합니다. 나머지 초기화가 실패하면 이미 만든 mapper를 정리합니다. 실패한 생성에 대한 storage 소유권은 호출자에 남습니다.
- `src/main/java/me/cortex/voxy/common/config/storage/rocksdb/RocksDBStorageBackend.java`: 네이티브 옵션/캐시를 만든 즉시 정리 목록에 등록하고, 설정/열기/초기 flush 실패 시에도 모두 정리합니다. column handle → DB → 옵션/캐시 순으로 역순 해제하며, 종료 시 WAL flush 실패가 자원 해제를 건너뛰지 않도록 합니다. 정리 중 추가 실패도 나머지 해제를 중단하지 않습니다. [RocksJava 자원 관리 문서](https://github.com/facebook/rocksdb/wiki/RocksJava-Basics).
- `gradle.properties`: 로컬 수정본을 port.9로 구분합니다. Java 17 바이트코드와 기존 공개 API/저장 형식/프로토콜을 유지합니다.
- `src/test/java/me/cortex/voxy/commonImpl/WorldCreationFailureTest.java`: 예외 이후 읽기/재시도/종료, Error 경로, mapper 실패 및 정리 실패, 실제 RocksDB 충돌 후 데이터 보존, 실패한 mapper 뒤 DB 재열기, flush 실패 뒤 DB 해제, 동시 요청 시 하나의 성공 엔진만 생성하는 7개 행동 테스트입니다.
- `verification/storage/*`, `build.gradle`: 같은 7개 테스트를 실제 remapped Fabric 서버에서 실행하는 별도 테스트 모드를 추가했습니다. 테스트 모드와 JUnit은 배포용 Voxy JAR에 포함되지 않습니다.

LOCK 파일 삭제/강제 해제, 오류 숨기기, 캐시 경로 변경, 기존 캐시 이동은 없습니다. 실제 다른 DB 핸들이 저장소를 사용 중이면 열기 오류가 계속 반환됩니다. 그 핸들을 정상 종료한 뒤 호출자가 재시도하면 정상 열립니다. 자동 재시도를 추가한 것은 아닙니다.

## 검증 결과

배포용 JAR: `build/libs/voxy-0.2.20-beta+1.20.1-port.9-33c0dff7.jar`.

SHA-256: `12cb5ce5f0ef90d6e8ed095bcbdd7143f7a81286a79f6bfd78d49beffbd7228b`.

파일명의 commit 부분은 기존 port.8 기준 커밋입니다. 이번 수정은 로컬 작업 트리에 있으며 게시/태그/커밋을 만들지 않았습니다.

- Gradle build 성공. GraalVM `C:/Java/graalvm-25.4.4.1.1+1.1/bin/java.exe`에서 JUnit 9개(새 실패 처리 7개 + 기존 클라이언트 로그인 2개), 실패/오류 0. 빌드 JVM은 Java 17; 테스트 JVM만 별도 init script로 GraalVM 25.4를 선택했습니다.
- 설치 JAR의 class major version 61 확인. verification 클래스/JUnit 미포함 확인. `build/verification/artifact.json`.
- 수정 전 타임아웃 증거: `build/verification/world-creation-before.xml`. 수정 후 결과: `build/test-results/test/`.
- VoxyServer port.1 + LOD Only 0.3 + 실제 port.9 JAR + GraalVM 25.4의 격리 Fabric 서버 5회 시작/종료 PASS. 각 시작마다 같은 7개 실패 처리 테스트가 통과했습니다. 소스만 실행한 결과가 아니라 production mappings JAR을 로드한 결과입니다.
- 서버 fixture: `../voxy-lod-only/build/isolated-20261013-143133`, `phase-1.log`부터 `phase-5.log`, `result.txt`. 서버 ERROR 0, 모든 프로세스 종료 코드 0.
- LOD 변환 384개 section과 mip 비교, 취소/실행 거부/참조 해제, Overworld/Nether 저장, 원거리 vanilla region 미생성, 재시작 체크포인트와 정상 청크 마커 보존, 생성 중 취소/종료, 실제 청크 우선 경쟁 처리, 음수 좌표 재시작 PASS. 변경하지 않은 terrain 생성의 대규모 동등성 검사는 이번에 반복하지 않았습니다.
- 그래픽 서버: `../voxy-lod-only/build/isolated-graphics-storage-fix-20261013-143347`. 종료한 테스트 fixture를 새 경로로 복사했습니다. 서버에서도 실패 처리 7개 PASS.
- 그래픽 클라이언트: `../voxy-lod-only/build/graphical-client-20261013-143347` (Java 17, port.9). 프로토콜 2, 원거리 vanilla chunk 없음, Voxy non-air voxel 127,248개, geometry와 render frame 경로 활성 확인. `GRAPHICAL_TEST PASS`, 양쪽 종료 코드 0.
- 해제 후 `state=idle, inFlight=0, conversionTasks=0, error=none`. 클라이언트 `Network handle null`/UNKNOWN 초기화 오류 없음. 테스트 계정의 예상 401/Realms 오류와 선택 의존성 Iris 미설치 경고는 서버/LOD 실패로 계산하지 않았습니다.
- 운영 Java 서버는 계속 실행 중이었고 종료/교체하지 않았습니다.

## 재현

빌드와 remapped 테스트 모드:

```powershell
$env:JAVA_HOME = 'C:\Java\graalvm-jdk-17.0.19+9.1'
.\gradlew.bat build remapStorageVerificationJar
```

`../voxy-lod-only`에서 기존 검증 runner를 사용합니다. runner에 추가한 환경변수는 지정하지 않으면 원래 libs/voxy.jar로 실행됩니다.

```powershell
$env:VOXY_TEST_JAR = 'C:\Users\maste\Desktop\tool\Vibe\voxy 1.20.1\build\libs\voxy-0.2.20-beta+1.20.1-port.9-33c0dff7.jar'
$env:VOXY_STORAGE_TEST_JAR = 'C:\Users\maste\Desktop\tool\Vibe\voxy 1.20.1\build\verification\voxy-storage-verification.jar'
$env:VOXY_LOD_TEST_JAVA = 'C:\Java\graalvm-25.4.4.1.1+1.1\bin\java.exe'
.\build\benchmark-venv\Scripts\python.exe verification/run_server.py --skip-parallel
```

## 별도로 남은 범위

이전 실행에서 최초 RocksDB LOCK 열기 실패를 일으킨 파일 시스템 원인은 여전히 미확정입니다. 이번 수정은 그 오류가 발생해도 Voxy가 내부 잠금을 영구 점유하거나 초기화 자원을 남기지 않도록 합니다. 테스트에서 DB를 열 수 없는 상태를 강제로 만들고 회복을 확인했지만, 최초 Windows 파일 오류가 절대로 발생하지 않는다는 뜻은 아닙니다.

Chunky 대비 성능, 모든 시드/모드 장식의 FULL 동등성, 전체 Prominence 장시간 TPS/메모리 및 FPS는 이번 수정으로 검증했다고 주장하지 않습니다. 운영 적용은 대상 게임과 서버가 종료된 뒤 별도로 진행해야 합니다.
