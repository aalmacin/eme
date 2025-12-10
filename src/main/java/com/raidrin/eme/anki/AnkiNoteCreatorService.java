package com.raidrin.eme.anki;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AnkiNoteCreatorService {
    private final ObjectMapper objectMapper;
    private final AnkiConnectService ankiConnectService;
    private final AnkiCardBuilderService ankiCardBuilderService;

    @Value("${image.output.directory:./generated_images}")
    private String imageOutputDirectory;

    @Value("${audio.output.directory:./generated_audio}")
    private String audioOutputDirectory;

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

    /**
     * Create an Anki note with media files (images and audio)
     * @param deckName The name of the Anki deck
     * @param front The front content of the card
     * @param back The back content of the card
     * @param wordData The word data containing media file references
     * @return The response from AnkiConnect
     */
    public String addNoteWithMedia(String deckName, String front, String back, Map<?, ?> wordData) {
        ankiConnectService.createDeck(deckName);

        ObjectNode params = objectMapper.createObjectNode();
        ObjectNode note = objectMapper.createObjectNode();
        note.put("deckName", deckName);
        note.put("modelName", "Basic");

        note.put("fields", objectMapper.createObjectNode()
                .put("Front", front)
                .put("Back", back)
        );

        // Extract and add picture files
        List<MediaFile> pictures = extractPictures(wordData);
        if (!pictures.isEmpty()) {
            ArrayNode pictureArray = objectMapper.createArrayNode();
            for (MediaFile picture : pictures) {
                ObjectNode pictureNode = objectMapper.createObjectNode();
                pictureNode.put("filename", picture.getFilename());
                pictureNode.put("path", picture.getPath());

                ArrayNode fieldsArray = objectMapper.createArrayNode();
                picture.getFields().forEach(fieldsArray::add);
                pictureNode.set("fields", fieldsArray);

                pictureArray.add(pictureNode);
            }
            note.set("picture", pictureArray);
        }

        // Extract and add audio files
        List<MediaFile> audioFiles = extractAudio(wordData);
        if (!audioFiles.isEmpty()) {
            ArrayNode audioArray = objectMapper.createArrayNode();
            for (MediaFile audio : audioFiles) {
                ObjectNode audioNode = objectMapper.createObjectNode();
                audioNode.put("filename", audio.getFilename());
                audioNode.put("path", audio.getPath());

                ArrayNode fieldsArray = objectMapper.createArrayNode();
                audio.getFields().forEach(fieldsArray::add);
                audioNode.set("fields", fieldsArray);

                audioArray.add(audioNode);
            }
            note.set("audio", audioArray);
        }

        params.put("note", note);

        return ankiConnectService.postToAnkiConnect("addNote", params);
    }

    private List<MediaFile> extractPictures(Map<?, ?> wordData) {
        List<MediaFile> pictures = new ArrayList<>();

        // Extract image file
        if (wordData.containsKey("image_file") && wordData.get("image_file") != null) {
            String imageFile = wordData.get("image_file").toString();
            if (!imageFile.isEmpty()) {
                File imagePath = new File(imageOutputDirectory, imageFile);
                if (imagePath.exists()) {
                    pictures.add(new MediaFile(
                        imageFile,
                        imagePath.getAbsolutePath(),
                        List.of("Back") // Images typically go on the back of the card
                    ));
                }
            }
        }

        return pictures;
    }

    private List<MediaFile> extractAudio(Map<?, ?> wordData) {
        List<MediaFile> audioFiles = new ArrayList<>();

        // Extract source audio
        if (wordData.containsKey("source_audio_file") && wordData.get("source_audio_file") != null) {
            String audioFile = wordData.get("source_audio_file").toString();
            if (!audioFile.isEmpty()) {
                File audioPath = new File(audioOutputDirectory, audioFile);
                if (audioPath.exists()) {
                    audioFiles.add(new MediaFile(
                        audioFile,
                        audioPath.getAbsolutePath(),
                        List.of("Front")
                    ));
                }
            }
        }

        // Extract sentence source audio
        if (wordData.containsKey("sentence_source_audio_file") && wordData.get("sentence_source_audio_file") != null) {
            String audioFile = wordData.get("sentence_source_audio_file").toString();
            if (!audioFile.isEmpty()) {
                File audioPath = new File(audioOutputDirectory, audioFile);
                if (audioPath.exists()) {
                    audioFiles.add(new MediaFile(
                        audioFile,
                        audioPath.getAbsolutePath(),
                        List.of("Back")
                    ));
                }
            }
        }

        return audioFiles;
    }

    /**
     * Create an Anki note with custom note type and field mapping
     * @param deckName The name of the Anki deck
     * @param modelName The custom note type name
     * @param fields Map of field names to values
     * @param wordData The word data containing media file references
     * @return The response from AnkiConnect
     */
    public String addNoteWithCustomModel(String deckName, String modelName, Map<String, String> fields, Map<?, ?> wordData) {
        ankiConnectService.createDeck(deckName);

        ObjectNode params = objectMapper.createObjectNode();
        ObjectNode note = objectMapper.createObjectNode();
        note.put("deckName", deckName);
        note.put("modelName", modelName);

        // Set field values
        ObjectNode fieldsNode = objectMapper.createObjectNode();
        fields.forEach(fieldsNode::put);
        note.set("fields", fieldsNode);

        // Extract and add picture files for custom model
        List<MediaFile> pictures = extractPicturesForCustomModel(wordData);
        if (!pictures.isEmpty()) {
            ArrayNode pictureArray = objectMapper.createArrayNode();
            for (MediaFile picture : pictures) {
                ObjectNode pictureNode = objectMapper.createObjectNode();
                pictureNode.put("filename", picture.getFilename());
                pictureNode.put("path", picture.getPath());

                ArrayNode fieldsArray = objectMapper.createArrayNode();
                picture.getFields().forEach(fieldsArray::add);
                pictureNode.set("fields", fieldsArray);

                pictureArray.add(pictureNode);
            }
            note.set("picture", pictureArray);
        }

        // Extract and add audio files for custom model
        List<MediaFile> audioFiles = extractAudioForCustomModel(wordData);
        if (!audioFiles.isEmpty()) {
            ArrayNode audioArray = objectMapper.createArrayNode();
            for (MediaFile audio : audioFiles) {
                ObjectNode audioNode = objectMapper.createObjectNode();
                audioNode.put("filename", audio.getFilename());
                audioNode.put("path", audio.getPath());

                ArrayNode fieldsArray = objectMapper.createArrayNode();
                audio.getFields().forEach(fieldsArray::add);
                audioNode.set("fields", fieldsArray);

                audioArray.add(audioNode);
            }
            note.set("audio", audioArray);
        }

        params.put("note", note);

        return ankiConnectService.postToAnkiConnect("addNote", params);
    }

    /**
     * Extract pictures for custom note type models
     */
    private List<MediaFile> extractPicturesForCustomModel(Map<?, ?> wordData) {
        List<MediaFile> pictures = new ArrayList<>();

        // Extract image file - maps to "Image" field in custom model
        if (wordData.containsKey("image_file") && wordData.get("image_file") != null) {
            String imageFile = wordData.get("image_file").toString();
            if (!imageFile.isEmpty()) {
                File imagePath = new File(imageOutputDirectory, imageFile);
                if (imagePath.exists()) {
                    pictures.add(new MediaFile(
                        imageFile,
                        imagePath.getAbsolutePath(),
                        List.of("Image") // Custom model uses "Image" field
                    ));
                }
            }
        }

        return pictures;
    }

    /**
     * Extract audio files for custom note type models
     */
    private List<MediaFile> extractAudioForCustomModel(Map<?, ?> wordData) {
        List<MediaFile> audioFiles = new ArrayList<>();

        // Extract source audio - maps to "SourceAudio" field
        if (wordData.containsKey("source_audio_file") && wordData.get("source_audio_file") != null) {
            String audioFile = wordData.get("source_audio_file").toString();
            if (!audioFile.isEmpty()) {
                File audioPath = new File(audioOutputDirectory, audioFile);
                if (audioPath.exists()) {
                    audioFiles.add(new MediaFile(
                        audioFile,
                        audioPath.getAbsolutePath(),
                        List.of("SourceAudio") // Custom model uses "SourceAudio" field
                    ));
                }
            }
        }

        // Extract sentence source audio - maps to "SentenceSourceAudio" field
        if (wordData.containsKey("sentence_source_audio_file") && wordData.get("sentence_source_audio_file") != null) {
            String audioFile = wordData.get("sentence_source_audio_file").toString();
            if (!audioFile.isEmpty()) {
                File audioPath = new File(audioOutputDirectory, audioFile);
                if (audioPath.exists()) {
                    audioFiles.add(new MediaFile(
                        audioFile,
                        audioPath.getAbsolutePath(),
                        List.of("SentenceSourceAudio") // Custom model uses "SentenceSourceAudio" field
                    ));
                }
            }
        }

        return audioFiles;
    }
}
