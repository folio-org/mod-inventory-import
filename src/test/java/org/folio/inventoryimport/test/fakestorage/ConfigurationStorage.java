package org.folio.inventoryimport.test.fakestorage;

import org.folio.inventoryimport.foliodata.ConfigurationsClient;

public class ConfigurationStorage extends RecordStorage {
    public String getResultSetName() {
        return ConfigurationsClient.RECORDS;
    }

    @Override
    protected void declareDependencies() {
        // Instances have none in fake storage
    }

    @Override
    protected void declareMandatoryProperties() {}


}
