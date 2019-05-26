package io.snello;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.reactivex.Flowable;
import io.snello.model.Metadata;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MetadataControllerTest extends ControllerTest {

    //TODO call create, get and delete

    @Test
    public void testGetMetadatas() {
        Flowable<HttpResponse<List>> flowable = Flowable.fromPublisher(
                client.exchange(HttpRequest.GET("/api/metadatas"), List.class)
        );

        HttpResponse<List> response = flowable.blockingFirst();

        assertEquals(HttpStatus.OK, response.status());
        Optional<List> maybeMetadatas = response.getBody(List.class);
        assertTrue(maybeMetadatas.isPresent());

        List<Metadata> metadatas = (List<Metadata>)maybeMetadatas.get();
        assertTrue(metadatas.size() >= 0);
    }

}
