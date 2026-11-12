package io.snello.test.actions;

import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.snello.api.service.JdbcRepository;
import io.snello.model.ScheduledAction;
import io.snello.service.ScheduledActionService;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestSecurity(user = "sched-admin", roles = {"Admin"})
class ScheduledActionsTest {

    private static final String MAIL = "scheduled@rent.it";
    private static final String BODY = "mail.send('job ' + action.name, '<p>run</p>', java.util.List.of('" + MAIL + "'), null, null, null);";

    @Inject
    JdbcRepository jdbc;
    @Inject
    ScheduledActionService service;
    @Inject
    MockMailbox mailbox;

    @BeforeEach
    @AfterEach
    void clean() throws Exception {
        jdbc.executeQuery("DELETE FROM scheduledactions WHERE name LIKE 'test-sched-%'");
        jdbc.executeQuery("DELETE FROM printtemplates WHERE name LIKE 'test-sched-%'");
        mailbox.clear();
    }

    private String create(String name, String frequency, boolean enabled, String body) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("frequency", frequency);
        m.put("enabled", enabled);
        m.put("body", body);
        m.put("last_status", "FORGED");
        return given().contentType(ContentType.JSON).body(m)
                .post("/api/scheduledactions")
                .then().statusCode(200).extract().path("uuid");
    }

    private void forceDue(String uuid) throws Exception {
        String past = OffsetDateTime.now().minusMinutes(10).toString();
        jdbc.executeQuery("UPDATE scheduledactions SET next_run = '" + past + "' WHERE uuid = '" + uuid + "'");
    }

    @Test
    void saveComputesNextRunAndIgnoresSchedulerFields() throws Exception {
        String uuid = create("test-sched-daily", "0 17 * * *", true, BODY);
        ScheduledAction job = service.find(uuid);
        assertNotNull(job.next_run);
        OffsetDateTime next = OffsetDateTime.parse(job.next_run);
        assertTrue(next.isAfter(OffsetDateTime.now()));
        assertEquals(17, next.atZoneSameInstant(service.zone()).getHour());
        assertNull(job.last_status, "last_status is owned by the scheduler");
    }

    @Test
    void quartzCronIsAcceptedAndBadCronIsRejected() {
        create("test-sched-quartz", "0 0 17 * * ?", true, BODY);
        Map<String, Object> bad = Map.of("name", "test-sched-bad", "frequency", "every day", "body", BODY);
        given().contentType(ContentType.JSON).body(bad).post("/api/scheduledactions").then().statusCode(400);
    }

    @Test
    void dueJobRunsOnceThenWaitsForItsNextTime() throws Exception {
        String uuid = create("test-sched-due", "0 3 * * *", true, BODY);
        forceDue(uuid);

        assertEquals(1, service.runDue());
        assertEquals(1, mailbox.getMailsSentTo(MAIL).size());
        ScheduledAction job = service.find(uuid);
        assertEquals("OK", job.last_status);
        assertNotNull(job.last_run);
        assertTrue(OffsetDateTime.parse(job.next_run).isAfter(OffsetDateTime.now()));

        assertEquals(0, service.runDue(), "next_run moved forward: no second run");
        assertEquals(1, mailbox.getMailsSentTo(MAIL).size());
    }

    @Test
    void disabledJobIsSkipped() throws Exception {
        String uuid = create("test-sched-off", "0 3 * * *", false, BODY);
        assertNull(service.find(uuid).next_run);
        forceDue(uuid);
        assertEquals(0, service.runDue());
        assertEquals(0, mailbox.getMailsSentTo(MAIL).size());
    }

    @Test
    void failingScriptIsRecordedAsError() throws Exception {
        String uuid = create("test-sched-fail", "0 3 * * *", true, "throw new Error('boom');");
        forceDue(uuid);
        assertEquals(1, service.runDue());
        ScheduledAction job = service.find(uuid);
        assertEquals("ERROR", job.last_status);
        assertTrue(job.last_message.contains("boom"), job.last_message);
    }

    @Test
    void runNowAndPreview() {
        String uuid = create("test-sched-now", "0 3 * * *", true, BODY);
        given().post("/api/scheduledactions/" + uuid + "/run")
                .then().statusCode(200).body("last_status", org.hamcrest.Matchers.equalTo("OK"));
        assertEquals(1, mailbox.getMailsSentTo(MAIL).size());

        given().queryParam("frequency", "0 17 * * *").queryParam("count", 3)
                .get("/api/scheduledactions/preview")
                .then().statusCode(200).body("next_runs", hasSize(3)).body("zone", notNullValue());
        given().queryParam("frequency", "nope").get("/api/scheduledactions/preview").then().statusCode(400);
    }

    @Test
    void importedRowWithoutNextRunIsInitialisedNotFired() throws Exception {
        String uuid = create("test-sched-import", "0 3 * * *", true, BODY);
        jdbc.executeQuery("UPDATE scheduledactions SET next_run = NULL WHERE uuid = '" + uuid + "'");
        assertEquals(0, service.runDue());
        assertNotNull(service.find(uuid).next_run);
        assertEquals(0, mailbox.getMailsSentTo(MAIL).size());
    }

    /** The {@code prints} binding renders a Print Template from a script, with filters and sort. */
    @Test
    void scriptRendersPrintTemplate() throws Exception {
        Map<String, Object> template = new HashMap<>();
        template.put("uuid", java.util.UUID.randomUUID().toString());
        template.put("name", "test-sched-print");
        template.put("export_type", "csv");
        template.put("sql_query", "SELECT 'a' AS x UNION ALL SELECT 'b' AS x");
        template.put("api_protected", false);
        jdbc.create("printtemplates", "uuid", template);

        String body = "var out = prints.print('test-sched-print', {x: 'b'}, null);"
                + "mail.send('rows ' + out.rows() + ' ' + out.fileName(), new java.lang.String(out.bytes(), 'UTF-8'),"
                + " java.util.List.of('" + MAIL + "'), null, null, null);";
        String uuid = create("test-sched-print-job", "0 3 * * *", true, body);
        given().post("/api/scheduledactions/" + uuid + "/run")
                .then().statusCode(200).body("last_status", org.hamcrest.Matchers.equalTo("OK"));

        var mails = mailbox.getMailsSentTo(MAIL);
        assertEquals(1, mails.size());
        assertEquals("rows 1 test-sched-print.csv", mails.getFirst().getSubject());
        assertTrue(mails.getFirst().getHtml().contains("b"), mails.getFirst().getHtml());
        assertFalse(mails.getFirst().getHtml().contains("a\r\n"), "filter x=b must drop row a");
    }
}
