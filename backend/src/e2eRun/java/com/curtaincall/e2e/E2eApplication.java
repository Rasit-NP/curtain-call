package com.curtaincall.e2e;

import org.springframework.boot.SpringApplication;

import com.curtaincall.CurtainCallApplication;

/**
 * E2E용 백엔드 실행기(결정 0161, 0162). 운영 애플리케이션을 e2e 프로필로 띄우고, 실행마다 새로 만드는 PostgreSQL 컨테이너
 * ({@link E2eDatabase})에 붙인다. Flyway가 그 빈 DB에 이 체크아웃의 마이그레이션과 시드를 넣는다. 사람의 개발 DB(compose db)에는 붙지 않는다.
 *
 * <p>테스트 소스(src/test)가 아니라 따로 둔 소스 세트(src/e2eRun)다. 테스트 클래스가 클래스패스에 있으면 운영 애플리케이션의 컴포넌트 스캔에 걸려
 * 시험용 컨트롤러와 고정 시계 같은 테스트 빈이 E2E 서버에 들어간다.
 *
 * <p>e2e/playwright.config.ts가 {@code ./gradlew bootE2eRun --args='--server.address=127.0.0.1 --server.port=...'}로 띄운다.
 * 컨테이너는 애플리케이션이 끝나면 멈추고, 프로세스가 강제로 끝나도 Testcontainers의 정리 컨테이너(Ryuk)가 지운다.
 */
public final class E2eApplication {

    private E2eApplication() {
    }

    public static void main(String[] args) {
        SpringApplication.from(CurtainCallApplication::main)
                .with(E2eDatabase.class)
                .withAdditionalProfiles("e2e")
                .run(args);
    }
}
