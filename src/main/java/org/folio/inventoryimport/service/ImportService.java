package org.folio.inventoryimport.service;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.openapi.RouterBuilder;
import io.vertx.ext.web.validation.RequestParameter;
import io.vertx.ext.web.validation.RequestParameters;
import io.vertx.ext.web.validation.ValidationHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.inventoryimport.foliodata.ConfigurationsClient;
import org.folio.inventoryimport.foliodata.SettingsClient;
import org.folio.inventoryimport.moduledata.*;
import org.folio.inventoryimport.moduledata.database.ModuleStorageAccess;
import org.folio.inventoryimport.moduledata.database.SqlQuery;
import org.folio.inventoryimport.moduledata.database.Tables;
import org.folio.inventoryimport.service.fileimport.FileProcessor;
import org.folio.inventoryimport.service.fileimport.FileQueue;
import org.folio.inventoryimport.service.fileimport.FileListeners;
import org.folio.inventoryimport.service.fileimport.XmlFileListener;
import org.folio.inventoryimport.utils.Miscellaneous;
import org.folio.inventoryimport.utils.SettableClock;
import org.folio.tlib.RouterCreator;
import org.folio.tlib.TenantInitHooks;
import org.folio.tlib.postgres.PgCqlException;
import org.folio.tlib.util.TenantUtil;

import java.time.LocalDateTime;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;

import static org.folio.okapi.common.HttpResponse.*;

/**
 * Main service.
 */
public class ImportService implements RouterCreator, TenantInitHooks {

    public static final Logger logger = LogManager.getLogger("inventory-import");

    @Override
    public Future<Router> createRouter(Vertx vertx) {
        return RouterBuilder.create(vertx, "openapi/inventory-import-1.0.yaml").map(routerBuilder -> {
            routerBuilder.rootHandler(BodyHandler.create().setBodyLimit(30000000));
            handlers(vertx, routerBuilder);
            return routerBuilder.createRouter();
        });
    }

    private void handlers(Vertx vertx, RouterBuilder routerBuilder) {
        // Configurations
        handler(vertx, routerBuilder, "postImportConfig", this::postImportConfig);
        handler(vertx, routerBuilder, "getImportConfigs", this::getImportConfigs);
        handler(vertx, routerBuilder, "getImportConfig", this::getImportConfigById);
        handler(vertx, routerBuilder, "putImportConfig", this::putImportConfig);
        handler(vertx, routerBuilder, "deleteImportConfig", this::deleteImportConfig);
        handler(vertx, routerBuilder, "postTransformation", this::postTransformation);
        handler(vertx, routerBuilder, "getTransformation", this::getTransformationById);
        handler(vertx, routerBuilder, "getTransformations", this::getTransformations);
        handler(vertx, routerBuilder, "putTransformation", this::putTransformation);
        handler(vertx, routerBuilder, "deleteTransformation", this::deleteTransformation);
        handler(vertx, routerBuilder, "postStep", this::postStep);
        handler(vertx, routerBuilder, "getSteps", this::getSteps);
        handler(vertx, routerBuilder, "getStep", this::getStepById);
        handler(vertx, routerBuilder, "putStep", this::putStep);
        handler(vertx, routerBuilder, "deleteStep", this::deleteStep);
        handler(vertx, routerBuilder, "getScript", this::getScript);
        handler(vertx, routerBuilder, "putScript", this::putScript);
        handler(vertx, routerBuilder, "postTsa", this::postTransformationStep);
        handler(vertx, routerBuilder, "getTsas", this::getTransformationSteps);
        handler(vertx, routerBuilder, "getTsa", this::getTransformationStepById);
        handler(vertx, routerBuilder, "putTsa", this::putTransformationStep);
        handler(vertx, routerBuilder, "deleteTsa", this::deleteTransformationStep);

        // Jobs
        handler(vertx, routerBuilder, "getImportJobs", this::getImportJobs);
        handler(vertx, routerBuilder, "getImportJob", this::getImportJobById);
        handler(vertx, routerBuilder, "postImportJob", this::postImportJob);
        handler(vertx, routerBuilder, "deleteImportJob", this::deleteImportJob);
        handler(vertx, routerBuilder, "postImportJobLogLines", this::postLogStatements);
        handler(vertx, routerBuilder, "getImportJobLogLines", this::getLogStatements);
        handler(vertx, routerBuilder, "getFailedRecordsForJob", this::getFailedRecords);
        handler(vertx, routerBuilder, "postFailedRecordsForJob", this::postFailedRecordsForJob);
        handler(vertx, routerBuilder, "deleteRecordFailure", this::deleteRecordFailure);
        // Processing
        handler(vertx, routerBuilder, "purgeAgedLogs", this::purgeAgedLogs);
        handler(vertx, routerBuilder, "importXmlRecords", this::stageXmlSourceFile);
        handler(vertx, routerBuilder, "startFileListener", this::activateFileListener);
        handler(vertx, routerBuilder, "pauseImport", this::pauseImportJob);
        handler(vertx, routerBuilder, "resumeImport", this::resumeImportJob);
    }

