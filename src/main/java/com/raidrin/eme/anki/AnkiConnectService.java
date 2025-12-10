package com.raidrin.eme.anki;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@Service
@RequiredArgsConstructor
public class AnkiConnectService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${anki.connect.url}")
    private String ankiConnectUrl;


    public String postToAnkiConnect(String action, ObjectNode params) {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("action", action);
        requestBody.put("version", 6);
        requestBody.set("params", params);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(requestBody.toString(), headers);

        // Post request to Anki Connect
        return restTemplate.postForObject(ankiConnectUrl, request, String.class);
    }

    public String createDeck(String deckName) {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("deck", deckName);
        return postToAnkiConnect("createDeck", params);
    }

    // ========================================================================
    // CUSTOM NOTE TYPE MANAGEMENT (Phase 2.4)
    // ========================================================================

    /**
     * Get all model (note type) names from Anki
     * @return JSON response with model names array
     */
    public String getModelNames() {
        ObjectNode params = objectMapper.createObjectNode();
        return postToAnkiConnect("modelNames", params);
    }

    /**
     * Check if a specific model (note type) exists
     * @param modelName The name of the model to check
     * @return true if the model exists, false otherwise
     */
    public boolean modelExists(String modelName) {
        try {
            String response = getModelNames();
            com.fasterxml.jackson.databind.JsonNode jsonNode = objectMapper.readTree(response);

            if (jsonNode.has("result") && jsonNode.get("result").isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode nameNode : jsonNode.get("result")) {
                    if (nameNode.asText().equals(modelName)) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            System.err.println("Error checking if model exists: " + e.getMessage());
            return false;
        }
    }

    /**
     * Create a custom model (note type) in Anki
     * @param modelName The name of the model
     * @param fieldNames List of field names for the model
     * @param frontTemplate HTML template for the front of the card
     * @param backTemplate HTML template for the back of the card
     * @param css CSS styling for the card
     * @return JSON response from AnkiConnect
     */
    public String createModel(String modelName, java.util.List<String> fieldNames,
                             String frontTemplate, String backTemplate, String css) {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("modelName", modelName);

        // Add field names as array
        com.fasterxml.jackson.databind.node.ArrayNode fieldsArray = objectMapper.createArrayNode();
        for (String fieldName : fieldNames) {
            fieldsArray.add(fieldName);
        }
        params.set("inOrderFields", fieldsArray);

        // Add card templates
        com.fasterxml.jackson.databind.node.ArrayNode templatesArray = objectMapper.createArrayNode();
        ObjectNode cardTemplate = objectMapper.createObjectNode();
        cardTemplate.put("Name", "Card 1");
        cardTemplate.put("Front", frontTemplate);
        cardTemplate.put("Back", backTemplate);
        templatesArray.add(cardTemplate);
        params.set("cardTemplates", templatesArray);

        // Add CSS
        params.put("css", css);

        return postToAnkiConnect("createModel", params);
    }

    /**
     * Get or create a custom model (note type)
     * This is a convenience method that checks if the model exists and creates it if not
     * @param modelName The name of the model
     * @param fieldNames List of field names
     * @param frontTemplate Front card template
     * @param backTemplate Back card template
     * @param css CSS styling
     * @return JSON response (model name if exists, creation response if created)
     */
    public String getOrCreateModel(String modelName, java.util.List<String> fieldNames,
                                  String frontTemplate, String backTemplate, String css) {
        if (modelExists(modelName)) {
            System.out.println("Model '" + modelName + "' already exists, using existing model");
            return "{\"result\": \"" + modelName + "\", \"error\": null}";
        }

        System.out.println("Creating new model: " + modelName);
        return createModel(modelName, fieldNames, frontTemplate, backTemplate, css);
    }

    /**
     * Get field names for a specific model
     * @param modelName The name of the model
     * @return JSON response with field names array
     */
    public String getModelFieldNames(String modelName) {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("modelName", modelName);
        return postToAnkiConnect("modelFieldNames", params);
    }
}