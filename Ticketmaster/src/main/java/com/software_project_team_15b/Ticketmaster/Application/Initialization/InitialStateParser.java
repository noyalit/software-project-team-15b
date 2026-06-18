package com.software_project_team_15b.Ticketmaster.Application.Initialization;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Parses the plaintext initial-state DSL into {@link Statement}s.
 * <p>
 * Grammar (informal):
 * <ul>
 *     <li>A program is a sequence of statements, each terminated by {@code ;}.</li>
 *     <li>A statement is {@code operation-name(arg1, arg2, ...)}.</li>
 *     <li>Arguments are comma-separated; surrounding whitespace is trimmed.</li>
 *     <li>An argument may be wrapped in double quotes to preserve commas/spaces;
 *         {@code \"} is an escaped quote inside a quoted value.</li>
 *     <li>Comments run from an unquoted {@code #} or {@code //} to end of line.</li>
 * </ul>
 * Pure and side-effect free, so it is unit-testable without a Spring context.
 */
@Component
public class InitialStateParser {

    private static final Logger LOG = LoggerFactory.getLogger(InitialStateParser.class);

    /** Longest statement snippet echoed back in an error message before truncation. */
    private static final int MAX_SNIPPET = 80;

    public List<Statement> parse(String text) {
        if (text == null) {
            throw new InitialStateException(
                    "Initial-state content is null (the file could not be read or was empty)");
        }

        String stripped = stripComments(text);

        List<Statement> statements = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        boolean inQuotes = false;
        int line = 1;
        int statementStartLine = 0;

        for (int i = 0; i < stripped.length(); i++) {
            char c = stripped.charAt(i);

            if (c == '\n') {
                line++;
                buffer.append(c);
                continue;
            }

            if (c == '"') {
                inQuotes = !inQuotes;
                if (statementStartLine == 0) {
                    statementStartLine = line;
                }
                buffer.append(c);
                continue;
            }

            if (c == ';' && !inQuotes) {
                if (!buffer.toString().isBlank()) {
                    statements.add(parseStatement(buffer.toString(), statementStartLine));
                }
                buffer.setLength(0);
                statementStartLine = 0;
                continue;
            }

            if (statementStartLine == 0 && !Character.isWhitespace(c)) {
                statementStartLine = line;
            }
            buffer.append(c);
        }

        if (inQuotes) {
            throw new InitialStateException(
                    "Unterminated quoted argument starting near line " + statementStartLine
                            + ": a '\"' was opened but never closed before end of file"
                            + " (offending text: " + snippet(buffer.toString()) + ")");
        }
        if (!buffer.toString().isBlank()) {
            int where = statementStartLine == 0 ? line : statementStartLine;
            throw new InitialStateException(
                    "Missing ';' terminator for the statement starting on line " + where
                            + "; every statement must end with ';'"
                            + " (offending text: " + snippet(buffer.toString()) + ")");
        }

        LOG.debug("Parsed {} statement(s) from {} character(s) of initial-state content",
                statements.size(), stripped.length());
        return statements;
    }

    /** Removes {@code #} and {@code //} comments, respecting double-quoted text within a line. */
    private String stripComments(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (String rawLine : text.split("\n", -1)) {
            boolean inQuotes = false;
            int cut = rawLine.length();
            for (int i = 0; i < rawLine.length(); i++) {
                char c = rawLine.charAt(i);
                if (c == '"') {
                    inQuotes = !inQuotes;
                } else if (!inQuotes && c == '#') {
                    cut = i;
                    break;
                } else if (!inQuotes && c == '/' && i + 1 < rawLine.length() && rawLine.charAt(i + 1) == '/') {
                    cut = i;
                    break;
                }
            }
            out.append(rawLine, 0, cut).append('\n');
        }
        return out.toString();
    }

    private Statement parseStatement(String raw, int line) {
        String trimmed = raw.trim();
        int open = trimmed.indexOf('(');
        if (open < 0) {
            throw new InitialStateException(
                    "Expected '(' after the operation name on line " + line
                            + "; a statement looks like 'operation(arg1, arg2, ...)'"
                            + " (offending text: " + snippet(trimmed) + ")");
        }
        if (!trimmed.endsWith(")")) {
            throw new InitialStateException(
                    "Expected closing ')' to end the statement on line " + line
                            + " (offending text: " + snippet(trimmed) + ")");
        }
        String name = trimmed.substring(0, open).trim();
        if (name.isEmpty()) {
            throw new InitialStateException(
                    "Missing operation name before '(' on line " + line
                            + " (offending text: " + snippet(trimmed) + ")");
        }

        String inner = trimmed.substring(open + 1, trimmed.length() - 1);
        Statement statement = new Statement(name, splitArgs(inner), line);
        LOG.trace("Parsed statement on line {}: operation={} argCount={}",
                line, name, statement.argCount());
        return statement;
    }

    /** Returns a single-line, length-bounded, quoted view of {@code text} for error messages. */
    private String snippet(String text) {
        String oneLine = text.trim().replaceAll("\\s+", " ");
        if (oneLine.length() > MAX_SNIPPET) {
            oneLine = oneLine.substring(0, MAX_SNIPPET) + "…";
        }
        return "'" + oneLine + "'";
    }

    private List<String> splitArgs(String inner) {
        List<String> args = new ArrayList<>();
        if (inner.isBlank()) {
            return args;
        }

        StringBuilder buffer = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                buffer.append(c);
            } else if (c == ',' && !inQuotes) {
                args.add(finalizeArg(buffer.toString()));
                buffer.setLength(0);
            } else {
                buffer.append(c);
            }
        }
        args.add(finalizeArg(buffer.toString()));
        return args;
    }

    private String finalizeArg(String raw) {
        String s = raw.trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1).replace("\\\"", "\"");
        }
        return s;
    }
}
