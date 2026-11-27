package com.curtaincall.support;

import java.util.List;

import javax.sql.DataSource;

import org.springframework.core.io.Resource;

import com.curtaincall.testsupport.infra.SeedScripts;

/**
 * 시드 통합 테스트의 도우미. {@code db/seed}의 시드 파일을 버전 번호 순서대로 직접 실행한다.
 *
 * <p>test 프로필의 Flyway 위치에는 시드가 없으므로, 시드가 실제 스키마에서 실행되는지는 이렇게 확인한다(결정 0036).
 * 통합 테스트는 테스트마다 테이블을 비우므로 비운 뒤에 부른다. 실행은 E2E 초기화(spec 6.9)와 같은 운영 코드
 * {@link SeedScripts}가 한다.
 */
public final class SeedFiles {

    private SeedFiles() {
    }

    public static void apply(DataSource dataSource) {
        SeedScripts.apply(dataSource);
    }

    /** 버전 번호 오름차순. 버전 번호를 읽을 수 없는 이름이 있으면 실패한다({@link SeedScripts#inVersionOrder}). */
    static List<Resource> inVersionOrder(Resource... seeds) {
        return SeedScripts.inVersionOrder(seeds);
    }
}
