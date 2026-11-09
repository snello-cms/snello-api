package io.snello.filter;

import io.quarkus.logging.Log;
import io.snello.model.pojo.FailMsg;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.sql.SQLException;

@Provider
public class RsExceptionMapper implements ExceptionMapper<Exception> {

    @Override
    public Response toResponse(Exception exception) {
        int statusCode = getStatusCode(exception);
        String message = safeMessage(exception.getMessage(), "Unexpected server error");
        String detail = buildDetail(exception);
        if (statusCode >= 500) {
            Log.error("REST error status=" + statusCode + " message=" + message, exception);
        } else {
            // client errors (404/405/400/...) are expected traffic: one line, no stack trace
            Log.warnf("REST client error status=%d message=%s %s", statusCode, message, detail);
        }
        return Response.status(statusCode)
                .type(MediaType.APPLICATION_JSON)
                .entity(new FailMsg(message, detail))
                .build();
    }

    private int getStatusCode(Exception exception) {
        if (exception instanceof WebApplicationException webApplicationException) {
            return webApplicationException.getResponse().getStatus();
        }
        Throwable root = getRootCause(exception);
        // Bad client input surfacing from the database or from a PRE action was answered 500 with raw SQL.
        if (root instanceof SQLException sql && sql.getSQLState() != null) {
            String state = sql.getSQLState();
            if ("23505".equals(state)) {
                return Response.Status.CONFLICT.getStatusCode();              // unique violation
            }
            // 22xxx data exception, 23xxx constraint, 42703 unknown column (filter), 42804 value of the wrong type
            // (e.g. "not-a-date" for a date column: values are bound as strings)
            if (state.startsWith("22") || state.startsWith("23") || "42703".equals(state) || "42804".equals(state)) {
                return Response.Status.BAD_REQUEST.getStatusCode();
            }
        }
        if (root instanceof javax.script.ScriptException || exception instanceof javax.script.ScriptException) {
            return Response.Status.BAD_REQUEST.getStatusCode();               // a PRE action rejected the write
        }
        return Response.Status.INTERNAL_SERVER_ERROR.getStatusCode();
    }

    private String buildDetail(Exception exception) {
        Throwable rootCause = getRootCause(exception);
        String rootMessage = safeMessage(rootCause.getMessage(), rootCause.getClass().getSimpleName());
        if (rootCause instanceof SQLException sqlException && sqlException.getSQLState() != null) {
            return "sqlState=" + sqlException.getSQLState() + ", rootCause=" + rootMessage;
        }
        return "rootCause=" + rootMessage;
    }

    private Throwable getRootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private String safeMessage(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}


