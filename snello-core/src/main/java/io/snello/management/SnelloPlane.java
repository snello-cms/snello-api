package io.snello.management;

/**
 * Build-time switch between the control plane and the query plane.
 * <p>
 * Each app module sets {@value #CONTROL_PLANE_PROPERTY} in its {@code application.properties}:
 * <ul>
 *     <li>{@code snello-control-app}: {@code true} — metadata CRUD, DDL, admin RS, data writes, AI write tools</li>
 *     <li>{@code snello-query-app}: {@code false} — only read endpoints on {@code /api/{table}}</li>
 * </ul>
 * Control-only beans and JAX-RS resources are annotated with
 * {@code @IfBuildProperty(name = CONTROL_PLANE_PROPERTY, stringValue = "true", enableIfMissing = true)},
 * so they are not even registered in the query image (no route, no bean). The flag is evaluated at
 * (re-)augmentation time, so it cannot be flipped with an env var on a running container.
 */
public final class SnelloPlane {

    public static final String CONTROL_PLANE_PROPERTY = "snello.control-plane.enabled";

    /** Interval of the metadata snapshot checksum poll; {@code off} disables it (control relies on CDI events). */
    public static final String METADATA_REFRESH_INTERVAL_PROPERTY = "snello.metadata.refresh-interval";

    private SnelloPlane() {
    }
}
