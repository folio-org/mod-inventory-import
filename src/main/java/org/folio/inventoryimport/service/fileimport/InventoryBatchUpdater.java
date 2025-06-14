package org.folio.inventoryimport.service.fileimport;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.ext.web.RoutingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.inventoryimport.foliodata.InventoryUpdateClient;

import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class InventoryBatchUpdater implements RecordReceiver {

    private final ImportJob job;
    private final ArrayList<ProcessingRecord> records = new ArrayList<>();
    private final InventoryUpdateClient updateClient;
    private final Turnstile turnstile = new Turnstile();
    public static final Logger logger = LogManager.getLogger("InventoryBatchUpdater");


    public InventoryBatchUpdater(ImportJob importJob, RoutingContext routingContext) {
        updateClient = InventoryUpdateClient.getClient(routingContext);
        this.job = importJob;
    }

    @Override
    public void put(ProcessingRecord record) {
        if (record != null) {
            record.setBatchIndex(records.size());
            records.add(record);
            if (records.size() > 99) {
                ArrayList<ProcessingRecord> copyOfRecords = new ArrayList<>(records);
                records.clear();
                releaseBatch(new BatchOfRecords(copyOfRecords, false));
            }
        } else { // a null record is the end-of-file signal, forward remaining records if any
            ArrayList<ProcessingRecord> copyOfRecords = new ArrayList<>(records);
            records.clear();
            releaseBatch(new BatchOfRecords(copyOfRecords, true));
        }
    }

    private void releaseBatch(BatchOfRecords batch) {
        turnstile.enterBatch(batch);
        persistBatch().onFailure(na -> {
            turnstile.exitBatch();
            logger.error("Unexpected error during upsert " + na.getMessage());
        }).onSuccess(na -> turnstile.exitBatch());
    }

    @Override
    public void endOfDocument() {
        put(null);
    }

    public void clearTurnstile() {
        turnstile.clear();
    }

    /**
     * This is the last function of the import pipeline, and since it's asynchronous
     * it must be in charge of when to invoke reporting. The job handling verticle will not
     * know when the last upsert of a source file of records is done, for example.
     */
    private Future<Void> persistBatch() {
        Promise<Void> promise = Promise.promise();
        BatchOfRecords batch = turnstile.viewCurrentBatch();
        if (batch != null) {
            if (batch.size() > 0) {
                updateClient.inventoryUpsert(batch.getUpsertRequestBody()).onSuccess(upsert -> {
                    job.reporting.incrementRecordsProcessed(batch.size());
                    if (upsert.statusCode() == 207) {
                        batch.setResponse(upsert);
                        job.reporting.reportErrors(batch)
                                .onFailure(err -> logger.error("Error logging upsert results " + err.getMessage()));
                    }
                    job.reporting.incrementInventoryMetrics(new InventoryMetrics(upsert.getMetrics()));
                    if (batch.isLastBatchOfFile()) {
                        reportEndOfFile();
                    }
                    promise.complete();
                });
            } else { // we get here when the last set of records is exactly 100. We just need to report
                if (batch.isLastBatchOfFile()) {
                    reportEndOfFile();
                }
                promise.complete();
            }
        }
        return promise.future();
    }

    private void reportEndOfFile() {
        job.reporting.endOfFile();
        boolean queueDone = job.fileQueueDone(true);
        if (queueDone) {
            job.reporting.endOfQueue();
        }
    }

    public boolean noPendingBatches(int idlingChecksThreshold) {
        return turnstile.isIdle(idlingChecksThreshold);
    }


    /** Class wrapping a blocking queue of one, acting as a turnstile for batches in order to persist them one
     * at a time with no overlap. */
    private static class Turnstile {

        private final BlockingQueue<BatchOfRecords> turnstile = new ArrayBlockingQueue<>(1);
        // Records the number of consecutive checks of whether the queue is idling.
        private final AtomicInteger turnstileEmptyChecks = new AtomicInteger(0);

        /**
         * Puts batch in blocking queue-of-one; process waits if previous batch still in queue.
         */
        private void enterBatch(BatchOfRecords batch) {
            try {
                turnstile.put(batch);
            } catch (InterruptedException ie) {
                throw new RuntimeException("Putting next batch in queue-of-one interrupted: " + ie.getMessage());
            }
        }

        private void clear() {
            turnstile.clear();
        }

        private void exitBatch() {
            try {
                turnstile.take();
            } catch (InterruptedException ie) {
                throw new RuntimeException("Taking batch from queue-of-one interrupted: " + ie.getMessage());
            }
        }

        private BatchOfRecords viewCurrentBatch() {
            return turnstile.peek();
        }

        private boolean isIdle(int idlingChecksThreshold) {
            if (turnstile.isEmpty()) {
                if (turnstileEmptyChecks.incrementAndGet() > idlingChecksThreshold) {
                    logger.info("Batch turnstile has been idle for " + idlingChecksThreshold + " consecutive checks.");
                    turnstileEmptyChecks.set(0);
                    return true;
                }
            } else {
                logger.info("Turnstile not empty");
                turnstileEmptyChecks.set(0);
            }
            return false;
        }

    }
}
