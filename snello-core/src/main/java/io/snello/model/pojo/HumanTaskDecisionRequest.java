package io.snello.model.pojo;

/** Body of POST /api/humantasks/{uuid}/decide: one of the task's decisions, optional comment. */
public record HumanTaskDecisionRequest(String decision, String comment) {
}