    private void exceptionResponse(Throwable cause, RoutingContext routingContext) {
        if (cause.getMessage().toLowerCase().contains("could not find")) {
            responseError(routingContext, 404, cause.getMessage());
        } else {
            responseError(routingContext, 400, cause.getClass().getSimpleName() + ": " + cause.getMessage());
        }
    }

    /**
     * Returns request validation exception, potentially with improved error message if problem was
     * an error in a polymorph schema, like in `harvestable` of type `oaiPmh` vs `xmlBulk`.
     */
    private void routerExceptionResponse(RoutingContext ctx) {
        String message = null;
        if (ctx.failure() != null) message = ctx.failure().getMessage();
        responseError(ctx, ctx.statusCode(), message);
    }

    @Override
    public Future<Void> postInit(Vertx vertx, String tenant, JsonObject tenantAttributes) {
        return new ModuleStorageAccess(vertx, tenant).init(tenantAttributes)
                .onFailure(x -> logger.error("Database initialization failed: " + x.getMessage()))
                .onSuccess(x -> logger.info("Tenant '" + tenant + "' database initialized"));
    }

    private Future<Void> getEntities(Vertx vertx, RoutingContext routingContext, Entity entity) {
        String tenant = TenantUtil.tenant(routingContext);
        ModuleStorageAccess moduleStorage = new ModuleStorageAccess(vertx, tenant);
        SqlQuery query;
        try {
            query = entity
                    .makeSqlFromCqlQuery(routingContext, moduleStorage.schemaDotTable(entity.table()));
        } catch (PgCqlException pce) {
            responseText(routingContext, 400)
                    .end("Could not execute query to retrieve " + entity.jsonCollectionName() + ": " + pce.getMessage() + " Request:" + routingContext.request().absoluteURI());
            return Future.succeededFuture();
        } catch (Exception e) {
            return Future.failedFuture(e.getMessage());
        }
        return moduleStorage.getEntities(query.getQueryWithLimits(), entity).onComplete(
                result -> {
                    if (result.succeeded()) {
                        JsonObject responseJson = new JsonObject();
                        JsonArray jsonRecords = new JsonArray();
                        responseJson.put(entity.jsonCollectionName(), jsonRecords);
                        List<Entity> recs = result.result();
                        for (Entity rec : recs) {
                            jsonRecords.add(rec.asJson());
                        }
                        moduleStorage.getCount(query.getCountingSql()).onComplete(
                                count -> {
                                    responseJson.put("totalRecords", count.result());
                                    responseJson(routingContext, 200).end(responseJson.encodePrettily());
                                }
                        );
                    } else {
                        responseText(routingContext, 500)
                                .end("Problem retrieving jobs: " + result.cause().getMessage());
                    }
                }
        ).mapEmpty();
    }

    private Future<Void> getEntity(Vertx vertx, RoutingContext routingContext, Entity entity) {
        String tenant = TenantUtil.tenant(routingContext);
        RequestParameters params = routingContext.get(ValidationHandler.REQUEST_CONTEXT_KEY);
        UUID id = UUID.fromString(params.pathParameter("id").getString());
        return new ModuleStorageAccess(vertx, tenant).getEntity(id, entity)
                .onSuccess(instance -> {
                    if (instance == null) {
                        responseText(routingContext, 404).end(entity.entityName() + " " + id + " not found.");
                    } else {
                        responseJson(routingContext, 200).end(instance.asJson().encodePrettily());
                    }
                })
                .mapEmpty();
    }

