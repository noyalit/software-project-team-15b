package com.software_project_team_15b.Ticketmaster.Infrastructure.Config;

/**
 * Test-only variant of {@link DotenvEnvironmentPostProcessor} that loads
 * {@code .env.test} (at the module root) instead of {@code .env}. Registered via
 * {@code src/test/resources/META-INF/spring.factories} so it runs for every Spring
 * context started during {@code mvn test}.
 * <p>
 * It is registered through {@code spring.factories} rather than a second
 * {@code META-INF/spring/...EnvironmentPostProcessor.imports} file because the main
 * processor already owns that {@code .imports} resource path — a duplicate at the
 * same classpath location is not merged, so this processor would never run.
 * <p>
 * It uses a distinct property-source name so it can coexist with a developer's
 * {@code .env} (loaded by the parent) without either clobbering the other. As with
 * the parent, the source is added at the lowest precedence — real OS environment
 * variables and {@code application.properties} still win.
 */
public class TestDotenvEnvironmentPostProcessor extends DotenvEnvironmentPostProcessor {

    @Override
    protected String filename() {
        return ".env.test";
    }

    @Override
    protected String propertySourceName() {
        return "dotenv.test";
    }
}
