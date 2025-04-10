package org.folio.inventoryimport.test.fixtures;

import io.vertx.core.json.JsonObject;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import static org.folio.inventoryimport.test.UnitTests.logger;

public class SampleFiles {

  public static final String EMPTY_XSLT = getSampleFile("stylesheets/empty.xslt");
  public static final String INVALID_XSLT = getSampleFile("stylesheets/invalid.xslt");
  public static String COPY_XML_DOC_XSLT = getSampleFile("stylesheets/copyXmlDoc.xslt");
  public static String INVENTORY_RECORD_SET_XML = getSampleFile("sourcefiles/inventoryRecordSet.xml");
  public static JsonObject TRANSFORMATION_JSON = new JsonObject(Objects.requireNonNull(getSampleFile("configs/transformation.json")));
  public static JsonObject IMPORT_CONFIG_JSON = new JsonObject(Objects.requireNonNull(getSampleFile("configs/importConfig.json")));
  public static JsonObject IMPORT_JOB_JSON = new JsonObject(Objects.requireNonNull(getSampleFile("jobs/importJob.json")));
  public static JsonObject FAILED_RECORDS_JSON = new JsonObject(Objects.requireNonNull(getSampleFile("jobs/failed-records.json")));

  private static String getSampleFile(String filename) {
    try {
      return FileUtils.readFileToString(
              new File("src/test/resources/samples/" + filename), "UTF-8");
    } catch (IOException fnfe) {
      logger.error(fnfe.getMessage());
      return null;
    }
  }
}
