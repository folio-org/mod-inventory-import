package org.folio.inventoryimport.test.fixtures;

import io.vertx.core.json.JsonObject;
import org.apache.commons.io.FileUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Objects;
import java.util.ArrayList;

import static org.folio.inventoryimport.test.UnitTests.logger;

public class Files {

  public static final String XSLT_EMPTY = getSampleFile("stylesheets/empty.xslt");
  public static final String XSLT_INVALID = getSampleFile("stylesheets/invalid.xslt");
  private static final String instanceTypeId = "30fffe0e-e985-4144-b2e2-1e8179bdb41f";
  public static String XSLT_COPY_XML_DOC = getSampleFile("stylesheets/copyXmlDoc.xslt");

  public static String XML_INVENTORY_RECORD_SET_200 = getSampleFile("sourcefiles/inventoryRecordSet200.xml");
  public static String XML_INVENTORY_RECORD_SET_207 = getSampleFile("sourcefiles/inventoryRecordSet207.xml");
  public static String XML_DELETE_HRID_359314724 = getSampleFile("sourcefiles/delete-359314724.xml");

  public static JsonObject JSON_TRANSFORMATION_CONFIG = new JsonObject(Objects.requireNonNull(getSampleFile("configs/transformation.json")));
  public static JsonObject JSON_IMPORT_CONFIG = new JsonObject(Objects.requireNonNull(getSampleFile("configs/importConfig.json")));
  public static JsonObject JSON_IMPORT_JOB = new JsonObject(Objects.requireNonNull(getSampleFile("jobs/importJob.json")));
  public static JsonObject JSON_FAILED_RECORDS = new JsonObject(Objects.requireNonNull(getSampleFile("jobs/failed-records.json")));
  public static JsonObject JSON_SINGLE_RECORD_UPSERT_RESPONSE_200 = new JsonObject(Objects.requireNonNull(getSampleFile("responses/singleRecordUpsertResponse200.json")));
  public static JsonObject JSON_SINGLE_RECORD_UPSERT_RESPONSE_207 = new JsonObject(Objects.requireNonNull(getSampleFile("responses/singleRecordUpsertResponse207.json")));

  private static String getSampleFile(String filename) {
    try {
      return FileUtils.readFileToString(
              new File("src/test/resources/fixtures/" + filename), "UTF-8");
    } catch (IOException fnfe) {
      logger.error(fnfe.getMessage());
      return null;
    }
  }

  /**
    * Creates [numberOfFiles] files (strings of file content), each with [recordsPerFile] records.
    * @param numberOfFiles number of files to generate
    * @param recordsPerFile number of XML records to create in each file
    * @return List of files (strings of file content)
  */
  public static ArrayList<String> filesOfInventoryXmlRecords (int numberOfFiles, int recordsPerFile) {
      ArrayList<String> sourceFiles = new ArrayList<>();
      for (int files = 0; files < numberOfFiles; files++) {
          int startRecord = files*recordsPerFile+1;
          sourceFiles.add(inventoryXmlRecords(startRecord, startRecord+recordsPerFile));
      }
      return sourceFiles;
  }

   /**
   * Generates an XML document, a `collection` of simple Inventory XML `record`s, each record given a unique instance
   * HRID and title using the numbers in the provided interval
   * @param firstRecord  The number for the first record in the series
   * @param lastRecord  The number of the last record in the series
   * @return a number of XML records (total records = lastRecord - firstRecord)
   */
  public static String inventoryXmlRecords(int firstRecord, int lastRecord)  {
      StringWriter sw = new StringWriter();
      try {
          DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
          DocumentBuilder builder = factory.newDocumentBuilder();
          Document document = builder.newDocument();

          Element root = document.createElement("collection");
          document.appendChild(root);
          for (int i = firstRecord; i < lastRecord; i++) {
              root.appendChild(createUpsertRecord(document, i));
          }
          TransformerFactory transformerFactory = TransformerFactory.newInstance();
          Transformer transformer = transformerFactory.newTransformer();
          transformer.setOutputProperty(OutputKeys.INDENT, "yes");
          DOMSource source = new DOMSource(document);
          transformer.transform(source, new StreamResult(sw));
      } catch (ParserConfigurationException | TransformerException e) {
          throw new RuntimeException(e);
      }
      return sw.toString();
  }

  private static Element createUpsertRecord(Document document, int recNo) {
      Element record = document.createElement("record");
      record.appendChild(createInstance(document, recNo));
      return record;
  }

  private static Element createInstance(Document document, int recNo) {
      Element instance = document.createElement("instance");
      instance.appendChild(createTextElement(document, "source", "SAMPLES"));
      instance.appendChild(createTextElement(document, "hrid", "in-" + recNo));
      instance.appendChild(createTextElement(document, "title", "200 " + recNo));
      instance.appendChild(createTextElement(document, "instanceTypeId", instanceTypeId));
      return instance;
  }

  private static Element createTextElement(Document document, String name, Object value) {
      Element element = document.createElement(name);
      element.appendChild(document.createTextNode(value.toString()));
      return element;
  }
}
