package com.software_project_team_15b.Ticketmaster.white.Application.Initialization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.software_project_team_15b.Ticketmaster.Application.Initialization.InitialStateException;
import com.software_project_team_15b.Ticketmaster.Application.Initialization.InitialStateParser;
import com.software_project_team_15b.Ticketmaster.Application.Initialization.Statement;

class InitialStateParserWhiteTest {

    private final InitialStateParser parser = new InitialStateParser();

    @Test
    void GivenStatementWithPaddedArgs_WhenParsed_ThenOperationAndTrimmedArgsAreExtracted() {
        List<Statement> statements = parser.parse("login( alice , Secret123! );");

        assertThat(statements).hasSize(1);
        Statement s = statements.get(0);
        assertThat(s.operation()).isEqualTo("login");
        assertThat(s.args()).containsExactly("alice", "Secret123!");
        assertThat(s.argCount()).isEqualTo(2);
    }

    @Test
    void GivenMultipleStatements_WhenParsed_ThenReturnedInSourceOrder() {
        List<Statement> statements = parser.parse(
                "guest-registration(u1, p1, 1990-01-01);\nlogin(u1, p1);");

        assertThat(statements).extracting(Statement::operation)
                .containsExactly("guest-registration", "login");
    }

    @Test
    void GivenQuotedArgumentWithComma_WhenParsed_ThenCommaAndSpacesArePreserved() {
        List<Statement> statements = parser.parse(
                "open-production-company(rina, \"Rina, Inc Productions\");");

        assertThat(statements.get(0).arg(1)).isEqualTo("Rina, Inc Productions");
    }

    @Test
    void GivenQuotedArgumentWithEscapedQuote_WhenParsed_ThenQuoteIsUnescaped() {
        List<Statement> statements = parser.parse("create-event(a, \"The \\\"Band\\\"\");");

        assertThat(statements.get(0).arg(1)).isEqualTo("The \"Band\"");
    }

    @Test
    void GivenHashAndSlashComments_WhenParsed_ThenCommentsAreIgnored() {
        String program = """
                # a leading comment
                login(u1, p1);   // trailing comment
                # another comment
                logout(u1);
                """;

        assertThat(parser.parse(program)).extracting(Statement::operation)
                .containsExactly("login", "logout");
    }

    @Test
    void GivenHashInsideQuotes_WhenParsed_ThenHashIsKept() {
        List<Statement> statements = parser.parse("create-event(a, \"Show # 1\");");

        assertThat(statements.get(0).arg(1)).isEqualTo("Show # 1");
    }

    @Test
    void GivenOperationWithNoArguments_WhenParsed_ThenArgListIsEmpty() {
        List<Statement> statements = parser.parse("ping();");

        assertThat(statements.get(0).argCount()).isZero();
    }

    @Test
    void GivenStatementsOnDifferentLines_WhenParsed_ThenSourceLineIsRecorded() {
        List<Statement> statements = parser.parse("login(u1, p1);\n\nlogout(u1);");

        assertThat(statements.get(0).sourceLine()).isEqualTo(1);
        assertThat(statements.get(1).sourceLine()).isEqualTo(3);
    }

    @Test
    void GivenNullContent_WhenParsed_ThenInitialStateExceptionIsThrown() {
        assertThatThrownBy(() -> parser.parse(null))
                .isInstanceOf(InitialStateException.class)
                .hasMessageContaining("null");
    }

    @Test
    void GivenStatementMissingSemicolon_WhenParsed_ThenInitialStateExceptionIsThrown() {
        assertThatThrownBy(() -> parser.parse("login(u1, p1)"))
                .isInstanceOf(InitialStateException.class)
                .hasMessageContaining("';'");
    }

    @Test
    void GivenUnterminatedQuote_WhenParsed_ThenInitialStateExceptionIsThrown() {
        assertThatThrownBy(() -> parser.parse("login(\"u1, p1);"))
                .isInstanceOf(InitialStateException.class)
                .hasMessageContaining("Unterminated");
    }

    @Test
    void GivenStatementMissingOpeningParen_WhenParsed_ThenInitialStateExceptionIsThrown() {
        assertThatThrownBy(() -> parser.parse("login;"))
                .isInstanceOf(InitialStateException.class)
                .hasMessageContaining("'('");
    }

    @Test
    void GivenStatementMissingOperationName_WhenParsed_ThenInitialStateExceptionIsThrown() {
        assertThatThrownBy(() -> parser.parse("(u1, p1);"))
                .isInstanceOf(InitialStateException.class)
                .hasMessageContaining("operation name");
    }
}
