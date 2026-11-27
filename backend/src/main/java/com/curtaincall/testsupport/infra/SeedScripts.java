package com.curtaincall.testsupport.infra;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/**
 * {@code db/seed}의 시드 파일을 버전 번호 순서(V1000__, V1001__, ...)대로 직접 실행한다.
 *
 * <p>local·e2e에서는 Flyway가 시드를 처음 한 번 넣는다. Flyway는 이미 적용한 버전 마이그레이션을 다시 실행하지 않으므로, 시드를 다시 넣을 때는
 * 같은 파일을 이렇게 직접 실행한다(결정 0036, 0049). E2E 초기화(spec 6.9)와, 시드를 넣지 않는 test 프로필의 시드 통합 테스트가 쓴다.
 * 테이블을 비운 뒤에 부른다.
 *
 * <p>진행 중인 트랜잭션이 있으면 그 연결에서 실행한다({@link ResourceDatabasePopulator#execute}는 DataSourceUtils로 연결을 얻는다).
 *
 * <p>순서는 Flyway처럼 파일 이름의 버전 번호를 숫자로 비교한다. 문자열로 비교하면 자릿수가 다를 때(V10000__과 V1001__)
 * Flyway와 순서가 달라진다.
 */
public final class SeedScripts {

    private static final String LOCATION = "classpath:db/seed/*.sql";

    /** Flyway 버전 마이그레이션 이름 중 시드가 쓰는 꼴: V{정수}__{설명}.sql */
    private static final Pattern VERSIONED = Pattern.compile("V(\\d+)__.+\\.sql");

    private SeedScripts() {
    }

    public static void apply(DataSource dataSource) {
        Resource[] seeds;
        try {
            seeds = new PathMatchingResourcePatternResolver().getResources(LOCATION);
        } catch (IOException e) {
            throw new UncheckedIOException("시드 파일을 찾지 못했다: " + LOCATION, e);
        }
        if (seeds.length == 0) {
            throw new IllegalStateException("db/seed에 시드 파일이 없다");
        }
        new ResourceDatabasePopulator(inVersionOrder(seeds).toArray(Resource[]::new)).execute(dataSource);
    }

    /**
     * 버전 번호 오름차순. 버전 번호를 읽을 수 없는 이름이 있으면 순서를 정할 수 없으므로 실패한다.
     * 정렬은 원소가 하나면 비교 함수를 부르지 않으므로, 이름 검사는 정렬 전에 모든 파일에 한다.
     */
    public static List<Resource> inVersionOrder(Resource... seeds) {
        record Versioned(BigInteger version, Resource seed) {
        }
        return Arrays.stream(seeds)
                .map(seed -> new Versioned(versionOf(seed), seed))
                .sorted(Comparator.comparing(Versioned::version))
                .map(Versioned::seed)
                .toList();
    }

    private static BigInteger versionOf(Resource seed) {
        String filename = seed.getFilename();
        Matcher matcher = VERSIONED.matcher(filename == null ? "" : filename);
        if (!matcher.matches()) {
            throw new IllegalStateException("시드 파일 이름에서 버전 번호를 읽을 수 없다: " + filename);
        }
        return new BigInteger(matcher.group(1));
    }
}
