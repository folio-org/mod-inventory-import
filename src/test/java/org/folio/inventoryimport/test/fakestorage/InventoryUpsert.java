package org.folio.inventoryimport.test.fakestorage;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import static org.folio.inventoryimport.test.fakestorage.RecordStorage.respond;
import static org.folio.inventoryimport.test.fixtures.Files.JSON_SINGLE_RECORD_UPSERT_RESPONSE_200;
import static org.folio.inventoryimport.test.fixtures.Files.JSON_SINGLE_RECORD_UPSERT_RESPONSE_207;

public class InventoryUpsert {
    protected void inventoryBatchUpsertHrid (RoutingContext routingContext) {
        JsonObject upsertBody = routingContext.body().asJsonObject();
        String title = upsertBody.getJsonArray("inventoryRecordSets").getJsonObject(0).getJsonObject("instance").getString("title");
        if (title.equals("200")) {
            respond(routingContext, JSON_SINGLE_RECORD_UPSERT_RESPONSE_200, 200);
        } else if (title.equals("207")) {
            respond(routingContext, JSON_SINGLE_RECORD_UPSERT_RESPONSE_207, 207);
        } else {
            respond(routingContext, new JsonObject("{ \"testSetupProblem\": true)"), 500);
        }
    }
}

