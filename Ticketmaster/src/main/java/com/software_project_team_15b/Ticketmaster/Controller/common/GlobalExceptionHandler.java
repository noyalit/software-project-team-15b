package com.software_project_team_15b.Ticketmaster.Controller.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({CannotCreateTransactionException.class, DataAccessException.class})
    public ResponseEntity<ApiResponse<Void>> handleDatabaseUnavailable(Exception ex) {
        LOG.warn("database unavailable: {}", ex == null ? null : ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiResponse<>(null, "The site is currently unavailable. Please try again later."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        if (isDatabaseConnectivityFailure(ex)) {
            LOG.warn("database connectivity failure: {}", ex == null ? null : ex.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ApiResponse<>(null, "The site is currently unavailable. Please try again later."));
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(null, ex == null ? "The request failed due to a server error. Please try again later." : ex.getMessage()));
    }

    private boolean isDatabaseConnectivityFailure(Throwable ex) {
        Throwable cur = ex;
        while (cur != null) {
            String name = cur.getClass().getName();
            if (name.contains("JDBCConnectionException")
                    || name.contains("CommunicationsException")
                    || name.contains("SQLNonTransientConnectionException")
                    || name.contains("CannotCreateTransactionException")
                    || name.contains("DataAccess")) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }
}
