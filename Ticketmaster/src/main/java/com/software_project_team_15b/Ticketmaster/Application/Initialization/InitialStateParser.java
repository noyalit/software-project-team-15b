package com.software_project_team_15b.Ticketmaster.Application.Initialization;

import java.util.ArrayList;
import java.util.List;

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

    public List<Statement> parse(String text) {
        if (text == null) {
            throw new InitialStateException("Initial-state content is null");
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
            throw new InitialStateException("Unterminated quoted argument near line " + statementStartLine);
        }
        if (!buffer.toString().isBlank()) {
            throw new InitialStateException(
                    "Missing ';' terminator for statement near line "
                            + (statementStartLine == 0 ? line : statementStartLine));
        }

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
                    "Expected '(' in statement near line " + line + ": " + trimmed);
        }
        if (!trimmed.endsWith(")")) {
            throw new InitialStateException(
                    "Expected ')' to close statement near line " + line + ": " + trimmed);
        }
        String name = trimmed.substring(0, open).trim();
        if (name.isEmpty()) {
            throw new InitialStateException("Missing operation name near line " + line + ": " + trimmed);
        }

        String inner = trimmed.substring(open + 1, trimmed.length() - 1);
        return new Statement(name, splitArgs(inner), line);
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