    private Future<Void> deleteEntity(Vertx vertx, RoutingContext routingContext, Entity entity) {
        String tenant = TenantUtil.tenant(routingContext);
        RequestParameters params = routingContext.get(ValidationHandler.REQUEST_CONTEXT_KEY);
        UUID id = UUID.fromString(params.pathParameter("id").getString());
        return new ModuleStorageAccess(vertx, tenant).deleteEntity(id, entity).
                onSuccess(result -> {
                    if (result == 0) {
                        responseText(routingContext, 404).end("Not found");
                    } else {
                        responseText(routingContext, 200).end();
                    }
                }).mapEmpty();
    }

    private Future<Void> storeEntityRespondWith201(Vertx vertx, RoutingContext routingContext, Entity entity) {
        String tenant = TenantUtil.tenant(routingContext);
        ModuleStorageAccess db = new ModuleStorageAccess(vertx,tenant);
        return db.storeEntity(entity)
                .onSuccess(
                        id -> db.getEntity(id, entity)
                                .map(stored -> responseJson(routingContext, 201)
                                        .end(stored.asJson().encodePrettily())))
                .mapEmpty();
    }

    private Future<Void> postImportConfig(Vertx vertx, RoutingContext routingContext) {
        ImportConfig importConfig = new ImportConfig().fromJson(routingContext.body().asJsonObject());
        return storeEntityRespondWith201(vertx, routingContext, importConfig);
    }

    private Future<Void> getImportConfigs(Vertx vertx, RoutingContext routingContext) {
        return getEntities(vertx, routingContext, new ImportConfig());
    }

    private Future<Void> getImportConfigById(Vertx vertx, RoutingContext routingContext) {
        return getEntity(vertx, routingContext, new ImportConfig());
    }

    private Future<Void> putImportConfig(Vertx vertx, RoutingContext routingContext) {
        String tenant = TenantUtil.tenant(routingContext);
        ImportConfig importConfig = new ImportConfig().fromJson(routingContext.body().asJsonObject());
        RequestParameters params = routingContext.get(ValidationHandler.REQUEST_CONTEXT_KEY);
        UUID id = UUID.fromString(params.pathParameter("id").getString());
        ModuleStorageAccess db = new ModuleStorageAccess(vertx,tenant);
        return db.updateEntity(id,importConfig)
                .onSuccess(result-> {
                    if (result.rowCount()==1) {
                        responseText(routingContext, 204).end();
                    } else {
                        responseText(routingContext, 404).end("Not found");
                    }
                }).mapEmpty();
    }

    private Future<Void> deleteImportConfig(Vertx vertx, RoutingContext routingContext) {
        return deleteEntity(vertx, routingContext, new ImportConfig());
    }

    private Future<Void> postImportJob(Vertx vertx, RoutingContext routingContext) {
        ImportJob importJob = new ImportJob().fromJson(routingContext.body().asJsonObject());
        return storeEntityRespondWith201(vertx, routingContext, importJob);
    }

    private Future<Void> getImportJobs(Vertx vertx, RoutingContext routingContext) {
        String tenant = TenantUtil.tenant(routingContext);
        ModuleStorageAccess moduleStorage = new ModuleStorageAccess(vertx, tenant);

        String fromDateTime = routingContext.request().getParam("from");
        String untilDateTime = routingContext.request().getParam("until");
        String timeRange = null;
        if (fromDateTime != null && untilDateTime != null) {
            timeRange = " (finished >= '" + fromDateTime + "'  AND finished <= '" + untilDateTime + "') ";
        } else if (fromDateTime != null) {
            timeRange = " finished >= '" + fromDateTime + "' ";
        } else if (untilDateTime != null) {
            timeRange = " finished <= '" + untilDateTime + "' ";
        }

        SqlQuery query;
        try {
            query = new ImportJob()
                    .makeSqlFromCqlQuery(routingContext, moduleStorage.schemaDotTable(Tables.import_job))
                    .withAdditionalWhereClause(timeRange);
        } catch (PgCqlException pce) {
            responseText(routingContext, 400)
                    .end("Could not execute query to retrieve jobs: " + pce.getMessage() + " Request:" + routingContext.request().absoluteURI());
            return Future.succeededFuture();
        } catch (Exception e) {
            return Future.failedFuture(e.getMessage());
        }
        return moduleStorage.getEntities(query.getQueryWithLimits(), new ImportJob()).onComplete(
                jobsList -> {
                    if (jobsList.succeeded()) {
                        JsonObject responseJson = new JsonObject();
                        JsonArray importJobs = new JsonArray();
                        responseJson.put("importJobs", importJobs);
                        List<Entity> jobs = jobsList.result();
                        for (Entity job : jobs) {
                            importJobs.add(job.asJson());
                        }
                        moduleStorage.getCount(query.getCountingSql()).onComplete(
                                count -> {
                                    responseJson.put("totalRecords", count.result());
                                    responseJson(routingContext, 200).end(responseJson.encodePrettily());
                                }
                        );
                    } else {
                        responseText(routingContext, 500)
                                .end("Problem retrieving jobs: " + jobsList.cause().getMessage());
                    }
                }
        ).mapEmpty();
    }

