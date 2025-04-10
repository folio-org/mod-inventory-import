package org.folio.inventoryimport.service.fileimport;

public interface RecordReceiver {
    void put(RecordCarrier record);

    void endOfDocument();
}
