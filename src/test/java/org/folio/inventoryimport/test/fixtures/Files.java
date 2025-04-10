package org.folio.inventoryimport.test.fixtures;

import io.vertx.core.json.JsonObject;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import static org.folio.inventoryimport.test.UnitTests.logger;

public class Files {

  public static final String XSLT_EMPTY = getSampleFile("stylesheets/empty.xslt");
  public static final String XSLT_INVALID = getSampleFile("stylesheets/invalid.xslt");
  public static String XSLT_COPY_XML_DOC = getSampleFile("stylesheets/copyXmlDoc.xslt");

  public static String XML_INVENTORY_RECORD_SET = getSampleFile("sourcefiles/inventoryRecordSet.xml");

  public static JsonObject JSON_TRANSFORMATION_CONFIG = new JsonObject(Objects.requireNonNull(getSampleFile("configs/transformation.json")));
  public static JsonObject JSON_IMPORT_CONFIG = new JsonObject(Objects.requireNonNull(getSampleFile("configs/importConfig.json")));
  public static JsonObject JSON_IMPORT_JOB = new JsonObject(Objects.requireNonNull(getSampleFile("jobs/importJob.json")));
  public static JsonObject JSON_FAILED_RECORDS = new JsonObject(Objects.requireNonNull(getSampleFile("jobs/failed-records.json")));

  public static JsonObject JSON_SINGLE_RECORD_UPSERT_RESPONSE_OK = new JsonObject(Objects.requireNonNull(getSampleFile("responses/singleRecordUpsertResponse.json")));

  private static String getSampleFile(String filename) {
    try {
      return FileUtils.readFileToString(
              new File("src/test/resources/fixtures/" + filename), "UTF-8");
    } catch (IOException fnfe) {
      logger.error(fnfe.getMessage());
      return null;
    }
  }
}