    private Future<Void> getImportJobById(Vertx vertx, RoutingContext routingContext) {
        return getEntity(vertx, routingContext, new ImportJob());
    }

    private Future<Void> deleteImportJob(Vertx vertx, RoutingContext routingContext) {
        return deleteEntity(vertx, routingContext, new ImportJob());
    }

    private Future<Void> postLogStatements(Vertx vertx, RoutingContext routingContext) {
        String tenant = TenantUtil.tenant(routingContext);
        JsonObject body = routingContext.body().asJsonObject();
        JsonArray lines = body.getJsonArray("logLines");
        List<Entity> logLines = new ArrayList<>();
        for (Object o : lines) {
            logLines.add(new LogLine().fromJson((JsonObject) o));
        }
        return new ModuleStorageAccess(vertx, tenant)
                .storeEntities(new LogLine(), logLines)
                .onSuccess(configId ->
                        responseJson(routingContext, 201).end(logLines.size() + " log line(s) created."))
                .mapEmpty();
    }

    private Future<Void> getLogStatements(Vertx vertx, RoutingContext routingContext) {
        return getEntities(vertx, routingContext, new LogLine());
    }

    private Future<Void> getFailedRecords(Vertx vertx, RoutingContext routingContext) {
        String tenant = TenantUtil.tenant(routingContext);
        ModuleStorageAccess moduleStorage = new ModuleStorageAccess(vertx, tenant);

        SqlQuery queryFromCql = new RecordFailure().makeSqlFromCqlQuery(
                        routingContext, moduleStorage.schemaDotTable(Tables.record_failure_view))
                .withDefaultLimit("100");
        RequestParameters params = routingContext.get(ValidationHandler.REQUEST_CONTEXT_KEY);
        RequestParameter jobId = params.pathParameter("id");
        RequestParameter from = params.queryParameter("from");
        RequestParameter until = params.queryParameter("until");

        String timeRange = null;
        if (from != null && until != null) {
            timeRange = " (time_stamp >= '" + from.getString()
                    + "'  AND time_stamp <= '" + until.getString() + "') ";
        } else if (from != null) {
            timeRange = " time_stamp >= '" + from.getString() + "' ";
        } else if (until != null) {
            timeRange = " time_stamp <= '" + until.getString() + "' ";
        }

        if (jobId != null) {
            queryFromCql.withAdditionalWhereClause("import_job_id = '" + jobId + "'");
        }
        if (timeRange != null) {
            queryFromCql.withAdditionalWhereClause(timeRange);
        }

        return moduleStorage.getEntities(queryFromCql.getQueryWithLimits(), new RecordFailure()).onComplete(
                failuresList -> {
                    if (failuresList.succeeded()) {
                        JsonObject responseJson = new JsonObject();
                        JsonArray recordFailures = new JsonArray();
                        responseJson.put("failedRecords", recordFailures);
                        List<Entity> failures = failuresList.result();
                        for (Entity failure : failures) {
                            recordFailures.add(failure.asJson());
                        }
                        moduleStorage.getCount(queryFromCql.getCountingSql()).onComplete(
                                count -> {
                                    responseJson.put("totalRecords", count.result());
                                    responseJson(routingContext, 200).end(responseJson.encodePrettily());
                                }
                        );
                    }
                }
        ).mapEmpty();
    }


