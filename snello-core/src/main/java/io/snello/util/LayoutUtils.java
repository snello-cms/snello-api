package io.snello.util;

import io.snello.model.enums.LayoutKind;
import io.snello.model.enums.LayoutPageType;

/**
 * Layout helpers shared by write validation and config import.
 */
public final class LayoutUtils {

    private LayoutUtils() {
    }

    /** Expected {@code json_data.kind} for a given {@code page_type}. */
    public static LayoutKind expectedKind(LayoutPageType pageType) {
        if (pageType == null) {
            return null;
        }
        return switch (pageType) {
            case LIST -> LayoutKind.LIST;
            case EDIT, VIEW -> LayoutKind.WIZARD;
        };
    }

    /** Convenience overload when {@code page_type} is still a raw DB/API string. */
    public static LayoutKind expectedKind(String pageType) {
        return expectedKind(LayoutPageType.from(pageType));
    }
}
