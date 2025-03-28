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
import org.folio.inventoryimport.test.sampleData.Samples;
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
import static org.folio.inventoryimport.test.Statics.*;
import static org.folio.inventoryimport.test.sampleData.Samples.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@RunWith(VertxUnitRunner.class)
public class TestSuite {
    private static final Logger logger = LoggerFactory.getLogger(TestSuite.class);

    static Vertx vertx;

    private static FakeFolioApis fakeFolioApis;
    static final String TENANT = "import_test";
    public static final Header OKAPI_TENANT = new Header(XOkapiHeaders.TENANT, TENANT);
    public static final Header OKAPI_URL = new Header(XOkapiHeaders.URL, BASE_URI_OKAPI);
    public static final Header OKAPI_TOKEN = new Header(XOkapiHeaders.TOKEN, "eyJhbGciOiJIUzUxMiJ9eyJzdWIiOiJhZG1pbiIsInVzZXJfaWQiOiI3OWZmMmE4Yi1kOWMzLTViMzktYWQ0YS0wYTg0MDI1YWIwODUiLCJ0ZW5hbnQiOiJ0ZXN0X3RlbmFudCJ9BShwfHcNClt5ZXJ8ImQTMQtAM1sQEnhsfWNmXGsYVDpuaDN3RVQ9");
    public static final String TRANSFORMATION_ID = "caf7dbe7-0cfc-4343-b1db-04671ac3e40a";
    public static final String IMPORT_CONFIG_ID = "9ac8c174-fdd3-4380-a2f9-135b33df9795";
    public static final String IMPORT_CONFIG_NAME = "test config";
    public static final String IMPORT_JOB_ID = "cda450e1-46bf-4bb9-9741-876ec395d5e9";
    public static final String STEP_ID = "66d5ef34-ee3d-434c-a07d-80dbfdb31b6e";
    public static final String PATH_TRANSFORMATIONS = "inventory-import/transformations";
    public static final String PATH_STEPS = "inventory-import/steps";
    public static final String PATH_TSAS = "inventory-import/tsas";
    public static final String PATH_IMPORT_CONFIGS = "inventory-import/import-configs";
    public static final String PATH_IMPORT_JOBS = "inventory-import/import-jobs";
    public static final String BASE_PATH_IMPORT_XML_FILE = "inventory-import/import-configs/xml-bulk";

    @ClassRule
    public static PostgreSQLContainer<?> postgresSQLContainer = TenantPgPoolContainer.create();


    @Rule
    public final TestName name = new TestName();

    @BeforeClass
    public static void beforeClass(TestContext context) {
        vertx = Vertx.vertx();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        RestAssured.baseURI = Statics.BASE_URI_INVENTORY_IMPORT;
        RestAssured.requestSpecification = new RequestSpecBuilder().build();

        DeploymentOptions deploymentOptions = new DeploymentOptions();
        deploymentOptions.setConfig(new JsonObject().put("port", Integer.toString(Statics.PORT_INVENTORY_IMPORT)));
        vertx.deployVerticle(new MainVerticle(), deploymentOptions)
                .onComplete(context.asyncAssertSuccess(x ->
                        fakeFolioApis = new FakeFolioApis(vertx, context)));

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
        vertx.fileSystem().deleteRecursive(FileQueue.SOURCE_FILES_ROOT_DIR,true);
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
        JsonObject transformation = new JsonObject();
        transformation.put("id", TRANSFORMATION_ID)
               .put("name", "testTransformation");

        postJsonObject(PATH_TRANSFORMATIONS, transformation);
        getRecordById(PATH_TRANSFORMATIONS, TRANSFORMATION_ID);
    }

    @Test
    public void canPostAndGetStepAndGetXslt() {
        JsonObject step = new JsonObject();
        step.put("id", STEP_ID)
                .put("name", "test step")
                .put("enabled", true)
                .put("script", COPY_XML_DOC_XSLT);

        postJsonObject(PATH_STEPS, step);
        getRecordById(PATH_STEPS, STEP_ID).extract().response().getBody().prettyPrint();
        await().until(() -> getRecords(PATH_STEPS + "/" + STEP_ID + "/script").extract().asPrettyString(), equalTo(COPY_XML_DOC_XSLT));
    }

    @Test
    public void cannotPostStepWithInvalidXslt() {
        JsonObject step = new JsonObject();
        step.put("id", STEP_ID)
                .put("name", "test step")
                .put("enabled", true)
                .put("script", INVALID_XSLT);

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
                .put("script", EMPTY_XSLT);
        postJsonObject(PATH_STEPS, step);
        getRecordById(PATH_STEPS, STEP_ID).extract().response().getBody().prettyPrint();
        await().until(() -> getRecords(PATH_STEPS + "/" + STEP_ID + "/script").extract().asPrettyString(), equalTo(EMPTY_XSLT));
        putXml(PATH_STEPS + "/" + STEP_ID + "/script", COPY_XML_DOC_XSLT);
        await().until(() -> getRecords(PATH_STEPS + "/" + STEP_ID + "/script").extract().asPrettyString(), equalTo(COPY_XML_DOC_XSLT));
    }

