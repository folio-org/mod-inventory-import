package org.folio.inventoryimport.service.fileimport;

import io.vertx.core.*;
import io.vertx.ext.web.RoutingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

public class XmlFilesImportVerticle extends AbstractVerticle {

    private final static ConcurrentMap<String, ConcurrentMap<String, XmlFilesImportVerticle>> fileImportVerticles = new ConcurrentHashMap<>();
    private final String tenant;
    private final UUID importConfigurationId;
    private ImportJob importJob;
    private final RoutingContext routingContext;
    final FileQueue fileQueue;
    public static final Logger logger = LogManager.getLogger("queued-files-processing");

    public XmlFilesImportVerticle(String tenant, String importConfigurationId, Vertx vertx, RoutingContext routingContext) {
        this.tenant = tenant;
        this.importConfigurationId = UUID.fromString(importConfigurationId);
        this.routingContext = routingContext;
        this.fileQueue = new FileQueue(vertx, tenant, importConfigurationId);
    }

    public static Future<String> deployIfUndeployed(Vertx vertx, String tenant, String importConfigurationId, RoutingContext routingContext) {
        Promise<String> promise = Promise.promise();
        fileImportVerticles.putIfAbsent(tenant, new ConcurrentHashMap<>());
        if (fileImportVerticles.get(tenant).get(importConfigurationId) == null) {
            fileImportVerticles.get(tenant).put(importConfigurationId,new XmlFilesImportVerticle(tenant, importConfigurationId, vertx, routingContext));
            vertx.deployVerticle(fileImportVerticles.get(tenant).get(importConfigurationId),
                    new DeploymentOptions().setWorkerPoolSize(1).setMaxWorkerExecuteTime(10).setMaxWorkerExecuteTimeUnit(TimeUnit.MINUTES)).onComplete(
                    started -> {
                        if (started.succeeded()) {
                            logger.info("Started verticle [" + started.result() + "] for [" + tenant + "] and configuration ID [" + importConfigurationId + "].");
                            promise.complete("Started verticle [" + started.result() + "] for configuration ID [" + importConfigurationId + "].");
                        } else {
                            logger.error("Couldn't start file processor verticle for tenant [" + tenant + "] and import configuration ID [" + importConfigurationId + "].");
                            promise.fail("Couldn't start file processor verticle for import configuration ID [" + importConfigurationId + "].");
                        }
                    });
        } else {
            promise.complete("Got already existing verticle for import configuration ID [" + importConfigurationId + "].");
        }
        return promise.future();
    }

    public static Future<String> undeployIfDeployed(Vertx vertx, String tenant, String importConfigId) {
        Promise<String> promise = Promise.promise();
        XmlFilesImportVerticle verticle = getVerticle(tenant, importConfigId);
        if (verticle != null) {
            try {
                String deploymentId = verticle.deploymentID();
                vertx.undeploy(verticle.deploymentID())
                        .onSuccess(ignore -> {
                            unregisterVerticle(tenant, importConfigId);
                            promise.complete("Un-deployed verticle [" + deploymentId + "] for import config [" + importConfigId + "].");
                        })
                        .onFailure(err -> promise.fail("Failed to undeploy verticle " + err.getMessage()));
            } catch (Exception e) {
                logger.error("Error attempting to undeploy import verticle for tenant [" + tenant + "] and import config [" + importConfigId + "].");
                promise.fail("Error attempting to undeploy import verticle for import config [" + importConfigId + "].");
            }
            verticle.getJob(false).onSuccess(job -> job.updater.clearTurnstile());
        } else {
            promise.complete("Currently no running verticle found to un-deploy for import config [" + importConfigId + "].");
        }
        return promise.future();
    }

    static XmlFilesImportVerticle getVerticle(String tenant, String importConfigId) {
        fileImportVerticles.putIfAbsent(tenant, new ConcurrentHashMap<>());
        return fileImportVerticles.get(tenant).get(importConfigId);
    }

    static void unregisterVerticle(String tenant, String importConfigId) {
        fileImportVerticles.putIfAbsent(tenant, new ConcurrentHashMap<>());
        fileImportVerticles.get(tenant).remove(importConfigId);
    }


    @Override
    public void start() {
        logger.info("Starting file processor for tenant [" + tenant + "] and job configuration ID [" + importConfigurationId + "].");
        vertx.setPeriodic(200, (r) -> {
            File currentFile = getNextFileIfPossible();
            if (currentFile != null) {  // null if queue is empty or a previous file is still processing
                boolean activating = fileQueue.passive.getAndSet(false); // check if job was passive before this file
                // Use existing job or instantiate new.
                getJob(activating)
                        .compose(job -> job.processFile(currentFile))
                        .onComplete(na -> fileQueue.deleteFile(currentFile))
                        .onFailure(f -> logger.error("Error processing file: " + f.getMessage()));
            }
        });
    }

    public File getNextFileIfPossible () {
        if (importJob != null && importJob.resumeHaltedProcessing()) {
            return fileQueue.currentlyPromotedFile();
        } else {
            return fileQueue.nextFileIfPossible();
        }
    }

    public Future<ImportJob> getJob (boolean activating) {
        if (activating) {
            return ImportJob.instantiateJob(tenant, importConfigurationId, fileQueue, vertx, routingContext)
                    .compose(job -> {
                        importJob = job;
                        return Future.succeededFuture(importJob);
                    });
        } else {
            return Future.succeededFuture(importJob);
        }
    }

}