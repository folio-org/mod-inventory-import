package org.folio.inventoryimport.test;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.HttpClientConfig;
import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.inventoryimport.MainVerticle;
import org.folio.inventoryimport.service.fileimport.FileQueue;
import org.folio.inventoryimport.test.fakestorage.FakeFolioApis;
import org.folio.inventoryimport.test.fixtures.Files;
import org.folio.inventoryimport.test.fixtures.Service;
import org.folio.inventoryimport.utils.SettableClock;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.tlib.postgres.testing.TenantPgPoolContainer;
import org.junit.*;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.folio.inventoryimport.test.fixtures.Service.*;
import static org.folio.inventoryimport.test.fixtures.Files.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@RunWith(VertxUnitRunner.class)
public class UnitTests {
    public static final Logger logger = LoggerFactory.getLogger(UnitTests.class);
    static Vertx vertx;
    private static FakeFolioApis fakeFolioApis;
    public static final Header CONTENT_TYPE_XML = new Header("Content-Type", "application/xml");
    public static final Header CONTENT_TYPE_JSON = new Header("Content-Type", "application/json");
    public static final String TRANSFORMATION_ID = "caf7dbe7-0cfc-4343-b1db-04671ac3e40a";
    public static final String IMPORT_CONFIG_ID = "9ac8c174-fdd3-4380-a2f9-135b33df9795";
    public static final String IMPORT_CONFIG_NAME = "test config";
    public static final String IMPORT_JOB_ID = "cda450e1-46bf-4bb9-9741-876ec395d5e9";
    public static final String STEP_ID = "66d5ef34-ee3d-434c-a07d-80dbfdb31b6e";

    @ClassRule
    public static PostgreSQLContainer<?> postgresSQLContainer = TenantPgPoolContainer.create();


    @Rule
    public final TestName name = new TestName();

    @BeforeClass
    public static void beforeClass(TestContext context) {
        vertx = Vertx.vertx();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        RestAssured.baseURI = Service.BASE_URI_INVENTORY_IMPORT;
        RestAssured.requestSpecification = new RequestSpecBuilder().build();

        DeploymentOptions deploymentOptions = new DeploymentOptions();
        deploymentOptions.setConfig(new JsonObject().put("port", Integer.toString(Service.PORT_INVENTORY_IMPORT)));
        vertx.deployVerticle(new MainVerticle(), deploymentOptions)
                .onComplete(context.asyncAssertSuccess(x ->
                        fakeFolioApis = new FakeFolioApis(vertx, context)));
        vertx.fileSystem().deleteRecursive(FileQueue.SOURCE_FILES_ROOT_DIR,true);
    }

    @AfterClass
    public static void afterClass(TestContext context) {
        vertx.close().onComplete(context.asyncAssertSuccess());
    }

    @Before
    public void initSchema() {
        tenantOp(TENANT, new JsonObject()
                        .put("module_to", "mod-inventory-import-1.0.0")
                , null);
    }

    @After
    public void cleanUpTestRecords() {
        tenantOp(TENANT, new JsonObject()
                .put("module_from", "mod-inventory-import-1.0.0")
                .put("purge", true), null);
        fakeFolioApis.configurationStorage.wipeMockRecords();
        fakeFolioApis.settingsStorage.wipeMockRecords();
    }

    void tenantOp(String tenant, JsonObject tenantAttributes, String expectedError) {
        ExtractableResponse<Response> response = RestAssured.given()
                .header(XOkapiHeaders.TENANT, tenant)
                .header(OKAPI_URL)
                .contentType(ContentType.JSON)
                .body(tenantAttributes.encode())
                .post("/_/tenant")
                .then()
                .extract();

        logger.info(response.asString());
        if (response.statusCode() == 204) {
            return;
        }
        assertThat(response.statusCode(), is(201));
        String location = response.header("Location");
        JsonObject tenantJob = new JsonObject(response.asString());
        assertThat(location, is("/_/tenant/" + tenantJob.getString("id")));

        RestAssured.given()
                .header(XOkapiHeaders.TENANT, tenant)
                .get(location + "?wait=10000")
                .then().statusCode(200)
                .body("complete", is(true))
                .body("error", is(expectedError));

        RestAssured.given()
                .header(XOkapiHeaders.TENANT, tenant)
                .delete(location)
                .then().statusCode(204);
    }

    public static RequestSpecification timeoutConfig(int timeOutInMilliseconds) {
        return new RequestSpecBuilder()
                .setConfig(RestAssured.config()
                        .httpClient(HttpClientConfig.httpClientConfig()
                                .setParam("http.connection.timeout", timeOutInMilliseconds)
                                .setParam("http.socket.timeout", timeOutInMilliseconds)))
                .build();
    }
    
    @Test
    public void canPostAndGetTransformation() {
        JsonObject transformation = JSON_TRANSFORMATION;
        String id = transformation.getString("id");

        postJsonObject(PATH_TRANSFORMATIONS, JSON_TRANSFORMATION);
        getRecordById(PATH_TRANSFORMATIONS, id);
        assertThat(getRecords(PATH_TRANSFORMATIONS).extract().path("totalRecords"), is(1));
    }

