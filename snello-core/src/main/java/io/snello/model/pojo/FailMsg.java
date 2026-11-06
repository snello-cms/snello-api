package io.snello.model.pojo;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * JSON error body produced by {@code RsExceptionMapper}: {@code {"msg": "...", "detail": "rootCause=..."}}.
 */
@RegisterForReflection
public class FailMsg {
    public String msg;
    public String detail;

    public FailMsg(String msg, String detail) {
        this.msg = msg;
        this.detail = detail;
    }
}
