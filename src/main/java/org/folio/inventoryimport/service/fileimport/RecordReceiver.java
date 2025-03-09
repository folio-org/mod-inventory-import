package org.folio.inventoryimport.service.fileimport;

public interface RecordReceiver {
    void put(String record);

    void endOfDocument();
}
