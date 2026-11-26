package com.curtaincall.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

/** 시드 파일 실행 순서. Flyway처럼 파일 이름의 버전 번호(숫자) 순서여야 한다. 파일은 읽지 않으므로 이름만 있는 자원을 쓴다. */
class SeedFilesTest {

    @Test
    @DisplayName("시드 파일은 파일 이름 문자열이 아니라 버전 번호 순서로 실행한다(V10000은 V1001 뒤)")
    void ordersByVersionNumber() {
        Resource[] seeds = {
                named("V10000__later.sql"),
                named("V1001__seed_venues.sql"),
                named("V1000__seed_users.sql"),
                named("V1002__seed_performances.sql"),
        };

        assertThat(SeedFiles.inVersionOrder(seeds)).extracting(Resource::getFilename).containsExactly(
                "V1000__seed_users.sql", "V1001__seed_venues.sql", "V1002__seed_performances.sql", "V10000__later.sql");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"seed.sql", "V__no_version.sql", "Vx__bad.sql", "V1001_one_underscore.sql"})
    @DisplayName("버전 번호를 읽을 수 없는 시드 파일 이름은 순서를 정하지 않고 거부한다")
    void rejectsUnversionedNames(String filename) {
        assertThatThrownBy(() -> SeedFiles.inVersionOrder(named(filename)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(filename);
    }

    private static Resource named(String filename) {
        return new FileSystemResource("seed-order-test/" + filename);
    }
}
