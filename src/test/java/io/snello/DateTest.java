package io.snello;

import org.junit.Test;

import java.time.Instant;

public class DateTest {
    @Test
    public void test() {
        Instant instant = Instant.now();
        System.out.println(Instant.now().toString());
    }
}
