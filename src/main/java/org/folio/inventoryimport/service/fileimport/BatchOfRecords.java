package org.folio.inventoryimport.service.fileimport;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.folio.inventoryimport.foliodata.InventoryUpdateClient;

import java.util.ArrayList;

public class BatchOfRecords {

    private final boolean lastBatchOfFile;
    private final ArrayList<ProcessingRecord> batch;
    private InventoryUpdateClient.UpsertResponse upsertResponse;

    public BatchOfRecords(ArrayList<ProcessingRecord> processingRecords, boolean lastBatchOfFile) {
        this.batch = processingRecords;
        this.lastBatchOfFile = lastBatchOfFile;
    }

    public boolean isLastBatchOfFile() {
        return lastBatchOfFile;
    }


    public int size() {
        return batch.size();
    }

    public JsonObject getUpsertRequestBody() {
        return new JsonObject().put("inventoryRecordSets", getRecordsAsJsonArray());
    }

    private JsonArray getRecordsAsJsonArray () {
        JsonArray inventoryRecordSets = new JsonArray();
        for (ProcessingRecord record : batch) {
            inventoryRecordSets.add(record.getRecordAsJson());
        }
        return inventoryRecordSets;
    }

    public void setResponse(InventoryUpdateClient.UpsertResponse upsertResponse) {
        this.upsertResponse = upsertResponse;
    }

    public JsonArray getErrors() {
        return upsertResponse.getErrors();
    }


    public ProcessingRecord get(int index) {
        return batch.get(index);
    }
 }
