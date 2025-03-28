package org.folio.inventoryimport.moduledata.database;

import io.vertx.core.Future;
import org.folio.inventoryimport.moduledata.*;
import org.folio.tlib.postgres.TenantPgPool;

public class DatabaseInit {

    /**
     * Creates tables and views.
     */
    public static Future<Void> createDatabase(TenantPgPool pool) {
        String schema = pool.getSchema();
        return pool.query(new Step().makeCreateTableSql(schema)).execute()
                .compose(ignore -> pool.query(new Transformation().makeCreateTableSql(schema)).execute())
                .compose(ignore -> pool.query(new ImportConfig().makeCreateTableSql(schema)).execute())
                .compose(ignore -> pool.query(new ImportJobLog().makeCreateTableSql(schema)).execute())
                .compose(ignore -> pool.query(new RecordFailure().makeCreateTableSql(schema)).execute())
                .compose(ignore -> pool.query(createRecordFailureView(schema)).execute())
                .compose(ignore -> pool.query(new LogLine().makeCreateTableSql(schema)).execute())
                .compose(ignore -> pool.query(new TransformationStep().makeCreateTableSql(schema)).execute())
                .mapEmpty();

        /* Template for processing parameters in init.
          JsonArray parameters = tenantAttributes.getJsonArray("parameters");
          if (parameters != null) {
            for (int i = 0; i < parameters.size(); i++) {
              JsonObject parameter = parameters.getJsonObject(i);
              if ("loadSample".equals(parameter.getString("key"))
                  && "true".equals(parameter.getString("value"))) {
              }
            }
          }
        */
    }

    /**
     * Creates view.
     */
    public static String createRecordFailureView(String schema) {
        String ddl;
        ddl = "CREATE OR REPLACE VIEW " + schema + "." + Tables.record_failure_view
                + " AS SELECT rf.id AS id, "
                + "          rf.import_job_Id AS import_job_id, "
                + "          ij.import_config_id AS import_config_id, "
                + "          ij.import_config_name AS import_config_name, "
                + "          rf.record_number AS record_number, "
                + "          rf.time_stamp AS time_stamp, "
                + "          rf.record_errors AS record_errors, "
                + "          rf.original_record AS original_record, "
                + "          rf.transformed_record AS transformed_record "
                + "  FROM " + schema + ".record_failure AS rf, "
                + "       " + schema + ".import_job as ij "
                + "  WHERE rf.import_job_id = ij.id";
        return ddl;
    }

}
