package io.snello.test.print;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.snello.api.service.JdbcRepository;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import jakarta.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Print templates: pdf / html (Qute) / csv (automatic or Qute) / xlsx (JXLS), filters by column alias, HTML
 * escaping, validation and the same row-level security as select queries.
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PrintTemplatesTest {

    private static final String TABLE = "print_probe";
    private static final String ROWS_HTML = "<html><body><h1>{template}</h1><ul>{#for row in rows}<li>{row.name}-{row.city}</li>{/for}</ul><p>{count}</p></body></html>";

    @Inject
    JdbcRepository jdbc;

    @BeforeAll
    void seed() throws Exception {
        cleanup();
        jdbc.executeQuery("CREATE TABLE " + TABLE + " (uuid varchar(50) PRIMARY KEY, name varchar(50), city varchar(50), owner varchar(50))");
        jdbc.executeQuery("INSERT INTO " + TABLE + " VALUES ('1','Anna','Rome','alice'),('2','Bruno','Milan','bob'),('3','<b>Carla</b>','Rome','bob')");
        template("pt-test-html", "html", ROWS_HTML, null, false, null);
        template("pt-test-pdf", "pdf", ROWS_HTML, null, false, null);
        template("pt-test-csv", "csv", null, null, false, null);
        template("pt-test-csvq", "csv", "{#for row in rows}{row.name};{/for}", null, false, null);
        template("pt-test-xlsx", "xlsx", null, Base64.getEncoder().encodeToString(jxlsTemplate()), false, null);
        template("pt-test-owned", "csv", null, null, true, "owner");
    }

    @AfterAll
    void cleanup() throws Exception {
        jdbc.executeQuery("DROP TABLE IF EXISTS " + TABLE);
        jdbc.executeQuery("DELETE FROM printtemplates WHERE name LIKE 'pt-test-%'");
    }

    private void template(String name, String type, String body, String file, boolean prot, String owner) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("uuid", name);
        m.put("name", name);
        m.put("export_type", type);
        m.put("sql_query", "SELECT name, city, owner FROM " + TABLE);
        m.put("html_body", body);
        m.put("template_file", file);
        m.put("template_file_name", file == null ? null : "t.xlsx");
        m.put("api_protected", prot);
        m.put("username_field", owner);
        jdbc.create("printtemplates", "uuid", m);
    }

    /** One header row and one data row; JXLS commands live in the A1 comment. */
    private static byte[] jxlsTemplate() throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("rows");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Name");
            header.createCell(1).setCellValue("City");
            Row data = sheet.createRow(1);
            data.createCell(0).setCellValue("${row.name}");
            data.createCell(1).setCellValue("${row.city}");
            Drawing<?> drawing = sheet.createDrawingPatriarch();
            ClientAnchor anchor = wb.getCreationHelper().createClientAnchor();
            anchor.setRow1(0);
            anchor.setCol1(0);
            Comment area = drawing.createCellComment(anchor);
            area.setString(wb.getCreationHelper().createRichTextString("jx:area(lastCell=\"B2\")"));
            header.getCell(0).setCellComment(area);
            ClientAnchor anchor2 = wb.getCreationHelper().createClientAnchor();
            anchor2.setRow1(1);
            anchor2.setCol1(0);
            anchor2.setRow2(3);
            anchor2.setCol2(2);
            Comment each = drawing.createCellComment(anchor2);
            each.setString(wb.getCreationHelper().createRichTextString("jx:each(items=\"rows\" var=\"row\" lastCell=\"B2\")"));
            data.getCell(0).setCellComment(each);
            wb.write(os);
            return os.toByteArray();
        }
    }

    private Response print(String name, Map<String, ?> params) {
        return given().queryParams(params).get("/api/printtemplates/" + name + "/print");
    }

    @Test
    void htmlRendersRowsFiltersByAliasAndEscapes() {
        String all = print("pt-test-html", Map.of()).then().statusCode(200).header("X-Snello-Rows", "3").extract().asString();
        assertTrue(all.contains("<li>Anna-Rome</li>"), all);
        assertTrue(all.contains("&lt;b&gt;Carla&lt;/b&gt;"), "database values are HTML-escaped: " + all);

        String rome = print("pt-test-html", Map.of("city", "Rome", "_sort", "name:asc")).then().statusCode(200).extract().asString();
        assertTrue(rome.contains("Anna") && !rome.contains("Bruno"), rome);
        assertTrue(rome.contains("<p>2</p>"), rome);
    }

    @Test
    void pdfIsAPdf() {
        Response r = print("pt-test-pdf", Map.of("_inline", "true"));
        r.then().statusCode(200).contentType("application/pdf");
        assertTrue(r.getHeader("Content-Disposition").startsWith("inline; filename=\"pt-test-pdf.pdf\""));
        byte[] bytes = r.asByteArray();
        assertEquals("%PDF", new String(bytes, 0, 4, StandardCharsets.US_ASCII));
    }

    @Test
    void automaticAndTemplatedCsv() {
        String csv = print("pt-test-csv", Map.of("_sort", "name:asc", "_separator", ";")).then().statusCode(200)
                .header("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")).extract().asString();
        String[] lines = csv.split("\r\n");
        assertEquals("name;city;owner", lines[0].toLowerCase());
        assertEquals(4, lines.length, csv);
        String q = print("pt-test-csvq", Map.of("city", "Milan")).then().statusCode(200).extract().asString();
        assertEquals("Bruno;", q);
    }

    @Test
    void xlsxFromJxlsTemplate() throws Exception {
        byte[] bytes = print("pt-test-xlsx", Map.of("city", "Rome")).then().statusCode(200).extract().asByteArray();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            assertEquals("Name", sheet.getRow(0).getCell(0).getStringCellValue());
            java.util.Set<String> names = new java.util.HashSet<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                names.add(sheet.getRow(r).getCell(0).getStringCellValue());
                assertEquals("Rome", sheet.getRow(r).getCell(1).getStringCellValue());
            }
            assertEquals(java.util.Set.of("Anna", "<b>Carla</b>"), names);
        }
    }

    @Test
    void unknownTemplateIs404() {
        print("pt-test-nope", Map.of()).then().statusCode(404);
    }

    @Test
    void anonymousIsRejectedOnProtectedTemplate() {
        print("pt-test-owned", Map.of()).then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "bob", roles = {"User"})
    void userSeesOnlyOwnRowsAndCannotSpoofOwner() {
        String csv = print("pt-test-owned", Map.of("owner", "alice")).then().statusCode(200).extract().asString();
        assertFalse(csv.contains("Anna"), csv);
        assertTrue(csv.contains("Bruno") && csv.contains("Carla"), csv);
    }

    @Test
    @TestSecurity(user = "boss", roles = {"Admin"})
    void adminSeesEveryRow() {
        print("pt-test-owned", Map.of()).then().statusCode(200).header("X-Snello-Rows", "3");
    }

    @Test
    @TestSecurity(user = "boss", roles = {"Admin"})
    void validationOnSave() {
        Map<String, Object> m = new HashMap<>();
        m.put("name", "pt-test-new");
        m.put("export_type", "PDF");
        m.put("sql_query", "SELECT 1 AS one");
        // pdf without body
        given().contentType(ContentType.JSON).body(m).post("/api/printtemplates").then().statusCode(400);
        m.put("html_body", "<p>{count}</p>");
        String uuid = given().contentType(ContentType.JSON).body(m).post("/api/printtemplates")
                .then().statusCode(200).body("export_type", org.hamcrest.Matchers.equalTo("pdf")).extract().path("uuid");
        // duplicate name
        given().contentType(ContentType.JSON).body(m).post("/api/printtemplates").then().statusCode(400);
        // bad name, bad type, xlsx without file, protected without owner column
        given().contentType(ContentType.JSON).body(Map.of("name", "a b", "export_type", "pdf", "sql_query", "x", "html_body", "x"))
                .post("/api/printtemplates").then().statusCode(400);
        given().contentType(ContentType.JSON).body(Map.of("name", "pt-test-t", "export_type", "doc", "sql_query", "x"))
                .post("/api/printtemplates").then().statusCode(400);
        given().contentType(ContentType.JSON).body(Map.of("name", "pt-test-x", "export_type", "xlsx", "sql_query", "x"))
                .post("/api/printtemplates").then().statusCode(400);
        given().contentType(ContentType.JSON).body(Map.of("name", "pt-test-p", "export_type", "csv", "sql_query", "x", "api_protected", true))
                .post("/api/printtemplates").then().statusCode(400);
        // partial update keeps validation on the merged row
        given().contentType(ContentType.JSON).body(Map.of("html_body", "")).put("/api/printtemplates/" + uuid).then().statusCode(400);
        given().contentType(ContentType.JSON).body(Map.of("description", "ok")).put("/api/printtemplates/" + uuid).then().statusCode(200);
        print("pt-test-new", Map.of()).then().statusCode(200).contentType("application/pdf");
    }

    @Test
    @TestSecurity(user = "boss", roles = {"Admin"})
    void templateErrorIsReportedAs400() throws Exception {
        template("pt-test-bad", "html", "<p>{row.missing}</p>", null, false, null);
        print("pt-test-bad", Map.of()).then().statusCode(400).body("error", org.hamcrest.Matchers.notNullValue());
    }
}
