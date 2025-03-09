package org.folio.inventoryimport.test;

import io.restassured.http.Header;
import org.folio.okapi.common.XOkapiHeaders;

public class Statics {

    public static final Header CONTENT_TYPE_XML = new Header("Content-Type", "application/xml");
    public static final Header OKAPI_TOKEN = new Header(XOkapiHeaders.TOKEN,"eyJhbGciOiJIUzUxMiJ9eyJzdWIiOiJhZG1pbiIsInVzZXJfaWQiOiI3OWZmMmE4Yi1kOWMzLTViMzktYWQ0YS0wYTg0MDI1YWIwODUiLCJ0ZW5hbnQiOiJ0ZXN0X3RlbmFudCJ9BShwfHcNClt5ZXJ8ImQTMQtAM1sQEnhsfWNmXGsYVDpuaDN3RVQ9");


    public static final int PORT_INVENTORY_IMPORT = 9230;
    public static final String BASE_URI_INVENTORY_IMPORT = "http://localhost:" + PORT_INVENTORY_IMPORT;
    public static final int PORT_OKAPI = 9031;
    public static final String BASE_URI_OKAPI = "http://localhost:" + PORT_OKAPI;

    public static final Header OKAPI_URL = new Header (XOkapiHeaders.URL, BASE_URI_OKAPI);

}
