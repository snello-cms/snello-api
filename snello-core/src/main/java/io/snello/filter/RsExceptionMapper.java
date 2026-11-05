package io.snello.filter;

import io.quarkus.logging.Log;
import io.quarkus.runtime.annotations.RegisterForReflection;

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
        Log.error("REST error status=" + statusCode + " message=" + message, exception);
        return Response.status(statusCode)
                .type(MediaType.APPLICATION_JSON)
                .entity(new FailMsg(message, detail))
                .build();
    }

    private int getStatusCode(Exception exception) {
        if (exception instanceof WebApplicationException webApplicationException) {
            return webApplicationException.getResponse().getStatus();
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

    @RegisterForReflection
    public static class FailMsg {
        public String msg;
        public String detail;

        public FailMsg(String msg, String detail) {
            this.msg = msg;
            this.detail = detail;
        }
    }
}


