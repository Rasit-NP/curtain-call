package com.curtaincall.support;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

import javax.sql.DataSource;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/**
 * {@code db/seed}의 시드 파일을 파일 이름 순서(V1000__, V1001__, ...)대로 직접 실행한다.
 *
 * <p>test 프로필의 Flyway 위치에는 시드가 없으므로, 시드가 실제 스키마에서 실행되는지는 이렇게 확인한다(결정 0036).
 * 통합 테스트는 테스트마다 테이블을 비우므로 비운 뒤에 부른다. E2E reset 컨트롤러가 할 일과 같다.
 */
public final class SeedFiles {

    private SeedFiles() {
    }

    public static void apply(DataSource dataSource) throws IOException {
        Resource[] seeds = new PathMatchingResourcePatternResolver().getResources("classpath:db/seed/*.sql");
        if (seeds.length == 0) {
            throw new IllegalStateException("db/seed에 시드 파일이 없다");
        }
        Arrays.sort(seeds, Comparator.comparing(Resource::getFilename));
        new ResourceDatabasePopulator(seeds).execute(dataSource);
    }
}
