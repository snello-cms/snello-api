package io.snello.model.pojo;

import java.util.List;
import java.util.Map;

/** Rows of one table read in pages for a data export; {@code skipped} when the table could not be read. */
public record TableExport(List<List<Map<String, Object>>> pages, int totalRows, boolean skipped, String skipReason) {
}