    @Test
    public void canPostAndGetStepAndGetXslt() {
        JsonObject step = new JsonObject();
        step.put("id", STEP_ID)
                .put("name", "test step")
                .put("enabled", true)
                .put("script", Files.XSLT_COPY_XML_DOC);

        postJsonObject(PATH_STEPS, step);
        getRecordById(PATH_STEPS, STEP_ID).extract().response().getBody().prettyPrint();
        assertThat(getRecords(PATH_STEPS).extract().path("totalRecords"), is(1));
        await().until(() -> getRecords(PATH_STEPS + "/" + STEP_ID + "/script").extract().asPrettyString(), equalTo(Files.XSLT_COPY_XML_DOC));
    }

    @Test
    public void cannotPostStepWithInvalidXslt() {
        JsonObject step = new JsonObject();
        step.put("id", STEP_ID)
                .put("name", "test step")
                .put("enabled", true)
                .put("script", Files.XSLT_INVALID);

        given()
                .baseUri(BASE_URI_INVENTORY_IMPORT)
                .header(OKAPI_TENANT)
                .header(OKAPI_URL)
                .body(step.encodePrettily())
                .header(CONTENT_TYPE_JSON)
                .post(PATH_STEPS)
                .then()
                .statusCode(400);
    }

    @Test
    public void canUpdateTheXsltOfAStep() {
        JsonObject step = new JsonObject();
        step.put("id", STEP_ID)
                .put("name", "test step")
                .put("enabled", true)
                .put("script", XSLT_EMPTY);
        postJsonObject(PATH_STEPS, step);
        getRecordById(PATH_STEPS, STEP_ID).extract().response().getBody().prettyPrint();
        await().until(() -> getRecords(PATH_STEPS + "/" + STEP_ID + "/script").extract().asPrettyString(), equalTo(XSLT_EMPTY));
        putXml(PATH_STEPS + "/" + STEP_ID + "/script", Files.XSLT_COPY_XML_DOC);
        await().until(() -> getRecords(PATH_STEPS + "/" + STEP_ID + "/script").extract().asPrettyString(), equalTo(Files.XSLT_COPY_XML_DOC));
    }

    @Test
    public void cannotUpdateStepWithInvalidXslt() {
        JsonObject step = new JsonObject();
        step.put("id", STEP_ID)
                .put("name", "test step")
                .put("enabled", true)
                .put("script", XSLT_EMPTY);

        given()
                .baseUri(BASE_URI_INVENTORY_IMPORT)
                .header(OKAPI_TENANT)
                .header(OKAPI_URL)
                .body(Files.XSLT_INVALID)
                .header(CONTENT_TYPE_XML)
                .put(PATH_STEPS + "/" + STEP_ID + "/script")
                .then()
                .statusCode(400);
    }

    @Test
    public void cannotUpdateXsltOfNonExistingStep() {
        given()
                .baseUri(BASE_URI_INVENTORY_IMPORT)
                .header(OKAPI_TENANT)
                .header(OKAPI_URL)
                .body(XSLT_EMPTY)
                .header(CONTENT_TYPE_XML)
                .put(PATH_STEPS + "/" + STEP_ID + "/script")
                .then()
                .statusCode(404);
    }

    @Test
    public void cannotGetTheXsltOfNonExistingStep() {
        given()
                .baseUri(BASE_URI_INVENTORY_IMPORT)
                .header(OKAPI_TENANT)
                .header(OKAPI_URL)
                .header(CONTENT_TYPE_XML)
                .get(PATH_STEPS + "/" + STEP_ID + "/script")
                .then()
                .statusCode(404);
    }


    @Test
    public void canInsertStepIntoPipeline () {
        JsonObject step = new JsonObject();
        step.put("id", STEP_ID)
                .put("name", "test step")
                .put("enabled", true)
                .put("script", Files.XSLT_COPY_XML_DOC);
        postJsonObject(PATH_STEPS, step);

        postJsonObject(PATH_TRANSFORMATIONS, JSON_TRANSFORMATION);

        JsonObject tsa = new JsonObject();
        tsa.put("stepId", STEP_ID)
                .put("transformationId", JSON_TRANSFORMATION.getString("id"))
                .put("position", "1");
        postJsonObject(PATH_TSAS, tsa);

        getRecords(PATH_TSAS+"?query=transformationId="+ JSON_TRANSFORMATION.getString("id"))
                .body("totalRecords" , is(1));

    }

    @Test
    public void canPostAndGetImportConfig() {
        postJsonObject(PATH_TRANSFORMATIONS, JSON_TRANSFORMATION);
        postJsonObject(PATH_IMPORT_CONFIGS, JSON_IMPORT_CONFIG);
        getRecords(PATH_IMPORT_CONFIGS)
                .body("totalRecords", is(1));
    }

    @Test
    public void canPostAndGetAndDeleteImportJob() {
        postJsonObject(PATH_TRANSFORMATIONS, JSON_TRANSFORMATION);
        postJsonObject(PATH_IMPORT_CONFIGS, JSON_IMPORT_CONFIG);
        postJsonObject(PATH_IMPORT_JOBS, JSON_IMPORT_JOB);
        getRecords(PATH_IMPORT_JOBS).body("totalRecords", is(1));
        deleteRecord(PATH_IMPORT_JOBS, JSON_IMPORT_JOB.getString("id"));
        getRecords(PATH_IMPORT_JOBS).body("totalRecords", is(0));
    }