    private Future<Void> postFailedRecordsForJob(Vertx vertx, RoutingContext routingContext) {
        String tenant = TenantUtil.tenant(routingContext);
        JsonObject body = routingContext.body().asJsonObject();
        JsonArray recs = body.getJsonArray(new RecordFailure().jsonCollectionName());
        List<Entity> failedRecs = new ArrayList<>();
        for (Object o : recs) {
            failedRecs.add(new RecordFailure().fromJson((JsonObject) o));
        }
        return new ModuleStorageAccess(vertx, tenant)
                .storeEntities(new RecordFailure(), failedRecs)
                .onSuccess(configId ->
                        responseJson(routingContext, 201).end(failedRecs.size() + " record failures logged."))
                .mapEmpty();
    }

    private Future<Void> deleteRecordFailure(Vertx vertx, RoutingContext routingContext) {
        return deleteEntity(vertx, routingContext, new RecordFailure());
    }

    private Future<Void> purgeAgedLogs(Vertx vertx, RoutingContext routingContext) {
        logger.info("Running timer process: purge aged logs");
        final String SETTINGS_SCOPE = "mod-inventory-import";
        final String SETTINGS_KEY = "PURGE_LOGS_AFTER";
        SettingsClient.getStringValue(routingContext,
                        SETTINGS_SCOPE,
                        SETTINGS_KEY)
                .onComplete(settingsValue -> {
                    if (settingsValue.result() != null) {
                        applyPurgeOfPastJobs(vertx, routingContext, settingsValue.result());
                    } else {
                        final String CONFIGS_MODULE = "mod-inventory-import";
                        final String CONFIGS_CONFIG_NAME = "PURGE_LOGS_AFTER";
                        ConfigurationsClient.getStringValue(routingContext,
                                        CONFIGS_MODULE,
                                        CONFIGS_CONFIG_NAME)
                                .onComplete(configsValue -> applyPurgeOfPastJobs(vertx, routingContext, configsValue.result()));
                    }
                });
        return Future.succeededFuture();
    }

    private void applyPurgeOfPastJobs(Vertx vertx, RoutingContext routingContext, String purgeSetting) {
        Period ageForDeletion = Miscellaneous.getPeriod(purgeSetting,3, "MONTHS");
        LocalDateTime untilDate = SettableClock.getLocalDateTime().minus(ageForDeletion).truncatedTo(ChronoUnit.MINUTES);
        logger.info("Running timer process: purging aged logs from before " + untilDate);
        String tenant = TenantUtil.tenant(routingContext);
        ModuleStorageAccess moduleStorage = new ModuleStorageAccess(vertx, tenant);
        moduleStorage.purgePreviousJobsByAge(untilDate)
                .onComplete(x -> routingContext.response().setStatusCode(204).end()).mapEmpty();
    }


    private Future<Void> postStep(Vertx vertx, RoutingContext routingContext) {
        Step step = new Step().fromJson(routingContext.body().asJsonObject());
        String validationResponse = step.validateScriptAsXml();
        if (validationResponse.equals("OK")) {
            return storeEntityRespondWith201(vertx, routingContext, step);
        }  else {
            return Future.failedFuture(validationResponse);
        }
    }

    private Future<Void> putStep(Vertx vertx, RoutingContext routingContext) {
        String tenant = TenantUtil.tenant(routingContext);
        Step step = new Step().fromJson(routingContext.body().asJsonObject());
        String validationResponse = step.validateScriptAsXml();
        if (validationResponse.equals("OK")) {
            RequestParameters params = routingContext.get(ValidationHandler.REQUEST_CONTEXT_KEY);
            UUID id = UUID.fromString(params.pathParameter("id").getString());
            ModuleStorageAccess db = new ModuleStorageAccess(vertx,tenant);
            return db.updateEntity(id,step)
                    .onSuccess(result-> {
                        if (result.rowCount()==1) {
                            responseText(routingContext, 204).end();
                        } else {
                            responseText(routingContext, 404).end("Not found");
                        }
                    }).mapEmpty();
        }  else {
            return Future.failedFuture(validationResponse);
        }
    }

