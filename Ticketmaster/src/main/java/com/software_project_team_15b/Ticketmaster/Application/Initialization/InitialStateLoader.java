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
                    "Initial-state seeding is enabled (app.init.state.enabled=true / INIT_RUN=true) "
                            + "but no file is configured. Set app.init.state.file (env INIT_STATE_FILE), "
                            + "e.g. 'classpath:initial-state.txt' or 'file:/path/to/seed.txt'.");
        }

        String location = filePath.trim();
        LOG.info("Initial-state seeding enabled; loading file from '{}'", location);

        String content = readFile(location);
        LOG.debug("Read {} character(s) from '{}'", content.length(), location);

        List<Statement> statements = parsePhase(location, content);
        LOG.info("Parsed {} statement(s) from '{}'; executing in order…", statements.size(), location);

        executePhase(location, statements);
        LOG.info("Initial-state file '{}' applied successfully: {} operation(s) executed",
                location, statements.size());
    }

    /** Parses the file, logging an actionable error (and aborting startup) on any syntax problem. */
    private List<Statement> parsePhase(String location, String content) {
        try {
            return parser.parse(content);
        } catch (InitialStateException e) {
            LOG.error("Initial-state file '{}' was rejected during parsing — fix the syntax problem "
                    + "below and restart. Startup is aborted.\n  -> {}", location, e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            LOG.error("Unexpected error while parsing initial-state file '{}'. Startup is aborted.",
                    location, e);
            throw new InitialStateException(
                    "Unexpected error while parsing initial-state file '" + location + "': "
                            + e.getMessage(), e);
        }
    }

    /** Executes the statements in order, logging the failing step (and aborting startup) on any error. */
    private void executePhase(String location, List<Statement> statements) {
        try {
            executor.execute(statements);
        } catch (InitialStateException e) {
            LOG.error("Initial-state file '{}' failed during execution — the database may be partially "
                    + "seeded; reset it (e.g. 'docker compose down -v') before retrying. Startup is "
                    + "aborted.\n  -> {}", location, e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            LOG.error("Unexpected error while executing initial-state file '{}'. Startup is aborted.",
                    location, e);
            throw new InitialStateException(
                    "Unexpected error while executing initial-state file '" + location + "': "
                            + e.getMessage(), e);
        }
    }

    private String readFile(String location) {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new InitialStateException(
                    "Initial-state file not found: '" + location + "'. Check app.init.state.file "
                            + "(env INIT_STATE_FILE); paths may use a 'classpath:' or 'file:' prefix "
                            + "(a bare path is treated as a filesystem path).");
        }
        try (InputStream in = resource.getInputStream()) {
            String content = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
            if (content.isBlank()) {
                LOG.warn("Initial-state file '{}' is empty; no operations will be executed", location);
            }
            return content;
        } catch (IOException e) {
            throw new InitialStateException(
                    "Failed to read initial-state file '" + location + "': " + e.getMessage(), e);
        }
    }
}
