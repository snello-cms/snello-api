package io.snello;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.reactivex.Flowable;
import io.snello.model.Document;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DocumentsControllerTest extends ControllerTest {

    //TODO call create, get and delete

    @Test
    public void testGetDocuments() {
        Flowable<HttpResponse<List>> flowable = Flowable.fromPublisher(
                client.exchange(HttpRequest.GET("/api/documents"), List.class)
        );

        HttpResponse<List> response = flowable.blockingFirst();

        assertEquals(HttpStatus.OK, response.status());
        Optional<List> maybeDocuments = response.getBody(List.class);
        assertTrue(maybeDocuments.isPresent());

        List<Document> documents = (List<Document>)maybeDocuments.get();
        assertTrue(documents.size() >= 0);
    }

}