    @Test
    public void canPostLogLines() {
        postJsonObject(PATH_TRANSFORMATIONS, JSON_TRANSFORMATION);
        postJsonObject(PATH_IMPORT_CONFIGS, JSON_IMPORT_CONFIG);
        postJsonObject(PATH_IMPORT_JOBS, JSON_IMPORT_JOB);

        String importJobId = JSON_IMPORT_JOB.getString("id");
        String importConfigName = JSON_IMPORT_CONFIG.getString("name");

        JsonArray logLines = new JsonArray();
        logLines.add(new JsonObject()
                .put("importJobId", importJobId)
                .put("timeStamp", SettableClock.getLocalDateTime().toString())
                .put("jobLabel", importConfigName)
                .put("line", "log line 1"));
        logLines.add(new JsonObject()
                .put("importJobId", importJobId)
                .put("timeStamp", SettableClock.getLocalDateTime().toString())
                .put("jobLabel", importConfigName)
                .put("line", "log line 2"));
        JsonObject request = new JsonObject().put("logLines", logLines);
        postJsonObject(PATH_IMPORT_JOBS+"/"+importJobId+"/log", request);
        getRecords(PATH_IMPORT_JOBS + "/" + importJobId + "/log")
                .body("totalRecords", is(2));
    }

    @Test
    public void canPostFailedRecords() {
        postJsonObject(PATH_TRANSFORMATIONS, JSON_TRANSFORMATION);
        postJsonObject(PATH_IMPORT_CONFIGS, JSON_IMPORT_CONFIG);
        postJsonObject(PATH_IMPORT_JOBS, JSON_IMPORT_JOB);
        postJsonObject(PATH_IMPORT_JOBS + "/" + JSON_IMPORT_JOB.getString("id") + "/failed-records", JSON_FAILED_RECORDS);
        getRecords(PATH_IMPORT_JOBS + "/" + JSON_IMPORT_JOB.getString("id") + "/failed-records")
                .body("totalRecords", is(5));
    }

    @Test
    public void canImportTransformedXml() {
        postJsonObject(PATH_TRANSFORMATIONS, JSON_TRANSFORMATION);

        JsonObject step = new JsonObject();
        step.put("id", STEP_ID)
                .put("name", "test step")
                .put("enabled", true)
                .put("script", Files.XSLT_COPY_XML_DOC);
        postJsonObject(PATH_STEPS, step);
        JsonObject tsa = new JsonObject();
        tsa.put("stepId", STEP_ID)
                .put("transformationId", JSON_TRANSFORMATION.getString("id"))
                .put("position", "1");
        postJsonObject(PATH_TSAS, tsa);
        postJsonObject(PATH_IMPORT_CONFIGS, JSON_IMPORT_CONFIG);

        String importConfigId = JSON_IMPORT_CONFIG.getString("id");
        String transformationId = JSON_TRANSFORMATION.getString("id");

        getRecordById(PATH_IMPORT_CONFIGS, importConfigId);
        getRecordById(PATH_TRANSFORMATIONS, transformationId);
        postSourceXml(BASE_PATH_IMPORT_XML_FILE + "/" + importConfigId + "/import", XML_INVENTORY_RECORD_SET);
        await().until(() ->  getTotalRecords(PATH_IMPORT_JOBS), is(1));
        String jobId = getRecords(PATH_IMPORT_JOBS).extract().path("importJobs[0].id");
        String started = getRecordById(PATH_IMPORT_JOBS, jobId).extract().path("started");
        await().until(() -> getRecordById(PATH_IMPORT_JOBS, jobId).extract().path("finished"), greaterThan(started));
        await().until(() ->  getTotalRecords(PATH_IMPORT_JOBS + "/" + importConfigId + "/log"), is(4));
    }
    
    