    @Test
    public void cannotUpdateStepWithInvalidXslt() {
        JsonObject step = new JsonObject();
        step.put("id", STEP_ID)
                .put("name", "test step")
                .put("enabled", true)
                .put("script", EMPTY_XSLT);

        given()
                .baseUri(BASE_URI_INVENTORY_IMPORT)
                .header(OKAPI_TENANT)
                .header(OKAPI_URL)
                .body(INVALID_XSLT)
                .header(CONTENT_TYPE_XML)
                .put(PATH_STEPS + "/" + STEP_ID + "/script")
                .then()
                .statusCode(400);

    }

    @Test
    public void canInsertStepIntoPipeline () {
        JsonObject step = new JsonObject();
        step.put("id", STEP_ID)
                .put("name", "test step")
                .put("enabled", true)
                .put("script", COPY_XML_DOC_XSLT);
        postJsonObject(PATH_STEPS, step);

        JsonObject transformation = new JsonObject();
        transformation.put("id", TRANSFORMATION_ID)
                .put("name", "testTransformation");
        postJsonObject(PATH_TRANSFORMATIONS, transformation);

        JsonObject tsa = new JsonObject();
        tsa.put("stepId", STEP_ID)
                .put("transformationId", TRANSFORMATION_ID)
                .put("position", "1");
        postJsonObject(PATH_TSAS, tsa);

        getRecords(PATH_TSAS+"?query=transformationId="+TRANSFORMATION_ID).body("totalRecords" , is(1));

    }

    @Test
    public void canPostAndGetImportConfig() {
        JsonObject transformation = new JsonObject();
        transformation.put("id", TRANSFORMATION_ID)
                .put("name", "testTransformation");
        postJsonObject(PATH_TRANSFORMATIONS, transformation);

        JsonObject importConfig = new JsonObject();
        importConfig
                .put("name", IMPORT_CONFIG_NAME)
                .put("type", "XML-BULK")
                .put("url", "NA")
                .put("transformationId", TRANSFORMATION_ID);

        postJsonObject(PATH_IMPORT_CONFIGS, importConfig);
        getRecords(PATH_IMPORT_CONFIGS)
                .body("totalRecords", is(1));
    }

    @Test
    public void canPostAndGetImportJob() {
        JsonObject transformation = new JsonObject();
        transformation.put("id", TRANSFORMATION_ID)
                .put("name", "testTransformation");
        postJsonObject(PATH_TRANSFORMATIONS, transformation);

        JsonObject importConfig = new JsonObject();
        importConfig
                .put("id", IMPORT_CONFIG_ID)
                .put("name", "test config")
                .put("type", "XML-BULK")
                .put("url", "NA")
                .put("transformationId", TRANSFORMATION_ID);

        postJsonObject(PATH_IMPORT_CONFIGS, importConfig);

        JsonObject importJob = new JsonObject();
        importJob
                .put("id", IMPORT_JOB_ID)
                .put("importConfigName", IMPORT_CONFIG_NAME)
                .put("importConfigId", IMPORT_CONFIG_ID)
                .put("started", "2024-01-01T01:01:01")
                .put("amountHarvested", 0);
        postJsonObject(PATH_IMPORT_JOBS, importJob);
        getRecords(PATH_IMPORT_JOBS)
                .body("totalRecords", is(1));

    }

    @Test
    public void canPostLogLines() {
        JsonObject transformation = new JsonObject();
        transformation.put("id", TRANSFORMATION_ID)
                .put("name", "testTransformation");
        postJsonObject(PATH_TRANSFORMATIONS, transformation);

        JsonObject importConfig = new JsonObject();
        importConfig
                .put("id", IMPORT_CONFIG_ID)
                .put("name", "test config")
                .put("type", "XML-BULK")
                .put("url", "NA")
                .put("transformationId", TRANSFORMATION_ID);

        postJsonObject(PATH_IMPORT_CONFIGS, importConfig);

        JsonObject importJob = new JsonObject();
        importJob
                .put("id", IMPORT_JOB_ID)
                .put("importConfigName", IMPORT_CONFIG_NAME)
                .put("importConfigId", IMPORT_CONFIG_ID)
                .put("started", "2024-01-01T01:01:01")
                .put("amountHarvested", 0);
        postJsonObject(PATH_IMPORT_JOBS, importJob);

        JsonArray logLines = new JsonArray();
        logLines.add(new JsonObject()
                .put("importJobId", IMPORT_JOB_ID)
                .put("timeStamp", SettableClock.getLocalDateTime().toString())
                .put("jobLabel", IMPORT_CONFIG_NAME)
                .put("line", "log line 1"));
        logLines.add(new JsonObject()
                .put("importJobId", IMPORT_JOB_ID)
                .put("timeStamp", SettableClock.getLocalDateTime().toString())
                .put("jobLabel", IMPORT_CONFIG_NAME)
                .put("line", "log line 2"));
        JsonObject request = new JsonObject().put("logLines", logLines);
        postJsonObject(PATH_IMPORT_JOBS+"/"+IMPORT_JOB_ID+"/log", request);

        getRecords(PATH_IMPORT_JOBS + "/" + IMPORT_JOB_ID + "/log")
                .body("totalRecords", is(2));
    }