    private Future<Void> getSteps(Vertx vertx, RoutingContext routingContext) {
        return getEntities(vertx, routingContext, new Step());
    }

    private Future<Void> getStepById(Vertx vertx, RoutingContext routingContext) {
        return getEntity(vertx, routingContext, new Step());
    }

    private Future<Void> deleteStep(Vertx vertx, RoutingContext routingContext) {
        return deleteEntity(vertx, routingContext, new Step());
    }

    private Future<Void> getScript(Vertx vertx, RoutingContext routingContext) {
        String tenant = TenantUtil.tenant(routingContext);
        return new ModuleStorageAccess(vertx, tenant).getScript(routingContext)
                .onSuccess(script -> responseText(routingContext, 200).end(script))
                .mapEmpty();
    }

    private Future<Void> putScript(Vertx vertx, RoutingContext routingContext) {
        String tenant = TenantUtil.tenant(routingContext);
        String validationResponse = Step.validateScriptAsXml(routingContext.body().asString());
        if (validationResponse.equals("OK")) {
            return new ModuleStorageAccess(vertx, tenant).putScript(routingContext)
                    .onSuccess(script -> responseText(routingContext, 204).end())
                    .mapEmpty();
        } else {
            return Future.failedFuture(validationResponse);
        }
    }

    private Future<Void> postTransformation(Vertx vertx, RoutingContext routingContext) {
        Entity transformation = new Transformation().fromJson(routingContext.body().asJsonObject());
        return storeEntityRespondWith201(vertx, routingContext, transformation);
    }

    private Future<Void> getTransformationById(Vertx vertx, RoutingContext routingContext) {
        return getEntity(vertx, routingContext, new Transformation());
    }

    private Future<Void> getTransformations(Vertx vertx, RoutingContext routingContext) {
        return getEntities(vertx, routingContext, new Transformation());
    }

    private Future<Void> putTransformation(Vertx vertx, RoutingContext routingContext) {
        String tenant = TenantUtil.tenant(routingContext);
        Transformation transformation = new Transformation().fromJson(routingContext.body().asJsonObject());
        RequestParameters params = routingContext.get(ValidationHandler.REQUEST_CONTEXT_KEY);
        UUID id = UUID.fromString(params.pathParameter("id").getString());
        ModuleStorageAccess db = new ModuleStorageAccess(vertx,tenant);
        return db.updateEntity(id, transformation)
                .onSuccess(result-> {
                    if (result.rowCount()==1) {
                        responseText(routingContext, 204).end();
                    } else {
                        responseText(routingContext, 404).end("Not found");
                    }
                }).mapEmpty();
    }

    private Future<Void> deleteTransformation(Vertx vertx, RoutingContext routingContext) {
        return deleteEntity(vertx, routingContext, new Transformation());
    }

    private Future<Void> postTransformationStep(Vertx vertx, RoutingContext routingContext) {
        Entity transformationStep = new TransformationStep().fromJson(routingContext.body().asJsonObject());
        return storeEntityRespondWith201(vertx, routingContext, transformationStep);
    }

    private Future<Void> getTransformationStepById(Vertx vertx, RoutingContext routingContext) {
        return getEntity(vertx, routingContext, new TransformationStep());
    }

    private Future<Void> getTransformationSteps(Vertx vertx, RoutingContext routingContext) {
        return getEntities(vertx, routingContext, new TransformationStep());
    }

    private Future<Void> putTransformationStep(Vertx vertx, RoutingContext routingContext) {
        String tenant = TenantUtil.tenant(routingContext);
        TransformationStep transformationStep = new TransformationStep().fromJson(routingContext.body().asJsonObject());
        RequestParameters params = routingContext.get(ValidationHandler.REQUEST_CONTEXT_KEY);
        UUID id = UUID.fromString(params.pathParameter("id").getString());
        ModuleStorageAccess db = new ModuleStorageAccess(vertx,tenant);
        return db.updateEntity(id, transformationStep)
                .onSuccess(result-> {
                    if (result.rowCount()==1) {
                        responseText(routingContext, 204).end();
                    } else {
                        responseText(routingContext, 404).end("Not found");
                    }
                }).mapEmpty();
    }

