package io.snello.model.pojo;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * A rendered print template, ready to stream.
 *
 * @param bytes       file content
 * @param contentType media type (application/pdf, text/csv, ...)
 * @param fileName    download name, e.g. {@code invoices.pdf}
 * @param rows        rows the query returned (logged, and sent as X-Snello-Rows)
 */
@RegisterForReflection
public record PrintOutput(byte[] bytes, String contentType, String fileName, int rows) {
}
