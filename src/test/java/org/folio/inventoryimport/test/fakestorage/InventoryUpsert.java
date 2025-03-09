package org.folio.inventoryimport.test.fakestorage;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import static org.folio.inventoryimport.test.fakestorage.RecordStorage.respond;

public class InventoryUpsert {
    protected void inventoryBatchUpsertHrid (RoutingContext routingContext) {
       respond(routingContext, getMetrics(), 200);
    }
    private JsonObject getMetrics() {
        return new JsonObject("{\n" +
                "  \"instance\" : {\n" +
                "    \"source\" : \"K10plus\",\n" +
                "    \"hrid\" : \"359314724\",\n" +
                "    \"modeOfIssuanceId\" : \"9d18a02f-5897-4c31-9106-c9abb5c7ae8b\",\n" +
                "    \"instanceTypeId\" : \"6312d172-f0cf-40f6-b27d-9fa8feaf332f\",\n" +
                "    \"instanceFormatIds\" : [ \"8d511d33-5e85-4c5d-9bce-6e3c9cd0c324\" ],\n" +
                "    \"identifiers\" : [ {\n" +
                "      \"value\" : \"359314724\",\n" +
                "      \"identifierTypeId\" : \"1d5cb40c-508f-451b-8952-87c92be4255a\"\n" +
                "    }, {\n" +
                "      \"value\" : \"51698851\",\n" +
                "      \"identifierTypeId\" : \"439bfbae-75bc-4f74-9fc7-b2a2d47ce3ef\"\n" +
                "    }, {\n" +
                "      \"value\" : \"GBV: 359314724\",\n" +
                "      \"identifierTypeId\" : \"8e33c1be-e2c4-43ac-a975-8fb50f71137a\"\n" +
                "    } ],\n" +
                "    \"title\" : \"Describing a class of global attractors via symbol sequences / Jörg Härterich; Matthias Wolfrum\",\n" +
                "    \"indexTitle\" : \"Describing a class of global attractors via symbol sequences Jörg Härterich Matthias Wolfrum\",\n" +
                "    \"alternativeTitles\" : [ ],\n" +
                "    \"contributors\" : [ {\n" +
                "      \"name\" : \"Härterich, Jörg\",\n" +
                "      \"contributorNameTypeId\" : \"2b94c631-fca9-4892-a730-03ee529ffe2a\",\n" +
                "      \"primary\" : \"true\"\n" +
                "    }, {\n" +
                "      \"name\" : \"Wolfrum, Matthias\",\n" +
                "      \"contributorNameTypeId\" : \"2b94c631-fca9-4892-a730-03ee529ffe2a\"\n" +
                "    } ],\n" +
                "    \"publication\" : [ {\n" +
                "      \"place\" : \"Berlin\",\n" +
                "      \"publisher\" : \"WIAS\",\n" +
                "      \"dateOfPublication\" : \"2002\",\n" +
                "      \"role\" : \"Publisher\"\n" +
                "    } ],\n" +
                "    \"electronicAccess\" : [ ],\n" +
                "    \"notes\" : [ {\n" +
                "      \"note\" : \"Erscheint auch als Online-Ausgabe: Describing a class of global attractors via symbol sequences / Härterich, Jörg. - Berlin : Weierstraß-Institut für Angewandte Analysis und Stochastik (WIAS), 2002\",\n" +
                "      \"instanceNoteTypeId\" : \"1d51e8b2-dee7-43f5-983c-a40757b9cdfa\"\n" +
                "    } ],\n" +
                "    \"natureOfContentTermIds\" : [ \"4570a93e-ddb6-4200-8e8b-283c8f5c9bfa\" ],\n" +
                "    \"languages\" : [ \"eng\" ],\n" +
                "    \"series\" : [ {\n" +
                "      \"value\" : \"Preprint ; 746\"\n" +
                "    } ],\n" +
                "    \"physicalDescriptions\" : [ \"26 S\" ],\n" +
                "    \"administrativeNotes\" : [ \"Aau (0500: Bibliografische Gattung)\", \"08.02.24, 00:59 (0210: Datum der letzten Änderung)\" ],\n" +
                "    \"id\" : \"9aa90608-cc51-4314-a22e-e9db237b964e\"\n" +
                "  },\n" +
                "  \"holdingsRecords\" : [ {\n" +
                "    \"hrid\" : \"586366237\",\n" +
                "    \"permanentLocationId\" : \"78091d58-5057-4f5d-bbcc-ca07eafd8cc1\",\n" +
                "    \"callNumber\" : \"K 2003 B 324\",\n" +
                "    \"holdingsTypeId\" : \"0c422f92-0f4d-4d32-8cbe-390ebc33a3e5\",\n" +
                "    \"sourceId\" : \"fa687f33-aab5-4119-b0ad-05afe8de4d92\",\n" +
                "    \"administrativeNotes\" : [ \"27-12-10, 18:16 (7903: Datum und Uhrzeit der letzten Änderung)\" ],\n" +
                "    \"discoverySuppress\" : \"false\",\n" +
                "    \"notes\" : [ ],\n" +
                "    \"electronicAccess\" : [ ],\n" +
                "    \"instanceId\" : \"9aa90608-cc51-4314-a22e-e9db237b964e\",\n" +
                "    \"id\" : \"0127f3f0-c8f2-45a5-a74a-4c46850460e0\",\n" +
                "    \"items\" : [ {\n" +
                "      \"hrid\" : \"586366237\",\n" +
                "      \"materialTypeId\" : \"1a54b431-2e4f-452d-9cae-9cee66c9a892\",\n" +
                "      \"permanentLoanTypeId\" : \"a8cacfd0-b284-47ae-b5fa-235d768411e7\",\n" +
                "      \"status\" : {\n" +
                "        \"name\" : \"Available\"\n" +
                "      },\n" +
                "      \"barcode\" : \"7$213905248\",\n" +
                "      \"discoverySuppress\" : \"false\",\n" +
                "      \"holdingsRecordId\" : \"0127f3f0-c8f2-45a5-a74a-4c46850460e0\",\n" +
                "      \"id\" : \"848eaa77-b2e1-4706-a65c-49862b3b3649\"\n" +
                "    } ]\n" +
                "  } ],\n" +
                "  \"instanceRelations\" : {\n" +
                "    \"parentInstances\" : [ {\n" +
                "      \"subInstanceId\" : \"9aa90608-cc51-4314-a22e-e9db237b964e\",\n" +
                "      \"superInstanceId\" : \"9761ca4c-a0cc-4401-8da1-642ee3c158aa\",\n" +
                "      \"instanceRelationshipTypeId\" : \"30773a27-b485-4dab-aeb6-b8c04fa3cb17\"\n" +
                "    } ]\n" +
                "  },\n" +
                "  \"processing\" : {\n" +
                "    \"holdingsRecord\" : {\n" +
                "      \"retainOmittedRecord\" : {\n" +
                "        \"ifField\" : \"hrid\",\n" +
                "        \"matchesPattern\" : \"\\\\D+.*\"\n" +
                "      },\n" +
                "      \"retainExistingValues\" : {\n" +
                "        \"forOmittedProperties\" : \"true\"\n" +
                "      },\n" +
                "      \"statisticalCoding\" : [ {\n" +
                "        \"if\" : \"deleteSkipped\",\n" +
                "        \"becauseOf\" : \"HOLDINGS_RECORD_PATTERN_MATCH\",\n" +
                "        \"setCode\" : \"ac9bae48-d14c-4414-919a-292d539f9967\"\n" +
                "      }, {\n" +
                "        \"if\" : \"deleteSkipped\",\n" +
                "        \"becauseOf\" : \"ITEM_PATTERN_MATCH\",\n" +
                "        \"setCode\" : \"970b8b4e-ee88-4037-b954-a10ee75340f0\"\n" +
                "      } ]\n" +
                "    },\n" +
                "    \"item\" : {\n" +
                "      \"retainOmittedRecord\" : {\n" +
                "        \"ifField\" : \"hrid\",\n" +
                "        \"matchesPattern\" : \"\\\\D+.*\"\n" +
                "      },\n" +
                "      \"retainExistingValues\" : {\n" +
                "        \"forOmittedProperties\" : \"true\"\n" +
                "      },\n" +
                "      \"statisticalCoding\" : [ {\n" +
                "        \"if\" : \"deleteSkipped\",\n" +
                "        \"becauseOf\" : \"ITEM_STATUS\",\n" +
                "        \"setCode\" : \"e7b3071c-8cc0-48cc-9cd0-dfc82c4e4602\"\n" +
                "      } ],\n" +
                "      \"status\" : {\n" +
                "        \"policy\" : \"overwrite\",\n" +
                "        \"ifStatusWas\" : [ {\n" +
                "          \"name\" : \"On order\"\n" +
                "        }, {\n" +
                "          \"name\" : \"Available\"\n" +
                "        }, {\n" +
                "          \"name\" : \"Intellectual item\"\n" +
                "        }, {\n" +
                "          \"name\" : \"Missing\"\n" +
                "        }, {\n" +
                "          \"name\" : \"Restricted\"\n" +
                "        }, {\n" +
                "          \"name\" : \"Unknown\"\n" +
                "        } ]\n" +
                "      }\n" +
                "    }\n" +
                "  },\n" +
                "  \"metrics\" : {\n" +
                "    \"INSTANCE\" : {\n" +
                "      \"CREATE\" : {\n" +
                "        \"COMPLETED\" : 1,\n" +
                "        \"FAILED\" : 0,\n" +
                "        \"SKIPPED\" : 0,\n" +
                "        \"PENDING\" : 0\n" +
                "      },\n" +
                "      \"UPDATE\" : {\n" +
                "        \"COMPLETED\" : 0,\n" +
                "        \"FAILED\" : 0,\n" +
                "        \"SKIPPED\" : 0,\n" +
                "        \"PENDING\" : 0\n" +
                "      },\n" +
                "      \"DELETE\" : {\n" +
                "        \"COMPLETED\" : 0,\n" +
                "        \"FAILED\" : 0,\n" +
                "        \"SKIPPED\" : 0,\n" +
                "        \"PENDING\" : 0\n" +
                "      }\n" +
                "    },\n" +
                "    \"HOLDINGS_RECORD\" : {\n" +
                "      \"CREATE\" : {\n" +
                "        \"COMPLETED\" : 1,\n" +
                "        \"FAILED\" : 0,\n" +
                "        \"SKIPPED\" : 0,\n" +
                "        \"PENDING\" : 0\n" +
                "      },\n" +
                "      \"UPDATE\" : {\n" +
                "        \"COMPLETED\" : 0,\n" +
                "        \"FAILED\" : 0,\n" +
                "        \"SKIPPED\" : 0,\n" +
                "        \"PENDING\" : 0\n" +
                "      },\n" +
                "      \"DELETE\" : {\n" +
                "        \"COMPLETED\" : 0,\n" +
                "        \"FAILED\" : 0,\n" +
                "        \"SKIPPED\" : 0,\n" +
                "        \"PENDING\" : 0\n" +
                "      }\n" +
                "    },\n" +
                "    \"ITEM\" : {\n" +
                "      \"CREATE\" : {\n" +
                "        \"COMPLETED\" : 1,\n" +
                "        \"FAILED\" : 0,\n" +
                "        \"SKIPPED\" : 0,\n" +
                "        \"PENDING\" : 0\n" +
                "      },\n" +
                "      \"UPDATE\" : {\n" +
                "        \"COMPLETED\" : 0,\n" +
                "        \"FAILED\" : 0,\n" +
                "        \"SKIPPED\" : 0,\n" +
                "        \"PENDING\" : 0\n" +
                "      },\n" +
                "      \"DELETE\" : {\n" +
                "        \"COMPLETED\" : 0,\n" +
                "        \"FAILED\" : 0,\n" +
                "        \"SKIPPED\" : 0,\n" +
                "        \"PENDING\" : 0\n" +
                "      }\n" +
                "    },\n" +
                "    \"INSTANCE_RELATIONSHIP\" : {\n" +
                "      \"CREATE\" : {\n" +
                "        \"COMPLETED\" : 1,\n" +
                "        \"FAILED\" : 0,\n" +
                "        \"SKIPPED\" : 0,\n" +
                "        \"PENDING\" : 0\n" +
                "      },\n" +
                "      \"DELETE\" : {\n" +
                "        \"COMPLETED\" : 0,\n" +
                "        \"FAILED\" : 0,\n" +
                "        \"SKIPPED\" : 0,\n" +
                "        \"PENDING\" : 0\n" +
                "      },\n" +
                "      \"PROVISIONAL_INSTANCE\" : {\n" +
                "        \"COMPLETED\" : 1,\n" +
                "        \"FAILED\" : 0,\n" +
                "        \"SKIPPED\" : 0,\n" +
                "        \"PENDING\" : 0\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}\n");
   }
}

