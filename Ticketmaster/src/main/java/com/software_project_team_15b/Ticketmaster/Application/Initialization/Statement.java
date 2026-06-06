package com.software_project_team_15b.Ticketmaster.Application.Initialization;

import java.util.List;

/**
 * A single parsed instruction from an initial-state file, e.g.
 * {@code open-production-company(rina, Acme)}.
 *
 * @param operation  the use-case name (the text before the parentheses)
 * @param args       the comma-separated arguments, already trimmed and unquoted
 * @param sourceLine the 1-based line number where the statement ended, used for
 *                   error messages
 */
public record Statement(String operation, List<String> args, int sourceLine) {

    public Statement {
        args = List.copyOf(args);
    }

    public String arg(int index) {
        return args.get(index);
    }

    public int argCount() {
        return args.size();
    }
}
