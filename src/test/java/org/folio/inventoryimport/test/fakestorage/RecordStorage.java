package org.folio.inventoryimport.test.fakestorage;

import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public abstract class RecordStorage {
    public final static String TOTAL_RECORDS = "totalRecords";
    public final String STORAGE_NAME = getClass().getSimpleName();
    public boolean failOnDelete = false;
    public boolean failOnCreate = false;
    public boolean failOnUpdate = false;
    public boolean failOnGetRecordById = false;
    public boolean failOnGetRecords = false;
    final List<ForeignKey> dependentEntities = new ArrayList<>();
    final List<ForeignKey> masterEntities = new ArrayList<>();
    final List<String> mandatoryProperties = new ArrayList<>();
    final List<String> uniqueProperties = new ArrayList<>();

    protected FakeFolioApis fakeStorage;

    protected final Map<String, FolioApiRecord> records = new HashMap<>();
    protected final Logger logger = LoggerFactory.getLogger("fake-folio-storage");

    public void attachToFakeStorage(FakeFolioApis fakeStorage) {
        this.fakeStorage = fakeStorage;
        declareDependencies();
        declareMandatoryProperties();
        declareUniqueProperties();
    }

    // PROPERTY NAME OF THE OBJECT THAT API RESULTS ARE RETURNED IN, IMPLEMENTED PER STORAGE ENTITY
    protected abstract String getResultSetName();

    // INTERNAL DATABASE OPERATIONS - insert() IS DECLARED PUBLIC SO THE TEST SUITE CAN INITIALIZE DATA OUTSIDE THE API.
    public StorageResponse insert (FolioApiRecord FolioApiRecord) {
        Resp validation = validateCreate(FolioApiRecord);

        if (validation.statusCode == 201) {
            FolioApiRecord.setFirstVersion();
            records.put(FolioApiRecord.getId(), FolioApiRecord);
        }
        return new StorageResponse(validation.statusCode, validation.message);
    }

    public static class Resp  {
        public int statusCode;
        public String message;
        public Resp(int status, String message) {
            statusCode = status;
            this.message = message;
        }
    }

    public Resp validateCreate(FolioApiRecord FolioApiRecord) {
        if (failOnCreate) {
            return new Resp(500, "forced fail");
        }
        if (!FolioApiRecord.hasId()) {
            FolioApiRecord.generateId();
        }
        if (records.containsKey(FolioApiRecord.getId())) {
            logger.error("Fake record storage already contains a record with id " + FolioApiRecord.getId() + ", cannot create " + FolioApiRecord.getJson().encodePrettily());
            return new Resp(400, "Record storage already contains a record with id " + FolioApiRecord.getId());
        }
        for (FolioApiRecord existingRecord : records.values()) {
            for (String nameOfUniqueProperty : uniqueProperties) {
                if (FolioApiRecord.getStringValue(nameOfUniqueProperty) != null && existingRecord.getStringValue(nameOfUniqueProperty) != null ) {
                    if (FolioApiRecord.getStringValue(nameOfUniqueProperty).equals(existingRecord.getStringValue(nameOfUniqueProperty))) {
                        return new Resp(400,  this.STORAGE_NAME +" already contains a record with " + nameOfUniqueProperty + " = " + FolioApiRecord.getStringValue(nameOfUniqueProperty));
                    }
                }
            }
        }
        logger.debug("Checking foreign keys");
        logger.debug("Got " + masterEntities.size() + " foreign keys");
        for (ForeignKey fk : masterEntities) {
            if (! FolioApiRecord.getJson().containsKey(fk.getDependentPropertyName())) {
                logger.error("Foreign key violation, record must contain " + fk.getDependentPropertyName());
                return new Resp(422, "{\"errors\":[{\"message\":\"must not be null\",\"type\":\"1\",\"code\":\"-1\",\"parameters\":[{\"key\":\""+fk.getDependentPropertyName()+"\",\"value\":\"null\"}]}]}");
            }
            if (!fk.getMasterStorage().hasId(FolioApiRecord.getJson().getString(fk.getDependentPropertyName()))) {
                logger.error("Foreign key violation " + fk.getDependentPropertyName() + " not found in "+ fk.getMasterStorage().getResultSetName() + ", cannot create " + FolioApiRecord.getJson().encodePrettily());
                logger.error(new JsonObject().encode());
                return new Resp (500, new JsonObject("{ \"message\": \"insert or update on table \\\"storage_table\\\" violates foreign key constraint \\\"fkey\\\"\", \"severity\": \"ERROR\", \"code\": \"23503\", \"detail\": \"Key (property value)=(the id) is not present in table \\\"a_referenced_table\\\".\", \"file\": \"ri_triggers.c\", \"line\": \"3266\", \"routine\": \"ri_ReportViolation\", \"schema\": \"diku_mod_inventory_storage\", \"table\": \"storage_table\", \"constraint\": \"a_fkey\" }").encodePrettily());
            } else {
                logger.debug("Found " + FolioApiRecord.getJson().getString(fk.getDependentPropertyName()) + " in " + fk.getMasterStorage().getResultSetName());
            }
        }
        for (String mandatory : mandatoryProperties) {
            if (!FolioApiRecord.getJson().containsKey(mandatory)) {
                return new Resp(422, new JsonObject("{\"message\" : {\n" + "      \"errors\" : [ {\n" + "        \"message\" : \"must not be null\",\n" + "        \"type\" : \"1\",\n" + "        \"code\" : \"javax.validation.constraints.NotNull.message\",\n" + "        \"parameters\" : [ {\n" + "          \"key\" : \"" + mandatory +"\",\n" + "          \"value\" : \"null\"\n" + "        } ]\n" + "      } ]\n" + "    }}").encodePrettily());
            }
        }
        return new Resp(201,"created");
    }

    protected int update (String id, FolioApiRecord FolioApiRecord) {

        Resp validation = validateUpdate(id, FolioApiRecord);
        if (validation.statusCode == 204) {
            records.put(id, FolioApiRecord);
        }
        return validation.statusCode;
    }

    public Resp validateUpdate (String id, FolioApiRecord FolioApiRecord) {
        if (failOnUpdate) {
            return new Resp(500, "forced fail on update");
        }
        if (FolioApiRecord.hasId() && !id.equals(FolioApiRecord.getId())) {
            return new Resp(400, "Fake record storage received request to update a record at an ID that doesn't match the ID in the record");
        }
        if (! records.containsKey(id)) {
            return new Resp(404,"Record not found, cannot update " + FolioApiRecord.getJson().encodePrettily());
        }
        for (ForeignKey fk : masterEntities) {
            if (! FolioApiRecord.getJson().containsKey(fk.getDependentPropertyName())) {
                return new Resp(422, "Foreign key violation, record must contain " + fk.getDependentPropertyName());
            }
            if (!fk.getMasterStorage().hasId(FolioApiRecord.getJson().getString(fk.getDependentPropertyName()))) {
                return new Resp(500, "Not found: "+ FolioApiRecord.getJson().getString(fk.getDependentPropertyName()) + " in " + fk.getMasterStorage().getResultSetName());
            }
        }
        return new Resp(204,"");
    }

    protected int delete (String id) {
        if (failOnDelete) return 500;
        if (!records.containsKey(id)) {
            logger.error("Record " + id + " not found, cannot delete");
            return 404;
        }
        logger.debug("Dependent entities: " + dependentEntities.size());
        for (ForeignKey fk : dependentEntities) {
            logger.debug("Deleting. Checking dependent " + fk.getDependentStorage().getResultSetName());
            logger.debug("Looking at property " + fk.getDependentPropertyName());
            if (fk.getDependentStorage().hasValue(fk.getDependentPropertyName(), id)) {
                logger.error("Foreign key violation " + records.get(id).getJson().toString() + " has a dependent record in " + fk.getDependentStorage().getResultSetName());
                return 400;
            }
        }
        records.remove(id);
        return 200;
    }

    protected Collection<FolioApiRecord> getRecords () {
        return records.values();
    }

    private FolioApiRecord getRecord (String id) {
        if (failOnGetRecordById) {
            return null;
        } else {
            FolioApiRecord folioApiRecord = records.get( id );
            if (folioApiRecord != null) {
                folioApiRecord.setVersion(folioApiRecord.getVersion() + 1);
            }
            return folioApiRecord;
        }
    }

    // FOREIGN KEY HANDLING
    protected boolean hasId (String id) {
        return records.containsKey(id);
    }

    /**
     * Checks if this storage has a record where this property (presumably a foreign key property) has this value
     * @param fkPropertyName name of the referenced property
     * @param value value of the referenced property
     * @return true if value exists
     */
    protected boolean hasValue (String fkPropertyName, String value) {
        for (FolioApiRecord FolioApiRecord : records.values()) {
            logger.debug("Checking " + FolioApiRecord.getJson().toString() + " for value " + value);
            if (FolioApiRecord.getJson().containsKey(fkPropertyName) && FolioApiRecord.getJson().getString(fkPropertyName).equals(value)) {
                return true;
            }
        }
        return false;
    }

    // USED BY A DEPENDENT ENTITY TO SET UP ITS FOREIGN KEYS BY CALLS to acceptDependant()
    protected abstract void declareDependencies();

    // METHOD ON THE PRIMARY KEY ENTITY TO REGISTER DEPENDENT ENTITIES
    protected void acceptDependant(RecordStorage dependentEntity, String dependentPropertyName) {
        ForeignKey fk = new ForeignKey(dependentEntity, dependentPropertyName, this);
        dependentEntities.add(fk);
        dependentEntity.setMasterEntity(fk);
    }

    protected void setMasterEntity (ForeignKey fk) {
        masterEntities.add(fk);
    }

    protected abstract void declareMandatoryProperties ();

    protected void declareUniqueProperties () {}
    // API REQUEST HANDLERS

    /**
     * Handles GET request with query parameter
     *
     */
    public void getRecords(RoutingContext routingContext) {
        final String optionalQuery = routingContext.request().getParam("query") != null ?
                decode(routingContext.request().getParam("query")) : null;
        JsonObject responseJson = buildJsonRecordsResponse(optionalQuery);
        if (responseJson != null) {
            respond(routingContext, buildJsonRecordsResponse(optionalQuery), 200);
        } else {
            respondWithMessage(routingContext, (failOnGetRecords ? "Forced " : "") + " Error on getting records", 500);
        }
    }

    /**
     * Handles GET by ID
     */
    protected void getRecordById(RoutingContext routingContext) {
        final String id = routingContext.pathParam("id");
        FolioApiRecord FolioApiRecord = getRecord(id);

        if (FolioApiRecord != null) {
            respond(routingContext, FolioApiRecord.getJson(), 200);
        } else {
            respondWithMessage(routingContext, (failOnGetRecordById ? "Forced error on get from " : "No record with ID " + id + " in ") + STORAGE_NAME, 404);
        }
    }

    /**
     * Handles DELETE
     */
    protected void deleteRecord (RoutingContext routingContext) {
        final String id = routingContext.pathParam("id");
        int code = delete(id);

        if (code == 200) {
            respond(routingContext, new JsonObject(), code);
        } else {
            respondWithMessage(routingContext, (failOnDelete ? "Forced " : "") + "Error deleting from " + STORAGE_NAME, code);
        }
    }

    /**
     * Handles DELETE ALL
     */
    protected void deleteAll (RoutingContext routingContext) {
        records.clear();
        respond(routingContext, new JsonObject("{\"message\": \"all records deleted\"}"), 200);
    }

    public void wipeMockRecords() {
        records.clear();
    }

    /**
     * Handles POST
     *
     */
    protected void createRecord(RoutingContext routingContext) {
        JsonObject recordJson = new JsonObject(routingContext.body().asString());
        StorageResponse response = insert(new FolioApiRecord(recordJson));
        if (response.statusCode == 201) {
            respond(routingContext, recordJson, response.statusCode);
        } else {
            respondWithMessage(routingContext, response.responseBody, response.statusCode);
        }
    }


    /**
     * Handles PUT
     *
     */
    protected void updateRecord(RoutingContext routingContext) {
        JsonObject recordJson = new JsonObject(routingContext.body().asString());
        String id = routingContext.pathParam("id");
        int code = update(id, new FolioApiRecord(recordJson));
        if (code == 204) {
            respond(routingContext, code);
        } else {
            respondWithMessage(routingContext, (failOnUpdate ? "Forced " : "") + "Error updating record in " + STORAGE_NAME, code);
        }
    }

    // HELPERS FOR RESPONSE PROCESSING

    JsonObject buildJsonRecordsResponse(String optionalQuery) {
        if (failOnGetRecords) return null;
        JsonObject response = new JsonObject();
        JsonArray jsonRecords = new JsonArray();
        getRecords().forEach( FolioApiRecord -> {
            if (optionalQuery == null || FolioApiRecord.match(optionalQuery)) {
                jsonRecords.add(FolioApiRecord.getJson());
            }});
        response.put(getResultSetName(), jsonRecords);
        response.put(TOTAL_RECORDS, jsonRecords.size());
        return response;
    }

    /**
     * Respond with JSON and status code
     *
     * @param responseJson the response
     * @param code the status code
     */
    protected static void respond(RoutingContext routingContext, JsonObject responseJson, int code) {
        routingContext.response().headers().add("Content-Type", "application/json");
        routingContext.response().setStatusCode(code);
        routingContext.response().end(responseJson.encodePrettily());
    }

    /**
     * Respond with status code
     *
     * @param code the status code
     */
    protected static void respond(RoutingContext routingContext, int code) {
        routingContext.response().headers().add("Content-Type", "application/json");
        routingContext.response().setStatusCode(code);
        routingContext.response().end();
    }

    /**
     * Respond with text message (error response)
     *
     * @param res error condition
     */
    protected static void respondWithMessage(RoutingContext routingContext, Throwable res) {
        routingContext.response().setStatusCode(500);
        routingContext.response().end(res.getMessage());
    }

    protected static void respondWithMessage (RoutingContext routingContext, String message, int code) {
        routingContext.response().setStatusCode(code);
        routingContext.response().end(message);

    }


    // UTILS

    public static String decode (String string) {
        return URLDecoder.decode(string, StandardCharsets.UTF_8);
    }

    public static class ForeignKey {

        private final RecordStorage dependentStorage;
        private final String dependentPropertyName;
        private final RecordStorage masterStorage;

        public ForeignKey (RecordStorage dependentStorage, String dependentPropertyName, RecordStorage masterStorage) {
            this.dependentStorage = dependentStorage;
            this.dependentPropertyName = dependentPropertyName;
            this.masterStorage = masterStorage;
        }

        public RecordStorage getDependentStorage() {
            return dependentStorage;
        }

        public String getDependentPropertyName() {
            return dependentPropertyName;
        }

        public RecordStorage getMasterStorage() {
            return masterStorage;
        }

    }
}
