package org.folio.inventoryimport.test.fakestorage;

import io.vertx.core.impl.logging.Logger;
import io.vertx.core.json.JsonObject;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FolioApiRecord {
    public static final String ID = "id";
    public static final String VERSION = "_version";
    protected final JsonObject recordJson;

    private final Logger logger = io.vertx.core.impl.logging.LoggerFactory.getLogger("FolioApiRecord");

    public FolioApiRecord() {
        recordJson = new JsonObject();
    }

    public FolioApiRecord(JsonObject FolioApiRecord) {
        recordJson = FolioApiRecord;
    }


    public String getStringValue (String propertyName) {
        return recordJson.containsKey(propertyName) ? recordJson.getValue(propertyName).toString() : null;
    }

    public JsonObject getJson() {
        return recordJson;
    }

    public FolioApiRecord setId (String id) {
        recordJson.put(ID, id);
        return this;
    }

    public boolean hasId() {
        return recordJson.getString(ID) != null;
    }

    public FolioApiRecord generateId () {
        recordJson.put(ID, UUID.randomUUID().toString());
        return this;
    }

    public String getId () {
        return recordJson.getString(ID);
    }

    public FolioApiRecord setFirstVersion () {
        recordJson.put(VERSION,1);
        return this;
    }

    public Integer getVersion () {
        return recordJson.containsKey( VERSION ) ? recordJson.getInteger( VERSION ) : 0;
    }

    public FolioApiRecord setVersion (Integer version) {
        recordJson.put( VERSION,  version);
        return this;
    }

    public boolean match(String query) {
        logger.debug("Matching " + recordJson + " with query " + query);
        Pattern orListPattern = Pattern.compile("[(]?(.*)==\\(([^)]*)\\)[)]?");
        Matcher orListMatcher = orListPattern.matcher(query);
        if (orListMatcher.find()) {
            logger.debug("OR list found");
            String key = orListMatcher.group(1);
            String[] values = orListMatcher.group(2).split(" OR ");
            for (String value : values) {
                if (value.replace("\"","").equals(recordJson.getString(key))) {
                    return true;
                }
            }
        } else {
            String trimmed = query.replace("(", "").replace(")", "");
            String[] orSections = trimmed.split(" and ");
            logger.debug(
                    "orSections: " + ( orSections.length > 1 ? orSections[0] + ", " + orSections[1] : orSections[0] ));

            for (int i = 0; i < orSections.length; i++) {
                if (orSections[i].contains(" not ")) {
                    Pattern pattern = Pattern.compile(" not ([^ ]+)");
                    Matcher matcher = pattern.matcher(orSections[i]);
                    if (matcher.find()) {
                        String notCriterion = matcher.group(1);
                        String[] equalityParts = notCriterion.split("==");
                        String key = equalityParts[0];
                        String value = equalityParts.length > 1 ? equalityParts[1].replace("\"", "") : "";
                        if (recordJson.getString(key) != null && recordJson.getString(key).equals(
                                value)) {
                            logger.debug("NOT query, no match for " + key + " not equal to " + value + " in " + recordJson);
                            return false;
                        } else {
                            logger.debug("NOT query, have match for " + key + " not equal to " + value + " in " + recordJson);
                        }
                    }
                }
                String[] queryParts = orSections[i].split("==");
                logger.debug("query: " + query);
                logger.debug("queryParts[0]: " + queryParts[0]);
                String key = queryParts[0];
                String value = queryParts.length > 1 ? queryParts[1].replace("\"", "") : "";
                logger.debug("key: " + key);
                logger.debug("value: " + value);
                logger.debug("recordJson.getString(key): " + recordJson.getString(key));
                logger.debug(
                        "Query parameter [" + value + "] matches record property [" + key + "(" + recordJson.getString(
                                key) + ")] ?: " + ( recordJson.getString(
                                key) != null && recordJson.getString(key).equals(value) ));
                if (recordJson.getString(key) != null && recordJson.getString(key).equals(value)) {
                    return true;
                }
            }
        }
        return false;
    }

}