    private Future<Void> deleteTransformationStep(Vertx vertx, RoutingContext routingContext) {
        return deleteEntity(vertx, routingContext, new TransformationStep());
    }

    private Future<Void> stageXmlSourceFile(Vertx vertx, RoutingContext routingContext) {

        final long fileStartTime = System.currentTimeMillis();
        String tenant = TenantUtil.tenant(routingContext);
        String importConfigId = routingContext.pathParam("id");
        String fileName = routingContext.queryParam("filename").stream().findFirst().orElse(UUID.randomUUID() + ".xml");
        Buffer xmlContent = Buffer.buffer(routingContext.body().asString());

        return activateFileListener(vertx, tenant, importConfigId, routingContext)
                .onSuccess(ignore -> {
                    new FileQueue(vertx, tenant, importConfigId).addNewFile(fileName, xmlContent);
                    responseText(routingContext, 200).end("File queued for processing in ms " + (System.currentTimeMillis() - fileStartTime));
                }).mapEmpty();
    }

    private Future<Void> activateFileListener(Vertx vertx, RoutingContext routingContext) {
        String tenant = TenantUtil.tenant(routingContext);
        String importConfigId = routingContext.pathParam("id");
        return activateFileListener(vertx, tenant, importConfigId, routingContext)
                .onSuccess(response -> responseText(routingContext, 200).end(response)).mapEmpty();
    }

    private Future<String> activateFileListener(Vertx vertx, String tenant, String importConfigId, RoutingContext routingContext) {
        Promise<String> promise = Promise.promise();
        new ModuleStorageAccess(vertx, tenant).getEntity(UUID.fromString(importConfigId), new ImportConfig())
                .onSuccess(cfg -> {
                    if (cfg != null) {
                        XmlFileListener
                                .deployIfNotDeployed(vertx, tenant, importConfigId, routingContext)
                                .onSuccess(promise::complete);
                    } else {
                        promise.fail("Could not find import config with id [" + importConfigId + "].");
                    }
                }).mapEmpty();
        return promise.future();
    }

    private Future<Void> pauseImportJob(Vertx vertx, RoutingContext routingContext) {
        String tenant = TenantUtil.tenant(routingContext);
        String importConfigId = routingContext.pathParam("id");
        if (FileListeners.hasFileListener(tenant, importConfigId)) {
            FileProcessor job = FileListeners.getFileListener(tenant, importConfigId).getImportJob();
            if (job.paused()) {
                responseText(routingContext, 200).end("File listener already paused for import config [" + importConfigId + "].");
            } else {
                job.pause();
                responseText(routingContext, 200).end("Processing paused for import config [" + importConfigId + "].");
            }
        } else {
            responseText(routingContext, 200).end("Currently no running import process found to pause for import config [" + importConfigId + "].");
        }
        return Future.succeededFuture();
    }

    private Future<Void> resumeImportJob(Vertx vertx, RoutingContext routingContext) {
        String tenant = TenantUtil.tenant(routingContext);
        String importConfigId = routingContext.pathParam("id");
        if (FileListeners.hasFileListener(tenant, importConfigId)) {
            FileProcessor job = FileListeners.getFileListener(tenant, importConfigId).getImportJob();
            if (job.paused()) {
                job.resume();
                responseText(routingContext, 200).end("Processing resumed for import config [" + importConfigId + "].");
            } else {
                responseText(routingContext, 200).end("File listener already active for import config [" + importConfigId + "].");
            }
        } else {
            responseText(routingContext, 200).end("Currently no running import process found to resume for import config [" + importConfigId + "].");
        }
        return Future.succeededFuture();

    }

    private void handler(Vertx vertx, RouterBuilder routerBuilder, String operation,
                         BiFunction<Vertx, RoutingContext, Future<Void>> method) {
        routerBuilder
                .operation(operation)
                .handler(ctx -> {
                    try {
                        method.apply(vertx, ctx)
                                .onFailure(cause -> exceptionResponse(cause, ctx));
                    } catch (Exception e) {  // exception thrown by method
                        logger.error("{}: {}", operation, e.getMessage(), e);
                        exceptionResponse(e, ctx);
                    }
                })
                .failureHandler(this::routerExceptionResponse);  // OpenAPI validation exception
    }


}
