package org.folio.inventoryimport.test.fakestorage;

import io.vertx.ext.web.RoutingContext;

import static org.folio.inventoryimport.test.fakestorage.RecordStorage.respond;
import static org.folio.inventoryimport.test.fixtures.Files.JSON_SINGLE_RECORD_UPSERT_RESPONSE_OK;

public class InventoryUpsert {
    protected void inventoryBatchUpsertHrid (RoutingContext routingContext) {
       respond(routingContext, JSON_SINGLE_RECORD_UPSERT_RESPONSE_OK, 200);
    }
}

