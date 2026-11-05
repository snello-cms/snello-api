package io.snello.test.util;

import org.junit.jupiter.api.Test;

import io.snello.util.ResourceFileUtils;

public class FileTest {
    @Test
    public void test() throws Exception {
        System.out.println(ResourceFileUtils.getExtension("test.mp4"));
    }

}
