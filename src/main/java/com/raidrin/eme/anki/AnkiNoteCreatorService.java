package com.raidrin.eme.anki;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AnkiNoteCreatorService {
    private final ObjectMapper objectMapper;
    private final AnkiConnectService ankiConnectService;

    public String addNote(String deckName, String front, String back) {
        ankiConnectService.createDeck(deckName);

        ObjectNode params = objectMapper.createObjectNode();
        ObjectNode note = objectMapper.createObjectNode();
        note.put("deckName", deckName);
        note.put("modelName", "Basic");

        note.put("fields", objectMapper.createObjectNode()
                .put("Front", front)
                .put("Back", back)
        );

        params.put("note", note);

        return ankiConnectService.postToAnkiConnect("addNote", params);
    }
}
