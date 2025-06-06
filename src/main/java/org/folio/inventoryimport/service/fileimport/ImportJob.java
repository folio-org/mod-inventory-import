package org.folio.inventoryimport.service.fileimport;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.inventoryimport.moduledata.ImportConfig;
import org.folio.inventoryimport.moduledata.database.ModuleStorageAccess;
import org.folio.inventoryimport.service.fileimport.transformation.TransformationPipeline;
import org.folio.inventoryimport.utils.SettableClock;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;


public class ImportJob {
    final UUID importConfigId;
    org.folio.inventoryimport.moduledata.ImportJob importJob;
    Reporting reporting;
    FileQueue fileQueue;
    TransformationPipeline transformationPipeline;
    InventoryBatchUpdater updater;
    final Vertx vertx;
    final ModuleStorageAccess configStorage;
    public static final Logger logger = LogManager.getLogger("ImportJob");


    private ImportJob(Vertx vertx, String tenant, UUID importConfigId) {
        this.vertx = vertx;
        this.importConfigId = importConfigId;
        this.configStorage = new ModuleStorageAccess(vertx, tenant);
    }

    public static Future<ImportJob> instantiateJob (String tenant, UUID jobConfigId, FileQueue fileQueue, Vertx vertx, RoutingContext routingContext) {
        ImportJob job = new ImportJob(vertx, tenant, jobConfigId);
        job.fileQueue = fileQueue;                          // Handle to source files in vertx file system
        job.reporting = new Reporting(job, tenant, vertx); // Logging progress and results
        job.updater = new InventoryBatchUpdater(job, routingContext); // Batching and persisting records in inventory.
        return job.initiateJobLog(jobConfigId)
                .compose(na -> job.getTransformationPipeline(tenant, jobConfigId, vertx))
                .compose(na -> Future.succeededFuture(job));
    }

    public boolean fileQueueDone(boolean atEndOfCurrentFile) {
        if (atEndOfCurrentFile && !fileQueue.hasNextFile() && !reporting.pendingFileStats()) {
            fileQueue.passive.set(true);
        }
        return fileQueue.passive.get();
    }

    public void setFinishedDateTime() {
        importJob.logFinishTime(SettableClock.getLocalDateTime(), configStorage);
    }

    Future<Void> processFile(File xmlFile) {
        Promise<Void> promise = Promise.promise();
        try {
            reporting.nowProcessing(xmlFile.getName());
            String xmlFileContents = Files.readString(xmlFile.toPath(), StandardCharsets.UTF_8);
            vertx.executeBlocking(new XmlRecordsFromFile(xmlFileContents).setTarget(transformationPipeline))
                            .onComplete(processing -> {
                                if (processing.succeeded()) {
                                    promise.complete();
                                } else {
                                    logger.error("Processing failed with " + processing.cause().getMessage());
                                    promise.complete();
                                }
                            });

        } catch (IOException e) {
            promise.fail("Could not open XML source file for importing " + e.getMessage());
        }
        return promise.future();
    }

    /**
     * If there's a file in the processing slot but no activity in the inventory updater, the current job
     * is assumed to be in a paused state, which could for example be due to a module restart.
     * @return true if there's a file ostensibly processing but no activity detected in inventory updater
     * for `idlingChecksThreshold` consecutive checks
     */
    public boolean resumeHaltedProcessing() {
        return fileQueue.processingSlotTaken() && updater.noPendingBatches(10);
    }

    private Future<UUID> initiateJobLog (UUID importConfigId) {
        return configStorage.getEntity(importConfigId, new ImportConfig())
                .compose(importConfig -> {
                    importJob = new org.folio.inventoryimport.moduledata.ImportJob().fromImportConfig((ImportConfig) importConfig);
                    return configStorage.storeEntity(importJob);
                });
    }

    private Future<TransformationPipeline> getTransformationPipeline(String tenant, UUID importConfigId, Vertx vertx) {
        Promise<TransformationPipeline> promise = Promise.promise();
        new ModuleStorageAccess(vertx, tenant).getEntity(importConfigId,new ImportConfig())
                .map(cfg -> ((ImportConfig) cfg).record.transformationId())
                .compose(transformationId -> TransformationPipeline.create(vertx, tenant, transformationId))
                .onComplete(pipelineBuild -> {
                    transformationPipeline = pipelineBuild.result();
                    transformationPipeline.setTarget(updater);
                    promise.complete(pipelineBuild.result());
                });
        return promise.future();
    }

}
