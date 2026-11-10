package io.snello.test.actions;

import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.snello.api.service.JdbcRepository;
import io.snello.api.service.MailService;
import io.snello.service.MetadataService;
import io.snello.service.ScheduledScriptJob;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Renter flow: after a registration is saved, a POST action mails the renter; a cron job does the same on a schedule.
 * Mailer runs in mock mode, so no real SMTP is needed (provider settings are env-driven, see application.properties).
 */
@QuarkusTest
@TestSecurity(user = "mail-admin", roles = {"Admin"})
class MailAndSchedulerTest {

    private static final String TABLE = "mail_probe";
    private static final String META = "mail-probe-meta";

    @Inject
    JdbcRepository jdbc;
    @Inject
    MetadataService metadataService;
    @Inject
    MockMailbox mailbox;
    @Inject
    MailService mailService;
    @Inject
    ScheduledScriptJob job;

    @BeforeEach
    void seed() throws Exception {
        cleanup();
        mailbox.clear();
        jdbc.executeQuery("CREATE TABLE " + TABLE + " (uuid varchar(50) PRIMARY KEY, name varchar(50), email varchar(100))");
        Map<String, Object> m = new HashMap<>();
        m.put("uuid", META);
        m.put("table_name", TABLE);
        m.put("table_key", "uuid");
        m.put("table_key_type", "uuid");
        m.put("created", true);
        jdbc.create("metadatas", "uuid", m);
        metadataService.invalidateMetadataCaches();
    }

    @AfterEach
    void cleanup() throws Exception {
        jdbc.executeQuery("DROP TABLE IF EXISTS " + TABLE);
        jdbc.executeQuery("DELETE FROM metadatas WHERE uuid = '" + META + "'");
        jdbc.executeQuery("DELETE FROM actions WHERE metadata_name = '" + TABLE + "'");
        metadataService.invalidateMetadataCaches();
        metadataService.invalidateSecondaryCaches();
    }

    @Test
    void smtpServiceKeepsTheFirstRecipient() {
        mailService.send("hello", "<b>hi</b>", List.of("a@x.it", "b@x.it"), null, null, null);
        // MockMailbox records one message per recipient
        assertEquals(1, mailbox.getMailsSentTo("a@x.it").size());
        assertEquals(1, mailbox.getMailsSentTo("b@x.it").size());
        assertEquals(List.of("a@x.it", "b@x.it"), mailbox.getMailsSentTo("a@x.it").get(0).getTo());
    }

    @Test
    void postActionEmailsTheRenter() throws Exception {
        Map<String, Object> a = new HashMap<>();
        a.put("uuid", java.util.UUID.randomUUID().toString());
        a.put("name", "welcome-mail");
        a.put("description", "test");
        a.put("metadata_name", TABLE);
        a.put("condition", "PERSIST");
        a.put("phase", "POST");
        a.put("body", "mail.send('Welcome ' + map.get('name'), '<p>registered</p>', "
                + "java.util.List.of(map.get('email')), null, null, null);");
        jdbc.create("actions", "uuid", a);
        metadataService.invalidateSecondaryCaches();

        given().contentType(ContentType.JSON).body(Map.of("name", "Mario", "email", "mario@rent.it"))
                .when().post("/api/" + TABLE).then().statusCode(200);

        for (int i = 0; i < 50 && mailbox.getTotalMessagesSent() == 0; i++) {
            Thread.sleep(100); // POST actions run asynchronously
        }
        assertEquals(1, mailbox.getMailsSentTo("mario@rent.it").size());
        assertEquals("Welcome Mario", mailbox.getMailsSentTo("mario@rent.it").get(0).getSubject());
    }

    @Test
    void schedulerRunsConfiguredScript() {
        // script is set in src/test/resources/application.properties (snello.scheduler.script)
        assertTrue(job.runOnce());
        assertEquals(1, mailbox.getMailsSentTo("sched@rent.it").size());
    }
}
