package com.software_project_team_15b.Ticketmaster.Application.Initialization;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

/**
 * At startup, after the configuration file (i) has been applied and the default
 * system admin seeded, reads an optional external "initial-state" file and
 * executes the use cases it lists so the system reaches the defined state.
 * <p>
 * The file path comes from {@code app.init.state.file} (env {@code INIT_STATE_FILE});
 * when blank the feature is off. Paths may use Spring resource prefixes such as
 * {@code classpath:} or {@code file:} (a bare path is treated as a filesystem path).
 * <p>
 * Runs last among {@link ApplicationRunner}s so {@code admin-login} can succeed,
 * and any failure propagates out of {@link #run} to abort context startup, as the
 * specification requires.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class InitialStateLoader implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(InitialStateLoader.class);

    private final ResourceLoader resourceLoader;
    private final InitialStateParser parser;
    private final InitialStateExecutor executor;
    private final boolean enabled;
    private final String filePath;

    public InitialStateLoader(
            ResourceLoader resourceLoader,
            InitialStateParser parser,
            InitialStateExecutor executor,
            @Value("${app.init.state.enabled:false}") boolean enabled,
            @Value("${app.init.state.file:}") String filePath) {
        this.resourceLoader = resourceLoader;
        this.parser = parser;
        this.executor = executor;
        this.enabled = enabled;
        this.filePath = filePath;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            LOG.debug("Initial-state seeding skipped (app.init.state.enabled=false)");
            return;
        }
        if (filePath == null || filePath.isBlank()) {
            throw new InitialStateException(
                    "initial-state is enabled (app.init.state.enabled=true) but no file is configured");
        }

        LOG.info("Loading initial-state file from '{}'", filePath);
        String content = readFile(filePath.trim());

        List<Statement> statements = parser.parse(content);
        executor.execute(statements);

        LOG.info("Initial-state file applied successfully: {} operation(s) executed", statements.size());
    }

    private String readFile(String location) {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new InitialStateException("Initial-state file not found: " + location);
        }
        try (InputStream in = resource.getInputStream()) {
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new InitialStateException("Failed to read initial-state file: " + location, e);
        }
    }
}