    @Test
    public void willPurgeAgedJobLogsUsingDefaultThreshold() {
        Response response = given().port(PORT_INVENTORY_IMPORT)
                .header(OKAPI_TENANT)
                .get("inventory-import/import-jobs")
                .then()
                .log().ifValidationFails().statusCode(200).extract().response();
        logger.info("will purge jobs response: " + response.asPrettyString());

        given().port(PORT_INVENTORY_IMPORT)
                .header(OKAPI_TENANT)
                .get("inventory-import/import-jobs")
                .then()
                .log().ifValidationFails().statusCode(200).extract().response();

        JsonObject transformation = new JsonObject();
        transformation.put("id", TRANSFORMATION_ID)
                .put("name", "testTransformation");
        postJsonObject(PATH_TRANSFORMATIONS, transformation);

        JsonObject importConfig = new JsonObject();
        importConfig
                .put("id", IMPORT_CONFIG_ID)
                .put("name", IMPORT_CONFIG_NAME)
                .put("type", "XML-BULK")
                .put("url", "NA")
                .put("transformationId", TRANSFORMATION_ID);
        postJsonObject(PATH_IMPORT_CONFIGS, importConfig);


        LocalDateTime now = LocalDateTime.now();
        final LocalDateTime agedJobStartedTime = now.minusMonths(3).minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        final LocalDateTime agedJobFinishedTime = agedJobStartedTime.plusMinutes(2);
        final LocalDateTime intermediateJobStartedTime = now.minusMonths(2).minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        final LocalDateTime intermediateJobFinishedTime = intermediateJobStartedTime.plusMinutes(2);
        final LocalDateTime newerJobStartedTime = now.minusMonths(2).truncatedTo(ChronoUnit.SECONDS);
        final LocalDateTime newerJobFinishedTime = newerJobStartedTime.plusMinutes(3);

        JsonObject agedJobJson =
                new JsonObject(
                        "    {\n" +
                                "      \"id\" : \"" + UUID.randomUUID() + "\",\n" +
                                "      \"importConfigName\" : \"fake job log\",\n" +
                                "      \"importConfigId\" : \"" + IMPORT_CONFIG_ID + "\",\n" +
                                "      \"type\" : \"xmlBulk\",\n" +
                                "      \"url\" : \"http://fileserver/xml/\",\n" +
                                "      \"allowErrors\" : true,\n" +
                                "      \"transformation\" : \"12345\",\n" +
                                "      \"storage\" : \"Batch Upsert Inventory\",\n" +
                                "      \"status\" : \"OK\",\n" +
                                "      \"started\" : \"" + agedJobStartedTime + "\",\n" +
                                "      \"finished\" : \"" + agedJobFinishedTime + "\",\n" +
                                "      \"amountHarvested\" : 5,\n" +
                                "      \"message\" : \"  Instances_processed/loaded/deletions(signals)/failed:__5___5___0(0)___0_ Holdings_records_processed/loaded/deleted/failed:__13___13___0___0_ Items_processed/loaded/deleted/failed:__4___4___0___0_ Source_records_processed/loaded/deleted/failed:__0___0___0___0_\"\n" +
                                "    }\n");

        JsonObject intermediateJobJson =
                new JsonObject(
                        "    {\n" +
                                "      \"id\" : \"" + UUID.randomUUID() + "\",\n" +
                                "      \"importConfigName\" : \"fake job log\",\n" +
                                "      \"importConfigId\" : \"" + IMPORT_CONFIG_ID + "\",\n" +
                                "      \"type\" : \"xmlBulk\",\n" +
                                "      \"url\" : \"http://fileserver/xml/\",\n" +
                                "      \"allowErrors\" : true,\n" +
                                "      \"transformation\" : \"12345\",\n" +
                                "      \"storage\" : \"Batch Upsert Inventory\",\n" +
                                "      \"status\" : \"OK\",\n" +
                                "      \"started\" : \"" + intermediateJobStartedTime + "\",\n" +
                                "      \"finished\" : \"" + intermediateJobFinishedTime + "\",\n" +
                                "      \"amountHarvested\" : 5,\n" +
                                "      \"message\" : \"  Instances_processed/loaded/deletions(signals)/failed:__5___5___0(0)___0_ Holdings_records_processed/loaded/deleted/failed:__13___13___0___0_ Items_processed/loaded/deleted/failed:__4___4___0___0_ Source_records_processed/loaded/deleted/failed:__0___0___0___0_\"\n" +
                                "    }\n");

        JsonObject newerJobJson =
                new JsonObject(
                        "    {\n" +
                                "      \"id\" : \"" + UUID.randomUUID() + "\",\n" +
                                "      \"importConfigName\" : \"fake job log\",\n" +
                                "      \"importConfigId\" : \"" + IMPORT_CONFIG_ID + "\",\n" +
                                "      \"type\" : \"xmlBulk\",\n" +
                                "      \"url\" : \"http://fileserver/xml/\",\n" +
                                "      \"allowErrors\" : true,\n" +
                                "      \"transformation\" : \"12345\",\n" +
                                "      \"storage\" : \"Batch Upsert Inventory\",\n" +
                                "      \"status\" : \"OK\",\n" +
                                "      \"started\" : \"" + newerJobStartedTime + "\",\n" +
                                "      \"finished\" : \"" + newerJobFinishedTime + "\",\n" +
                                "      \"amountHarvested\" : 3,\n" +
                                "      \"message\" : \"  Instances_processed/loaded/deletions(signals)/failed:__3___3___0(0)___0_ Holdings_records_processed/loaded/deleted/failed:__8___8___0___0_ Items_processed/loaded/deleted/failed:__2___2___0___0_ Source_records_processed/loaded/deleted/failed:__0___0___0___0_\"\n" +
                                "    }\n");

        given().port(PORT_INVENTORY_IMPORT).header(OKAPI_TENANT)
                .body(agedJobJson.encode())
                .contentType(ContentType.JSON)
                .post("inventory-import/import-jobs")
                .then()
                .log().ifValidationFails().statusCode(201).extract().response();

        given().port(PORT_INVENTORY_IMPORT).header(OKAPI_TENANT)
                .body(intermediateJobJson.encode())
                .contentType(ContentType.JSON)
                .post("inventory-import/import-jobs")
                .then()
                .log().ifValidationFails().statusCode(201).extract().response();

        given().port(PORT_INVENTORY_IMPORT).header(OKAPI_TENANT)
                .body(newerJobJson.encode())
                .contentType(ContentType.JSON)
                .post("inventory-import/import-jobs")
                .then()
                .log().ifValidationFails().statusCode(201).extract().response();

        RestAssured
                .given()
                .port(PORT_INVENTORY_IMPORT)
                .header(OKAPI_TENANT)
                .contentType(ContentType.JSON)
                .get("inventory-import/import-jobs")
                .then().statusCode(200)
                .body("totalRecords", is(3));

        final RequestSpecification timeoutConfig = timeoutConfig(10000);

        given()
                .port(Service.PORT_OKAPI)
                .header(OKAPI_TENANT)
                .header(OKAPI_URL)
                .header(OKAPI_TOKEN)
                .contentType(ContentType.JSON)
                .header(XOkapiHeaders.REQUEST_ID, "purge-aged-logs")
                .spec(timeoutConfig)
                .when().post("inventory-import/purge-aged-logs")
                .then().log().ifValidationFails().statusCode(204)
                .extract().response();

        RestAssured
                .given()
                .port(PORT_INVENTORY_IMPORT)
                .header(OKAPI_TENANT)
                .contentType(ContentType.JSON)
                .get("inventory-import/import-jobs")
                .then().statusCode(200)
                .body("totalRecords", is(2));

    }

