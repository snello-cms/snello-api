package io.snello.model.pojo;

/** Body of POST /api/humantasks/{uuid}/close (the workflow, after applying the decision): optional final note. */
public record HumanTaskCloseRequest(String note) {
}
