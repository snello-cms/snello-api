package io.snello;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static io.snello.util.ParamUtils.*;

public class ConditionsTest {
    // separator serve per aggiungere un separatore dalla condizione precedente
    String separator;
    // query_params sono i parametri da portare come PARAMETRI (IN) per la subquery
    String query_params;
    // condition da valutare per aggiungere condizioni al posto di usare i query param in AND tra loro
    String condition;
    // sub_query viene usata nella WHERE
    String sub_query;

    Map<String, String> httpParameters = new HashMap<>();

    //se condition == true
    // se where non è vuota => uso la condition per agganciare la sub_query


    // DATA FROM DA
    // if (search.getFrom().received_date != null) {}
    // DATA TO A
    // if (search.getTo().received_date != null) {}

    @Test
    public void notNullTest() {
        String notNullCond = "received_date" + NN;
        if (notNullCond.endsWith(NN)) {
            String keySimple = notNullCond.substring(0, notNullCond.length() - NN.length());
            if (!httpParameters.containsKey(keySimple)) {
                Assert.assertNotNull("http parameter dont exist", null);
            } else {
                String value = httpParameters.get(keySimple);
                Assert.assertNotNull("http parameter is null", value);
            }
        }
        sub_query = " a.received_date > ? and a.received_date < ?";
    }

    // data NOT NULL && data NOT EMPTY
    // if (search.getLike().data != null && !search.getLike().data.trim().isEmpty()) {}
    public void notNullAndNotEmptyTest() {
        String notNullAndNotEmpty = "data" + NN + _AND_ + "data" + NIE;
        if (notNullAndNotEmpty.contains(_AND_)) {
            String[] conditions = notNullAndNotEmpty.split(_AND_);
            if (conditions[0].endsWith(NN)) {
                String keySimple1 = conditions[0].substring(0, conditions[0].length() - NN.length());
                if (!httpParameters.containsKey(keySimple1)) {
                    Assert.assertNotNull("http parameter dont exist", null);
                } else {
                    String value1 = httpParameters.get(keySimple1);
                    Assert.assertNotNull("http parameter is null", value1);
                }
            }
            if (conditions[1].endsWith(NIE)) {
                String keySimple2 = conditions[1].substring(0, conditions[1].length() - NIE.length());
                if (!httpParameters.containsKey(keySimple2)) {
                    Assert.assertNotNull("http parameter dont exist", null);
                } else {
                    String value = httpParameters.get(keySimple2);
                    Assert.assertNotNull("http parameter is null", value);
                }
            }
        }
        sub_query = " a.data = ? ";

    }

    // NUM > 0
    // if (search.getObj().num > 0) {}
    public void numberIntervalTest() {
        String numberInterval = "num" + GT + "0";
        if (numberInterval.contains(GT)) {
            String keySimple = numberInterval.substring(0, numberInterval.length() - GT.length());
            if (!httpParameters.containsKey(keySimple)) {
                Assert.assertNotNull("http parameter dont exist", null);
            } else {
                String value = httpParameters.get(keySimple);
                Assert.assertFalse("http parameter less than zero", Integer.valueOf(value) > 0);
            }
        }
        sub_query = " a.num > ? and a.num < ?";
    }


}