    @Test
    public void willPurgeAgedJobLogsUsingSettingsEntry() {

        Response response = given().port(PORT_INVENTORY_IMPORT)
                .header(OKAPI_TENANT)
                .get("inventory-import/import-jobs")
                .then()
                .log().ifValidationFails().statusCode(200).extract().response();
        logger.info("will purge jobs response: " + response.asPrettyString());

        given().port(PORT_INVENTORY_IMPORT)
                .header(OKAPI_TENANT)
                .get("inventory-import/import-jobs")
                .then()
                .log().ifValidationFails().statusCode(200).extract().response();

        JsonObject transformation = new JsonObject();
        transformation.put("id", TRANSFORMATION_ID)
                .put("name", "testTransformation");
        postJsonObject(PATH_TRANSFORMATIONS, transformation);

        JsonObject importConfig = new JsonObject();
        importConfig
                .put("id", IMPORT_CONFIG_ID)
                .put("name", IMPORT_CONFIG_NAME)
                .put("type", "XML-BULK")
                .put("url", "NA")
                .put("transformationId", TRANSFORMATION_ID);
        postJsonObject(PATH_IMPORT_CONFIGS, importConfig);

        Response response2 = given().port(PORT_INVENTORY_IMPORT)
                .header(OKAPI_TENANT)
                .get("inventory-import/import-jobs")
                .then()
                .log().ifValidationFails().statusCode(200).extract().response();
        logger.info("will purge jobs response: " + response2.asPrettyString());

        given().port(PORT_INVENTORY_IMPORT)
                .header(OKAPI_TENANT)
                .get("inventory-import/import-jobs")
                .then()
                .log().ifValidationFails().statusCode(200).extract().response();

        LocalDateTime now = LocalDateTime.now();
        final LocalDateTime agedJobStartedTime = now.minusMonths(3).minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        final LocalDateTime agedJobFinishedTime = agedJobStartedTime.plusMinutes(2);
        final LocalDateTime intermediateJobStartedTime = now.minusMonths(2).minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        final LocalDateTime intermediateJobFinishedTime = intermediateJobStartedTime.plusMinutes(2);
        final LocalDateTime newerJobStartedTime = now.minusMonths(2).truncatedTo(ChronoUnit.SECONDS);
        final LocalDateTime newerJobFinishedTime = newerJobStartedTime.plusMinutes(3);

        JsonObject agedJobJson =
                new JsonObject(
                        "    {\n" +
                                "      \"id\" : \"" + UUID.randomUUID() + "\",\n" +
                                "      \"importConfigName\" : \"fake job log\",\n" +
                                "      \"importConfigId\" : \"" + IMPORT_CONFIG_ID + "\",\n" +
                                "      \"type\" : \"xmlBulk\",\n" +
                                "      \"url\" : \"http://fileserver/xml/\",\n" +
                                "      \"allowErrors\" : true,\n" +
                                "      \"transformation\" : \"12345\",\n" +
                                "      \"storage\" : \"Batch Upsert Inventory\",\n" +
                                "      \"status\" : \"OK\",\n" +
                                "      \"started\" : \"" + agedJobStartedTime + "\",\n" +
                                "      \"finished\" : \"" + agedJobFinishedTime + "\",\n" +
                                "      \"amountHarvested\" : 5,\n" +
                                "      \"message\" : \"  Instances_processed/loaded/deletions(signals)/failed:__5___5___0(0)___0_ Holdings_records_processed/loaded/deleted/failed:__13___13___0___0_ Items_processed/loaded/deleted/failed:__4___4___0___0_ Source_records_processed/loaded/deleted/failed:__0___0___0___0_\"\n" +
                                "    }\n");

        JsonObject intermediateJobJson =
                new JsonObject(
                        "    {\n" +
                                "      \"id\" : \"" + UUID.randomUUID() + "\",\n" +
                                "      \"importConfigName\" : \"fake job log\",\n" +
                                "      \"importConfigId\" : \"" + IMPORT_CONFIG_ID + "\",\n" +
                                "      \"type\" : \"xmlBulk\",\n" +
                                "      \"url\" : \"http://fileserver/xml/\",\n" +
                                "      \"allowErrors\" : true,\n" +
                                "      \"transformation\" : \"12345\",\n" +
                                "      \"storage\" : \"Batch Upsert Inventory\",\n" +
                                "      \"status\" : \"OK\",\n" +
                                "      \"started\" : \"" + intermediateJobStartedTime + "\",\n" +
                                "      \"finished\" : \"" + intermediateJobFinishedTime + "\",\n" +
                                "      \"amountHarvested\" : 5,\n" +
                                "      \"message\" : \"  Instances_processed/loaded/deletions(signals)/failed:__5___5___0(0)___0_ Holdings_records_processed/loaded/deleted/failed:__13___13___0___0_ Items_processed/loaded/deleted/failed:__4___4___0___0_ Source_records_processed/loaded/deleted/failed:__0___0___0___0_\"\n" +
                                "    }\n");


        JsonObject newerJobJson =
                new JsonObject(
                        "    {\n" +
                                "      \"id\" : \"" + UUID.randomUUID() + "\",\n" +
                                "      \"importConfigName\" : \"fake job log\",\n" +
                                "      \"importConfigId\" : \"" + IMPORT_CONFIG_ID + "\",\n" +
                                "      \"type\" : \"xmlBulk\",\n" +
                                "      \"url\" : \"http://fileserver/xml/\",\n" +
                                "      \"allowErrors\" : true,\n" +
                                "      \"transformation\" : \"12345\",\n" +
                                "      \"storage\" : \"Batch Upsert Inventory\",\n" +
                                "      \"status\" : \"OK\",\n" +
                                "      \"started\" : \"" + newerJobStartedTime + "\",\n" +
                                "      \"finished\" : \"" + newerJobFinishedTime + "\",\n" +
                                "      \"amountHarvested\" : 3,\n" +
                                "      \"message\" : \"  Instances_processed/loaded/deletions(signals)/failed:__3___3___0(0)___0_ Holdings_records_processed/loaded/deleted/failed:__8___8___0___0_ Items_processed/loaded/deleted/failed:__2___2___0___0_ Source_records_processed/loaded/deleted/failed:__0___0___0___0_\"\n" +
                                "    }\n");

        given().port(PORT_INVENTORY_IMPORT).header(OKAPI_TENANT)
                .body(agedJobJson.encode())
                .contentType(ContentType.JSON)
                .post("inventory-import/import-jobs")
                .then()
                .log().ifValidationFails().statusCode(201).extract().response();

        given().port(PORT_INVENTORY_IMPORT).header(OKAPI_TENANT)
                .body(intermediateJobJson.encode())
                .contentType(ContentType.JSON)
                .post("inventory-import/import-jobs")
                .then()
                .log().ifValidationFails().statusCode(201).extract().response();

        given().port(PORT_INVENTORY_IMPORT).header(OKAPI_TENANT)
                .body(newerJobJson.encode())
                .contentType(ContentType.JSON)
                .post("inventory-import/import-jobs")
                .then()
                .log().ifValidationFails().statusCode(201).extract().response();

        RestAssured
                .given()
                .port(PORT_INVENTORY_IMPORT)
                .header(OKAPI_TENANT)
                .contentType(ContentType.JSON)
                .get("inventory-import/import-jobs")
                .then().statusCode(200)
                .body("totalRecords", is(3));

        logger.info(FakeFolioApis.post("/settings/entries",
                new JsonObject()
                        .put("id", UUID.randomUUID().toString())
                        .put("scope", "mod-inventory-import")
                        .put("key", "PURGE_LOGS_AFTER")
                        .put("value", "2 MONTHS")).encodePrettily());


        Response response3 = RestAssured
                .given()
                .baseUri("http://localhost:" + Service.PORT_OKAPI)
                .port(Service.PORT_OKAPI)
                .header(OKAPI_TENANT)
                .contentType(ContentType.JSON)
                .get("settings/entries")
                .then().statusCode(200)
                .body("totalRecords", is(1))
                .extract().response();

        logger.info(response3.asPrettyString());

        final RequestSpecification timeoutConfig = timeoutConfig(10000);

        given()
                .port(Service.PORT_OKAPI)
                .header(OKAPI_TENANT)
                .header(Service.OKAPI_URL)
                .header(Service.OKAPI_TOKEN)
                .contentType(ContentType.JSON)
                .header(XOkapiHeaders.REQUEST_ID, "purge-aged-logs")
                .spec(timeoutConfig)
                .when().post("inventory-import/purge-aged-logs")
                .then().log().ifValidationFails().statusCode(204)
                .extract().response();

        RestAssured
                .given()
                .port(PORT_INVENTORY_IMPORT)
                .header(OKAPI_TENANT)
                .contentType(ContentType.JSON)
                .get("inventory-import/import-jobs")
                .then().statusCode(200)
                .body("totalRecords", is(1));
    }

