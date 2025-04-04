package org.folio.inventoryimport.moduledata;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.templates.RowMapper;
import io.vertx.sqlclient.templates.TupleMapper;
import org.folio.inventoryimport.moduledata.database.Tables;
import org.folio.tlib.postgres.TenantPgPool;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TransformationStep extends Entity {

    public TransformationStep() {}

    public TransformationStep(UUID id, UUID transformationId, UUID stepId, Integer position) {
        record = new TransformationStepRecord(id, transformationId, stepId, position);
    }

    // Transformation/Step association record, the entity data.
    public record TransformationStepRecord(UUID id, UUID transformationId, UUID stepId, Integer position) {}
    public TransformationStepRecord record;

    // Static map of Entity Fields.
    private static final Map<String, Field> FIELDS = new HashMap<>();
    public static final String ID = "ID", TRANSFORMATION_ID = "TRANSFORMATION_ID", STEP_ID = "STEP_ID", POSITION="POSITION";
    static {
        FIELDS.put(ID,new Field("id", "id", PgColumn.Type.UUID, false, true, true));
        FIELDS.put(TRANSFORMATION_ID,new Field("transformationId", "transformation_id", PgColumn.Type.UUID, false, true));
        FIELDS.put(STEP_ID, new Field("stepId", "step_id", PgColumn.Type.UUID, true, true));
        FIELDS.put(POSITION, new Field("position", "position", PgColumn.Type.INTEGER, false, true));
    }
    @Override
    public Map<String, Field> fields() {
        return FIELDS;
    }

    @Override
    public String jsonCollectionName() {
        return "transformationStepAssociations";
    }

    @Override
    public String entityName() {
        return "Transformation-step association";
    }

    @Override
    public Entity fromJson(JsonObject json) {
        return new TransformationStep(
                getUuidOrGenerate(json.getString(jsonPropertyName(ID))),
                json.containsKey(jsonPropertyName(TRANSFORMATION_ID)) ?
                        UUID.fromString(json.getString(jsonPropertyName(TRANSFORMATION_ID)))
                        : UUID.fromString(json.getString("transformation")), // legacy Harvester schema
                json.containsKey(jsonPropertyName(STEP_ID)) ?
                   UUID.fromString(json.getString(jsonPropertyName(STEP_ID)))
                   : UUID.fromString(json.getJsonObject("step").getString("id")), // legacy Harvester schema
                Integer.parseInt(json.getString(jsonPropertyName(POSITION))));
    }

    @Override
    public JsonObject asJson() {
        JsonObject json = new JsonObject();
        json.put(jsonPropertyName(ID), record.id);
        json.put(jsonPropertyName(TRANSFORMATION_ID), record.transformationId);
        json.put(jsonPropertyName(STEP_ID), record.stepId);
        json.put(jsonPropertyName(POSITION), record.position);
        return json;
    }

    @Override
    public RowMapper<Entity> getRowMapper() {
        return row -> new TransformationStep(
               row.getUUID(dbColumnName(ID)),
               row.getUUID(dbColumnName(TRANSFORMATION_ID)),
               row.getUUID(dbColumnName(STEP_ID)),
               row.getInteger(dbColumnName(POSITION)));
    }

    @Override
    public TupleMapper<Entity> getTupleMapper() {
        return TupleMapper.mapper(
                entity -> {
                    TransformationStepRecord rec = ((TransformationStep) entity).record;
                    Map<String, Object> parameters = new HashMap<>();
                    parameters.put(dbColumnName(ID), rec.id);
                    parameters.put(dbColumnName(TRANSFORMATION_ID), rec.transformationId);
                    parameters.put(dbColumnName(STEP_ID), rec.stepId);
                    parameters.put(dbColumnName(POSITION), rec.position);
                    return parameters;
                });
    }

    @Override
    public Tables table() {
        return Tables.transformation_step;
    }


    @Override
    public Future<Void> createDatabase(TenantPgPool pool) {
        return executeSqlStatements(pool,

                "CREATE TABLE IF NOT EXISTS " + pool.getSchema() + "." + table()
                + " ("
                + dbColumnName(ID) + " UUID PRIMARY KEY, "
                + dbColumnName(TRANSFORMATION_ID) + " UUID NOT NULL "
                + " REFERENCES " + pool.getSchema() + "." + Tables.transformation + "(" + new Transformation().dbColumnName(Transformation.ID) + "), "
                + dbColumnName(STEP_ID) + " UUID NOT NULL "
                + " REFERENCES " + pool.getSchema() + "." + Tables.step + "(" + new Step().dbColumnName(Step.ID) + "), "
                + dbColumnName(POSITION) + " INTEGER NOT NULL "
                + ") "

        ).mapEmpty();
    }

}
