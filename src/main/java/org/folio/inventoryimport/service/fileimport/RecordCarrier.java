package org.folio.inventoryimport.service.fileimport;

public class RecordCarrier {
    String original;
    String record;

    public RecordCarrier(String original) {
        this.original = original;
        record = original;
    }

    public void update(String record) {
        this.record = record;
    }
    public String getRecord() {
        return record;
    }

}