    @Test
    public void willPurgeAgedJobLogsUsingConfigurationsEntry() {
        Response response = given().port(PORT_INVENTORY_IMPORT)
                .header(OKAPI_TENANT)
                .get("inventory-import/import-jobs")
                .then()
                .log().ifValidationFails().statusCode(200).extract().response();
        logger.info("will purge jobs response: " + response.asPrettyString());

        given().port(Service.PORT_INVENTORY_IMPORT)
                .header(OKAPI_TENANT)
                .get("inventory-import/import-jobs")
                .then()
                .log().ifValidationFails().statusCode(200).extract().response();

        JsonObject transformation = new JsonObject();
        transformation.put("id", TRANSFORMATION_ID)
                .put("name", "testTransformation");
        postJsonObject(PATH_TRANSFORMATIONS, transformation);

        JsonObject importConfig = new JsonObject();
        importConfig
                .put("id", IMPORT_CONFIG_ID)
                .put("name", IMPORT_CONFIG_NAME)
                .put("type", "XML-BULK")
                .put("url", "NA")
                .put("transformationId", TRANSFORMATION_ID);
        postJsonObject(PATH_IMPORT_CONFIGS, importConfig);

        Response response2 = given().port(PORT_INVENTORY_IMPORT)
                .header(OKAPI_TENANT)
                .get("inventory-import/import-jobs")
                .then()
                .log().ifValidationFails().statusCode(200).extract().response();
        logger.info("will purge jobs response: " + response2.asPrettyString());

        given().port(PORT_INVENTORY_IMPORT)
                .header(OKAPI_TENANT)
                .get("inventory-import/import-jobs")
                .then()
                .log().ifValidationFails().statusCode(200).extract().response();

        LocalDateTime now = LocalDateTime.now();
        final LocalDateTime agedJobStartedTime = now.minusMonths(3).minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        final LocalDateTime agedJobFinishedTime = agedJobStartedTime.plusMinutes(2);
        final LocalDateTime intermediateJobStartedTime = now.minusMonths(2).minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        final LocalDateTime intermediateJobFinishedTime = intermediateJobStartedTime.plusMinutes(2);
        final LocalDateTime newerJobStartedTime = now.minusMonths(2).truncatedTo(ChronoUnit.SECONDS);
        final LocalDateTime newerJobFinishedTime = newerJobStartedTime.plusMinutes(3);

        JsonObject agedJobJson =
                new JsonObject(
                        "    {\n" +
                                "      \"id\" : \"" + UUID.randomUUID() + "\",\n" +
                                "      \"importConfigName\" : \"fake job log\",\n" +
                                "      \"importConfigId\" : \"" + IMPORT_CONFIG_ID + "\",\n" +
                                "      \"type\" : \"xmlBulk\",\n" +
                                "      \"url\" : \"http://fileserver/xml/\",\n" +
                                "      \"allowErrors\" : true,\n" +
                                "      \"transformation\" : \"12345\",\n" +
                                "      \"storage\" : \"Batch Upsert Inventory\",\n" +
                                "      \"status\" : \"OK\",\n" +
                                "      \"started\" : \"" + agedJobStartedTime + "\",\n" +
                                "      \"finished\" : \"" + agedJobFinishedTime + "\",\n" +
                                "      \"amountHarvested\" : 5,\n" +
                                "      \"message\" : \"  Instances_processed/loaded/deletions(signals)/failed:__5___5___0(0)___0_ Holdings_records_processed/loaded/deleted/failed:__13___13___0___0_ Items_processed/loaded/deleted/failed:__4___4___0___0_ Source_records_processed/loaded/deleted/failed:__0___0___0___0_\"\n" +
                                "    }\n");

        JsonObject intermediateJobJson =
                new JsonObject(
                        "    {\n" +
                                "      \"id\" : \"" + UUID.randomUUID() + "\",\n" +
                                "      \"importConfigName\" : \"fake job log\",\n" +
                                "      \"importConfigId\" : \"" + IMPORT_CONFIG_ID + "\",\n" +
                                "      \"type\" : \"xmlBulk\",\n" +
                                "      \"url\" : \"http://fileserver/xml/\",\n" +
                                "      \"allowErrors\" : true,\n" +
                                "      \"transformation\" : \"12345\",\n" +
                                "      \"storage\" : \"Batch Upsert Inventory\",\n" +
                                "      \"status\" : \"OK\",\n" +
                                "      \"started\" : \"" + intermediateJobStartedTime + "\",\n" +
                                "      \"finished\" : \"" + intermediateJobFinishedTime + "\",\n" +
                                "      \"amountHarvested\" : 5,\n" +
                                "      \"message\" : \"  Instances_processed/loaded/deletions(signals)/failed:__5___5___0(0)___0_ Holdings_records_processed/loaded/deleted/failed:__13___13___0___0_ Items_processed/loaded/deleted/failed:__4___4___0___0_ Source_records_processed/loaded/deleted/failed:__0___0___0___0_\"\n" +
                                "    }\n");


        JsonObject newerJobJson =
                new JsonObject(
                        "    {\n" +
                                "      \"id\" : \"" + UUID.randomUUID() + "\",\n" +
                                "      \"importConfigName\" : \"fake job log\",\n" +
                                "      \"importConfigId\" : \"" + IMPORT_CONFIG_ID + "\",\n" +
                                "      \"type\" : \"xmlBulk\",\n" +
                                "      \"url\" : \"http://fileserver/xml/\",\n" +
                                "      \"allowErrors\" : true,\n" +
                                "      \"transformation\" : \"12345\",\n" +
                                "      \"storage\" : \"Batch Upsert Inventory\",\n" +
                                "      \"status\" : \"OK\",\n" +
                                "      \"started\" : \"" + newerJobStartedTime + "\",\n" +
                                "      \"finished\" : \"" + newerJobFinishedTime + "\",\n" +
                                "      \"amountHarvested\" : 3,\n" +
                                "      \"message\" : \"  Instances_processed/loaded/deletions(signals)/failed:__3___3___0(0)___0_ Holdings_records_processed/loaded/deleted/failed:__8___8___0___0_ Items_processed/loaded/deleted/failed:__2___2___0___0_ Source_records_processed/loaded/deleted/failed:__0___0___0___0_\"\n" +
                                "    }\n");

        given().port(Service.PORT_INVENTORY_IMPORT).header(OKAPI_TENANT)
                .body(agedJobJson.encode())
                .contentType(ContentType.JSON)
                .post("inventory-import/import-jobs")
                .then()
                .log().ifValidationFails().statusCode(201).extract().response();

        given().port(Service.PORT_INVENTORY_IMPORT).header(OKAPI_TENANT)
                .body(intermediateJobJson.encode())
                .contentType(ContentType.JSON)
                .post("inventory-import/import-jobs")
                .then()
                .log().ifValidationFails().statusCode(201).extract().response();

        given().port(Service.PORT_INVENTORY_IMPORT).header(OKAPI_TENANT)
                .body(newerJobJson.encode())
                .contentType(ContentType.JSON)
                .post("inventory-import/import-jobs")
                .then()
                .log().ifValidationFails().statusCode(201).extract().response();

        RestAssured
                .given()
                .port(Service.PORT_INVENTORY_IMPORT)
                .header(OKAPI_TENANT)
                .contentType(ContentType.JSON)
                .get("inventory-import/import-jobs")
                .then().statusCode(200)
                .body("totalRecords", is(3));

        FakeFolioApis.post("/configurations/entries",
                new JsonObject()
                        .put("module", "mod-harvester-admin")
                        .put("configName", "PURGE_LOGS_AFTER")
                        .put("value", "2 MONTHS"));

        RestAssured
                .given()
                .baseUri("http://localhost:" + Service.PORT_OKAPI)
                .port(Service.PORT_OKAPI)
                .header(OKAPI_TENANT)
                .contentType(ContentType.JSON)
                .get("configurations/entries")
                .then().statusCode(200)
                .body("totalRecords", is(1))
                .extract().response();

        final RequestSpecification timeoutConfig = timeoutConfig(10000);

        given()
                .port(Service.PORT_OKAPI)
                .header(OKAPI_TENANT)
                .header(Service.OKAPI_URL)
                .header(Service.OKAPI_TOKEN)
                .contentType(ContentType.JSON)
                .header(XOkapiHeaders.REQUEST_ID, "purge-aged-logs")
                .spec(timeoutConfig)
                .when().post("inventory-import/purge-aged-logs")
                .then().log().ifValidationFails().statusCode(204)
                .extract().response();

        RestAssured
                .given()
                .port(Service.PORT_INVENTORY_IMPORT)
                .header(OKAPI_TENANT)
                .contentType(ContentType.JSON)
                .get("inventory-import/import-jobs")
                .then().statusCode(200)
                .body("totalRecords", is(1));
    }


