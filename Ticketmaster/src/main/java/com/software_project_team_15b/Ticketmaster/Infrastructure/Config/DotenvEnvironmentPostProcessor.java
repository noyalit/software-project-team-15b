package com.software_project_team_15b.Ticketmaster.Infrastructure.Config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Loads a repository-root {@code .env} file so the same file drives Docker
 * Compose (which reads {@code .env} natively) and local {@code mvnw spring-boot:run}.
 * <p>
 * Spring Boot has no built-in {@code .env} support, so this post-processor walks
 * up from the working directory ({@code user.dir}) to find the first {@code .env}
 * and exposes its {@code KEY=VALUE} entries as a property source. The source is
 * added with the <em>lowest</em> precedence, so real OS environment variables and
 * {@code application.properties} still win — e.g. {@code INIT_RUN=true mvnw spring-boot:run}
 * overrides {@code INIT_RUN=false} from {@code .env}.
 * <p>
 * It runs before {@link ConfigDataEnvironmentPostProcessor} so the values are
 * available for profile activation (e.g. {@code spring.profiles.active=${SPRING_PROFILES_ACTIVE:...}}).
 * If no {@code .env} is found (CI, packaged jar) it is a silent no-op.
 * <p>
 * A typo in the file is a configuration mistake. Rather than silently dropping the
 * line (which hides the typo) or aborting the JVM with a failure exit code, the
 * processor reports the offending entry and stops startup <em>cleanly</em>
 * (exit code 0): the system does not start with a half-applied configuration, but
 * the process still reports success to the launcher/CI. Three classes of typo are
 * detected:
 * <ul>
 *   <li><b>Malformed line</b> — a non-blank, non-comment line that is not a valid
 *       {@code KEY=VALUE} assignment.</li>
 *   <li><b>Unknown key</b> — a key that is a near-miss of a recognised critical key
 *       (e.g. {@code SPRING_PROFILES_ACTIVES} for {@code SPRING_PROFILES_ACTIVE}). The
 *       intended key would otherwise be left unset and silently default.</li>
 *   <li><b>Unsupported value</b> — a recognised key whose value is outside its closed
 *       set (e.g. {@code EXTERNAL_MODE=reals}, where only {@code fake}/{@code real}
 *       are valid). This is checked against the <em>resolved</em> environment, so it is
 *       caught whether the value comes from the {@code .env} file (local) or from a real
 *       environment variable (the Docker stack, where Compose injects {@code .env} as
 *       container environment variables and no {@code .env} file exists inside the container).</li>
 * </ul>
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /** Property source name; also used to make the post-processor idempotent. */
    static final String PROPERTY_SOURCE_NAME = "dotenv";

    private static final String FILE_NAME = ".env";
    private static final int MAX_PARENT_LEVELS = 6;

    /**
     * Critical keys whose value is restricted to a closed set. A value outside the set
     * (e.g. {@code EXTERNAL_MODE=reals}) is a typo and stops startup cleanly. These keys
     * also drive unknown-key detection: a near-miss of one of them (e.g.
     * {@code SPRING_PROFILES_ACTIVES}) is reported so the misspelling does not silently
     * leave the real key unset. Add new closed-value keys here.
     */
    private static final Map<String, Set<String>> ENUMERATED_KEYS = Map.of(
            "SPRING_PROFILES_ACTIVE", Set.of("inMemory", "docker", "cloudsql"),
            "EXTERNAL_MODE", Set.of("fake", "real"),
            "INIT_RUN", Set.of("true", "false"));

    /** Max edit distance for a key to be treated as a misspelling of a critical key. */
    private static final int MAX_TYPO_DISTANCE = 2;
    /** Only run typo detection on reasonably long keys, to avoid short-key false positives. */
    private static final int MIN_TYPO_KEY_LENGTH = 6;

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getPropertySources().contains(propertySourceName())) {
            return;
        }
        Path envFile = locateEnvFile();
        if (envFile != null) {
            Map<String, Object> values = parse(envFile);
            if (!values.isEmpty()) {
                environment.getPropertySources().addLast(new MapPropertySource(propertySourceName(), values));
            }
        }
        // Validate the *effective* value of each critical key, whatever its source. In the
        // Docker stack the root .env is consumed by Compose and injected as real environment
        // variables, so no .env file exists inside the container — only this resolved-value
        // check catches a bad value (e.g. EXTERNAL_MODE=realq) there.
        validateResolvedValues(environment);
    }

    private Path locateEnvFile() {
        Path dir = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath();
        for (int level = 0; level <= MAX_PARENT_LEVELS && dir != null; level++) {
            Path candidate = dir.resolve(filename());
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        return null;
    }

    /**
     * Name of the {@code .env} file to load. Overridable so a test variant can load
     * {@code .env.test} instead without clobbering a developer's {@code .env}.
     */
    protected String filename() {
        return FILE_NAME;
    }

    /**
     * Name of the property source this processor registers. Overridable so a test
     * variant uses a distinct name and can coexist with the parent's source.
     */
    protected String propertySourceName() {
        return PROPERTY_SOURCE_NAME;
    }

    Map<String, Object> parse(Path envFile) {
        Map<String, Object> values = new LinkedHashMap<>();
        Map<String, Integer> lineByKey = new LinkedHashMap<>();
        List<String> lines;
        try {
            lines = Files.readAllLines(envFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // Unreadable .env should never break startup; treat as absent.
            return values;
        }
        for (int i = 0; i < lines.size(); i++) {
            String raw = lines.get(i);
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("export ")) {
                line = line.substring("export ".length()).trim();
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                // No '=' (or an empty key): the line is not a valid KEY=VALUE
                // assignment — almost certainly a typo. Report and stop cleanly.
                reportMalformedLine(envFile, i + 1, raw);
                return values; // unreachable in production; lets tests continue.
            }
            String key = line.substring(0, eq).trim();
            if (key.isEmpty()) {
                reportMalformedLine(envFile, i + 1, raw);
                return values;
            }
            values.put(key, unquote(line.substring(eq + 1).trim()));
            lineByKey.put(key, i + 1);
        }
        validate(envFile, values, lineByKey);
        return values;
    }

    /**
     * Flags a {@code .env} key that is a near-miss of a critical key (e.g.
     * {@code SPRING_PROFILES_ACTIVES} for {@code SPRING_PROFILES_ACTIVE}): the intended key
     * would otherwise be left unset and silently default. File-only, because the real key
     * name is needed to spot the misspelling — value errors are checked separately against
     * the resolved environment so they are caught in Docker too. Stops startup cleanly.
     */
    private void validate(Path envFile, Map<String, Object> values, Map<String, Integer> lineByKey) {
        for (String key : values.keySet()) {
            if (ENUMERATED_KEYS.containsKey(key)) {
                continue;
            }
            String suggestion = closestCriticalKey(key);
            if (suggestion != null) {
                reportUnknownKey(envFile, lineByKey.getOrDefault(key, 0), key, suggestion);
                return; // unreachable in production; lets tests continue.
            }
        }
    }

    /**
     * Validates the effective value of each critical key as resolved by Spring — so it
     * covers values from the {@code .env} file (local) and from real environment variables
     * (the Docker stack, where Compose injects them). A key that is present but carries a
     * value outside its closed set stops startup cleanly (exit code 0). Absent keys are fine:
     * their documented default applies.
     */
    void validateResolvedValues(ConfigurableEnvironment environment) {
        for (Map.Entry<String, Set<String>> entry : ENUMERATED_KEYS.entrySet()) {
            String key = entry.getKey();
            String value = environment.getProperty(key);
            if (value != null && !entry.getValue().contains(value)) {
                reportUnsupportedValue(key, value, entry.getValue());
                return; // unreachable in production; lets tests continue.
            }
        }
    }

    /**
     * Returns the recognised critical key that {@code key} is most likely a misspelling of,
     * or {@code null} if it is not a near-miss of any. Only long-enough keys within
     * {@link #MAX_TYPO_DISTANCE} edits are considered, so unrelated keys are left untouched.
     */
    private String closestCriticalKey(String key) {
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String known : ENUMERATED_KEYS.keySet()) {
            if (Math.max(key.length(), known.length()) < MIN_TYPO_KEY_LENGTH) {
                continue;
            }
            int distance = levenshtein(key, known);
            if (distance > 0 && distance <= MAX_TYPO_DISTANCE && distance < bestDistance) {
                best = known;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** Iterative Levenshtein edit distance between two strings. */
    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[b.length()];
    }

    private void reportMalformedLine(Path envFile, int lineNumber, String rawLine) {
        failAndExit(String.format(
                ".env configuration is invalid: malformed entry on line %d of '%s': \"%s\". "
                        + "Every non-comment line must be a 'KEY=VALUE' assignment. "
                        + "Fix the typo and restart.",
                lineNumber, envFile, rawLine.trim()));
    }

    private void reportUnknownKey(Path envFile, int lineNumber, String key, String suggestion) {
        failAndExit(String.format(
                ".env configuration is invalid: unknown key '%s' on line %d of '%s' — did you mean '%s'? "
                        + "As written, '%s' would be left unset and silently fall back to its default. "
                        + "Fix the typo and restart.",
                key, lineNumber, envFile, suggestion, suggestion));
    }

    private void reportUnsupportedValue(String key, String value, Set<String> allowed) {
        failAndExit(String.format(
                "Configuration is invalid: '%s' has unsupported value \"%s\". Allowed values: %s. "
                        + "Fix it (in .env or the container environment) and restart.",
                key, value, allowed));
    }

    /**
     * Reports the configuration error and terminates the JVM with a success exit code (0):
     * the system must not start with an invalid {@code .env}, but a typo is not a crash.
     * <p>
     * Writes to {@code System.err} because the logging system is not yet initialised at this
     * point in startup. Overridable so tests can assert a clean shutdown was requested without
     * killing the test JVM.
     */
    protected void failAndExit(String message) {
        System.err.println("ERROR: " + message + " The system will not start.");
        System.exit(0);
    }

    private String unquote(String value) {
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    @Override
    public int getOrder() {
        // Run before ConfigData so .env values are available for profile activation
        // and config import, but still below OS env vars in precedence.
        return ConfigDataEnvironmentPostProcessor.ORDER - 1;
    }
}
