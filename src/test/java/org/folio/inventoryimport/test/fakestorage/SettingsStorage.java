package org.folio.inventoryimport.test.fakestorage;

import org.folio.inventoryimport.foliodata.SettingsClient;

public class SettingsStorage extends RecordStorage {
    public String getResultSetName() {
        return SettingsClient.RECORDS;
    }

    @Override
    protected void declareDependencies() {
        // Instances have none in fake storage
    }

    @Override
    protected void declareMandatoryProperties() {}

    protected void declareUniqueProperties() {
        mandatoryProperties.add("id");
        mandatoryProperties.add("scope");
        mandatoryProperties.add("key");
        mandatoryProperties.add("value");
    }


}
