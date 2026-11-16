package io.snello.util;

import io.snello.model.enums.PrintExportType;

/**
 * Print-template helpers shared by write validation and render.
 */
public final class PrintTemplateUtils {

    private PrintTemplateUtils() {
    }

    /** {@code null}/blank {@code export_type} defaults to PDF at print time. */
    public static PrintExportType resolve(String exportType) {
        PrintExportType type = PrintExportType.from(exportType);
        return type == null && (exportType == null || exportType.isBlank()) ? PrintExportType.PDF : type;
    }

    public static boolean requiresHtmlBody(PrintExportType type) {
        return type == PrintExportType.PDF || type == PrintExportType.HTML;
    }

    public static boolean requiresTemplateFile(PrintExportType type) {
        return type == PrintExportType.XLSX;
    }
}
