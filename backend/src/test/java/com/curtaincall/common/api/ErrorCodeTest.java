package com.curtaincall.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ErrorCodeTest {

    /** 표의 행: | 409 | `SEAT_ALREADY_HELD` | ... */
    private static final Pattern TABLE_ROW = Pattern.compile("^\\| (\\d{3}) \\| `([A-Z_]+)` \\|", Pattern.MULTILINE);

    @Test
    @DisplayName("오류 코드와 상태는 루트 CLAUDE.md의 오류 코드 표와 같다")
    void matchesRootClaudeMdTable() throws IOException {
        // Gradle은 backend/에서 테스트를 실행한다.
        String claudeMd = Files.readString(Path.of("..", "CLAUDE.md"));
        Map<String, Integer> table = new TreeMap<>();
        Matcher row = TABLE_ROW.matcher(claudeMd);
        while (row.find()) {
            table.put(row.group(2), Integer.parseInt(row.group(1)));
        }

        Map<String, Integer> codes = Arrays.stream(ErrorCode.values())
                .collect(Collectors.toMap(Enum::name, code -> code.status().value(), (a, b) -> a, TreeMap::new));

        assertThat(table).as("CLAUDE.md 표").isNotEmpty();
        assertThat(codes).isEqualTo(table);
    }

    @Test
    @DisplayName("모든 오류 코드에 사람이 읽는 기본 설명이 있다")
    void everyCodeHasDefaultDetail() {
        assertThat(ErrorCode.values()).allSatisfy(code -> assertThat(code.defaultDetail()).isNotBlank());
    }
}
