package io.snello;

import io.micronaut.http.client.DefaultHttpClient;
import io.micronaut.http.client.RxHttpClient;
import io.reactivex.Flowable;
import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class RestClientTest {

    @Test
    public void test() throws MalformedURLException {
//        List<RxHttpClient> clients = new ArrayList<>();
//        List<Flowable<String>> results = new ArrayList<>();
//        try {
//            String[] urls = new String[]{"", ""};
//
//            for (String url : urls) {
//                RxHttpClient cl = new DefaultHttpClient(new URL(url));
//                clients.add(cl);
//                Flowable<String> res = cl.retrieve("/");
//                results.add(res);
//            }
//            Flowable.concat(results).flatMap(re -> {
//                return "";
//            });
//
//        } finally {
//            for (RxHttpClient cl : clients) {
//                if (cl != null && cl.isRunning()) {
//                    cl.close();
//                }
//
//            }
//        }
    }
}