    @Test
    public void canImportTransformedXml() {
        JsonObject transformation = new JsonObject();
        transformation.put("id", TRANSFORMATION_ID)
                .put("name", "testTransformation");
        postJsonObject(PATH_TRANSFORMATIONS, transformation);

        JsonObject step = new JsonObject();
        step.put("id", STEP_ID)
                .put("name", "test step")
                .put("enabled", true)
                .put("script", COPY_XML_DOC_XSLT);
        postJsonObject(PATH_STEPS, step);

        JsonObject tsa = new JsonObject();
        tsa.put("stepId", STEP_ID)
                .put("transformationId", TRANSFORMATION_ID)
                .put("position", "1");
        postJsonObject(PATH_TSAS, tsa);

        JsonObject importConfig = new JsonObject();
        importConfig
                .put("id", IMPORT_CONFIG_ID)
                .put("name", IMPORT_CONFIG_NAME)
                .put("type", "XML-BULK")
                .put("url", "NA")
                .put("transformationId", TRANSFORMATION_ID);
        postJsonObject(PATH_IMPORT_CONFIGS, importConfig);

        getRecordById(PATH_IMPORT_CONFIGS, IMPORT_CONFIG_ID);
        getRecordById(PATH_TRANSFORMATIONS, TRANSFORMATION_ID);
        postSourceXml(BASE_PATH_IMPORT_XML_FILE + "/" + IMPORT_CONFIG_ID + "/import", Samples.INVENTORY_RECORD_SET_XML);
        await().until(() ->  getTotalRecords(PATH_IMPORT_JOBS), is(1));
        String jobId = getRecords(PATH_IMPORT_JOBS).extract().path("importJobs[0].id");
        String started = getRecordById(PATH_IMPORT_JOBS, jobId).extract().path("started");
        await().until(() -> getRecordById(PATH_IMPORT_JOBS, jobId).extract().path("finished"), greaterThan(started));
        await().until(() ->  getTotalRecords(PATH_IMPORT_JOBS + "/" + IMPORT_JOB_ID + "/log"), is(4));
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
                .port(Statics.PORT_OKAPI)
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
                .baseUri("http://localhost:" + Statics.PORT_OKAPI)
                .port(Statics.PORT_OKAPI)
                .header(OKAPI_TENANT)
                .contentType(ContentType.JSON)
                .get("settings/entries")
                .then().statusCode(200)
                .body("totalRecords", is(1))
                .extract().response();

        logger.info(response3.asPrettyString());

        final RequestSpecification timeoutConfig = timeoutConfig(10000);

        given()
                .port(Statics.PORT_OKAPI)
                .header(OKAPI_TENANT)
                .header(Statics.OKAPI_URL)
                .header(Statics.OKAPI_TOKEN)
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

        given().port(Statics.PORT_INVENTORY_IMPORT)
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

        given().port(Statics.PORT_INVENTORY_IMPORT).header(OKAPI_TENANT)
                .body(agedJobJson.encode())
                .contentType(ContentType.JSON)
                .post("inventory-import/import-jobs")
                .then()
                .log().ifValidationFails().statusCode(201).extract().response();

        given().port(Statics.PORT_INVENTORY_IMPORT).header(OKAPI_TENANT)
                .body(intermediateJobJson.encode())
                .contentType(ContentType.JSON)
                .post("inventory-import/import-jobs")
                .then()
                .log().ifValidationFails().statusCode(201).extract().response();

        given().port(Statics.PORT_INVENTORY_IMPORT).header(OKAPI_TENANT)
                .body(newerJobJson.encode())
                .contentType(ContentType.JSON)
                .post("inventory-import/import-jobs")
                .then()
                .log().ifValidationFails().statusCode(201).extract().response();

        RestAssured
                .given()
                .port(Statics.PORT_INVENTORY_IMPORT)
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
                .baseUri("http://localhost:" + Statics.PORT_OKAPI)
                .port(Statics.PORT_OKAPI)
                .header(OKAPI_TENANT)
                .contentType(ContentType.JSON)
                .get("configurations/entries")
                .then().statusCode(200)
                .body("totalRecords", is(1))
                .extract().response();

        final RequestSpecification timeoutConfig = timeoutConfig(10000);

        given()
                .port(Statics.PORT_OKAPI)
                .header(OKAPI_TENANT)
                .header(Statics.OKAPI_URL)
                .header(Statics.OKAPI_TOKEN)
                .contentType(ContentType.JSON)
                .header(XOkapiHeaders.REQUEST_ID, "purge-aged-logs")
                .spec(timeoutConfig)
                .when().post("inventory-import/purge-aged-logs")
                .then().log().ifValidationFails().statusCode(204)
                .extract().response();

        RestAssured
                .given()
                .port(Statics.PORT_INVENTORY_IMPORT)
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
                .header(Statics.CONTENT_TYPE_XML)
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
