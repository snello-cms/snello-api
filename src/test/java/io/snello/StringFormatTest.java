package io.snello;

import io.snello.repository.h2.H2Constants;
import io.snello.repository.mysql.MysqlConstants;
import io.snello.repository.postgresql.PostgresqlConstants;
import org.junit.Test;

public class StringFormatTest {

    @Test
    public void test() {
        /*
        CREATE TABLE public.libri_autori (
	libri_id varchar_ignorecase(100),
	autori_id varchar_ignorecase(100),
	column1 integer DEFAULT (NEXT VALUE FOR "public"."SYSTEM_SEQUENCE_BF05C13B_4747_43D4_A000_F4F5EFBB2466") NOT NULL AUTO_INCREMENT
);

         */
        System.out.println(String.format(H2Constants.joinTableQuery,
                "libri_autori", "libri_id", "autori_id"
        ));

        System.out.println(String.format(MysqlConstants.joinTableQuery,
                "libri_autori", "libri_id", "autori_id"
        ));

        System.out.println(String.format(PostgresqlConstants.joinTableQuery,
                "libri_autori", "libri_id", "autori_id"
        ));


    }
}
