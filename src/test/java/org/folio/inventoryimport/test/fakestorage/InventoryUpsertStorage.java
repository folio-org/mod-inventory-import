package org.folio.inventoryimport.test.fakestorage;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.util.Collection;

import static org.folio.inventoryimport.test.fixtures.Files.JSON_SINGLE_RECORD_UPSERT_RESPONSE_200;
import static org.folio.inventoryimport.test.fixtures.Files.JSON_SINGLE_RECORD_UPSERT_RESPONSE_207;

public class InventoryUpsertStorage extends RecordStorage {
    protected void inventoryBatchUpsertHrid (RoutingContext routingContext) {
        JsonObject upsertBody = routingContext.body().asJsonObject();
        String source = upsertBody.getJsonArray("inventoryRecordSets").getJsonObject(0).getJsonObject("instance").getString("source");
        try {
            upsertInventoryRecords(routingContext);
            Thread.sleep(500); // Fake some response time
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        if (source.endsWith("200")) {
            respond(routingContext, JSON_SINGLE_RECORD_UPSERT_RESPONSE_200, 200);
        } else if (source.endsWith("207")) {
            respond(routingContext, JSON_SINGLE_RECORD_UPSERT_RESPONSE_207, 207);
        } else {
            respond(routingContext, new JsonObject("{ \"testSetupProblem\": true}"), 500);
        }
    }

    @Override
    protected void deleteRecord (RoutingContext routingContext) {
        JsonObject deletionBody = routingContext.body().asJsonObject();
        final String id = deletionBody.getString("hrid");
        int code = delete(id);

        if (code == 200) {
            respond(routingContext, JSON_SINGLE_RECORD_UPSERT_RESPONSE_200, 200);
        } else if (code == 404) {
            respondWithMessage(routingContext, "Not found", 404);
        } else {
            respondWithMessage(routingContext, (failOnDelete ? "Forced " : "") + "Error deleting from " + STORAGE_NAME, code);
        }
    }

    protected void upsertInventoryRecords(RoutingContext routingContext) {
        JsonObject recordsJson = new JsonObject(routingContext.body().asString());
        recordsJson.getJsonArray("inventoryRecordSets").stream()
                .forEach(record -> {
                    JsonObject instance = ((JsonObject) record).getJsonObject("instance");
                    String hrid = instance.getString("hrid");
                    instance.put("id", hrid); // dummy 'id'
                    FolioApiRecord incoming = new FolioApiRecord(instance);
                    FolioApiRecord existing = getRecord(instance.getString("hrid"));
                    if (existing == null) {
                        insert(new FolioApiRecord(instance));
                    } else {
                        update(incoming.getId(), incoming);
                    }
                });
    }

    public Collection<FolioApiRecord> internalGetInstances() {
        return getRecords();
    }

    @Override
    protected String getResultSetName() {
        return null;
    }

    @Override
    protected void declareDependencies() {
    }

    @Override
    protected void declareMandatoryProperties() {
    }
}

