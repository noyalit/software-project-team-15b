package com.software_project_team_15b.Ticketmaster.Infrastructure.Config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
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
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /** Property source name; also used to make the post-processor idempotent. */
    static final String PROPERTY_SOURCE_NAME = "dotenv";

    private static final String FILE_NAME = ".env";
    private static final int MAX_PARENT_LEVELS = 6;

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            return;
        }
        Path envFile = locateEnvFile();
        if (envFile == null) {
            return;
        }
        Map<String, Object> values = parse(envFile);
        if (!values.isEmpty()) {
            environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, values));
        }
    }

    private Path locateEnvFile() {
        Path dir = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath();
        for (int level = 0; level <= MAX_PARENT_LEVELS && dir != null; level++) {
            Path candidate = dir.resolve(FILE_NAME);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        return null;
    }

    private Map<String, Object> parse(Path envFile) {
        Map<String, Object> values = new LinkedHashMap<>();
        List<String> lines;
        try {
            lines = Files.readAllLines(envFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // Unreadable .env should never break startup; treat as absent.
            return values;
        }
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("export ")) {
                line = line.substring("export ".length()).trim();
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            if (key.isEmpty()) {
                continue;
            }
            values.put(key, unquote(line.substring(eq + 1).trim()));
        }
        return values;
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