    ValidatableResponse postJsonObject(String api, JsonObject body) {
        return given()
                .baseUri(BASE_URI_INVENTORY_IMPORT)
                .header(OKAPI_TENANT)
                .header(OKAPI_URL)
                .body(body.encodePrettily())
                .header(CONTENT_TYPE_JSON)
                .post(api)
                .then()
                .statusCode(201);
    }

    ValidatableResponse putXml (String api, String body) {
        return given()
                .baseUri(BASE_URI_INVENTORY_IMPORT)
                .header(OKAPI_TENANT)
                .header(OKAPI_URL)
                .body(body)
                .header(CONTENT_TYPE_XML)
                .put(api)
                .then()
                .statusCode(204);

    }

    ValidatableResponse postSourceXml(String api, String xmlContent) {
        return given()
                .baseUri(BASE_URI_INVENTORY_IMPORT)
                .header(OKAPI_TENANT)
                .header(OKAPI_URL)
                .body(xmlContent)
                .header(CONTENT_TYPE_XML)
                .post(api)
                .then()
                .statusCode(200);
    }

    ValidatableResponse getRecordById(String api, String id) {
        return given()
                .baseUri(BASE_URI_INVENTORY_IMPORT)
                .header(OKAPI_TENANT)
                .header(OKAPI_URL)
                .get(api + "/" + id)
                .then()
                .statusCode(200);
    }

    ValidatableResponse deleteRecord(String api, String id) {
        return given()
                .baseUri(BASE_URI_INVENTORY_IMPORT)
                .header(OKAPI_TENANT)
                .header(OKAPI_URL)
                .delete(api + "/" + id)
                .then()
                .statusCode(200);
    }

    ValidatableResponse getRecords(String api) {
        return given()
                .baseUri(BASE_URI_INVENTORY_IMPORT)
                .header(OKAPI_TENANT)
                .header(OKAPI_URL)
                .get(api)
                .then()
                .statusCode(200);
    }

    Integer getTotalRecords(String api) {
        return new JsonObject(
                given()
                .baseUri(BASE_URI_INVENTORY_IMPORT)
                .header(OKAPI_TENANT)
                        .header(OKAPI_URL)
                .get(api).asPrettyString()).getInteger("totalRecords");

    }


}
