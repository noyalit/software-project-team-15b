package com.software_project_team_15b.Ticketmaster.Infrastructure.Config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

/**
 * Tests for {@link DotenvEnvironmentPostProcessor#parse(Path)}: well-formed entries
 * load, and a typo (a non-blank, non-comment line that is not a valid {@code KEY=VALUE})
 * stops startup cleanly instead of being silently dropped.
 */
class DotenvEnvironmentPostProcessorTest {

    /** Sentinel thrown in place of {@code System.exit(0)} so tests can assert on the clean shutdown. */
    private static class ExitInvoked extends RuntimeException {
        ExitInvoked(String message) {
            super(message);
        }
    }

    /** Captures the failure message and avoids killing the test JVM. */
    private static class TestableProcessor extends DotenvEnvironmentPostProcessor {
        @Override
        protected void failAndExit(String message) {
            throw new ExitInvoked(message);
        }
    }

    private final TestableProcessor processor = new TestableProcessor();

    private Path writeEnv(Path dir, String content) throws IOException {
        Path file = dir.resolve(".env");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    void wellFormedFile_parsesEntries(@TempDir Path dir) throws IOException {
        Path env = writeEnv(dir, """
                # a comment
                SPRING_PROFILES_ACTIVE=inMemory
                export SERVER_PORT=8081
                QUOTED="hello world"

                """);

        Map<String, Object> values = processor.parse(env);

        assertThat(values)
                .containsEntry("SPRING_PROFILES_ACTIVE", "inMemory")
                .containsEntry("SERVER_PORT", "8081")
                .containsEntry("QUOTED", "hello world")
                .hasSize(3);
    }

    @Test
    void lineWithoutEquals_stopsStartupCleanly(@TempDir Path dir) throws IOException {
        Path env = writeEnv(dir, """
                SERVER_PORT=8081
                SPRING_PROFILES_ACTIVE inMemory
                """);

        assertThatThrownBy(() -> processor.parse(env))
                .isInstanceOf(ExitInvoked.class)
                .hasMessageContaining("line 2")
                .hasMessageContaining("SPRING_PROFILES_ACTIVE inMemory");
    }

    @Test
    void lineWithEmptyKey_stopsStartupCleanly(@TempDir Path dir) throws IOException {
        Path env = writeEnv(dir, "=oops\n");

        assertThatThrownBy(() -> processor.parse(env))
                .isInstanceOf(ExitInvoked.class)
                .hasMessageContaining("line 1");
    }

    @Test
    void unknownKey_nearCriticalKey_stopsStartupCleanly(@TempDir Path dir) throws IOException {
        // SPRING_PROFILES_ACTIVES is a misspelling of SPRING_PROFILES_ACTIVE: the real
        // key is left unset, so this must stop startup instead of silently defaulting.
        Path env = writeEnv(dir, "SPRING_PROFILES_ACTIVES=inMemory\n");

        assertThatThrownBy(() -> processor.parse(env))
                .isInstanceOf(ExitInvoked.class)
                .hasMessageContaining("SPRING_PROFILES_ACTIVES")
                .hasMessageContaining("did you mean 'SPRING_PROFILES_ACTIVE'");
    }

    @Test
    void resolvedValue_unsupported_stopsStartupCleanly() {
        // EXTERNAL_MODE only accepts fake|real; "reals" is a typo. Resolving from the
        // environment (not the file) means this also fires for Docker-injected env vars.
        MockEnvironment env = new MockEnvironment();
        env.setProperty("EXTERNAL_MODE", "reals");

        assertThatThrownBy(() -> processor.validateResolvedValues(env))
                .isInstanceOf(ExitInvoked.class)
                .hasMessageContaining("EXTERNAL_MODE")
                .hasMessageContaining("reals");
    }

    @Test
    void resolvedValue_valid_passes() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("EXTERNAL_MODE", "real");
        env.setProperty("SPRING_PROFILES_ACTIVE", "docker");

        processor.validateResolvedValues(env); // no exception
    }

    @Test
    void resolvedValue_absentCriticalKeys_passes() {
        // No critical keys set: documented defaults apply, so startup must not be blocked.
        processor.validateResolvedValues(new MockEnvironment());
    }

    @Test
    void unrelatedUnknownKeys_areAllowed(@TempDir Path dir) throws IOException {
        // Keys that are not near-misses of a critical key (POSTGRES_DB, QUOTED) load fine.
        Path env = writeEnv(dir, """
                POSTGRES_DB=ticketmaster
                QUOTED="hello world"
                """);

        Map<String, Object> values = processor.parse(env);

        assertThat(values)
                .containsEntry("POSTGRES_DB", "ticketmaster")
                .containsEntry("QUOTED", "hello world")
                .hasSize(2);
    }

    @Test
    void unreadableFile_isTreatedAsAbsent_noExit(@TempDir Path dir) {
        // A directory cannot be read as a file: parse returns empty rather than failing.
        Map<String, Object> values = processor.parse(dir);

        assertThat(values).isEmpty();
    }
}
