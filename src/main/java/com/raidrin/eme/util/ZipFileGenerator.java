package com.raidrin.eme.util;

import com.raidrin.eme.storage.entity.TranslationSessionEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Utility for generating ZIP files from translation session assets
 */
@Component
public class ZipFileGenerator {

    @Value("${zip.output.directory:./session_zips}")
    private String zipOutputDirectory;

    @Value("${image.output.directory:./generated_images}")
    private String imageOutputDirectory;

    @Value("${audio.output.directory:./generated_audio}")
    private String audioOutputDirectory;

    /**
     * Create a ZIP file containing all assets for a translation session
     *
     * @param session The translation session
     * @param sessionData Session data containing file paths and metadata
     * @return Path to the created ZIP file
     */
    public String createSessionZip(TranslationSessionEntity session, Map<String, Object> sessionData) throws IOException {
        // Create output directory if it doesn't exist
        Path zipDir = Paths.get(zipOutputDirectory);
        if (!Files.exists(zipDir)) {
            Files.createDirectories(zipDir);
        }

        // Generate ZIP filename
        String sanitizedWord = FileNameSanitizer.sanitize(session.getWord(), "").replaceAll("\\.$", "");
        String zipFileName = "session_" + session.getId() + "_" + sanitizedWord + "_" + System.currentTimeMillis() + ".zip";
        Path zipFilePath = zipDir.resolve(zipFileName);

        // Collect files to include in ZIP
        List<Path> filesToZip = new ArrayList<>();
        java.util.Set<String> addedFiles = new java.util.HashSet<>(); // Track to avoid duplicates

        System.out.println("=== Starting ZIP file collection for session " + session.getId() + " ===");

        // Add audio files from the session (top-level audio_files array)
        if (sessionData.containsKey("audio_files") && sessionData.get("audio_files") instanceof List) {
            List<?> audioFiles = (List<?>) sessionData.get("audio_files");
            System.out.println("Found " + audioFiles.size() + " audio files in top-level audio_files array");
            for (Object audioFile : audioFiles) {
                if (audioFile instanceof String) {
                    Path audioPath = Paths.get((String) audioFile);
                    if (Files.exists(audioPath)) {
                        String absolutePath = audioPath.toAbsolutePath().toString();
                        if (!addedFiles.contains(absolutePath)) {
                            filesToZip.add(audioPath);
                            addedFiles.add(absolutePath);
                            System.out.println("Adding top-level audio file to ZIP: " + audioPath);
                        }
                    } else {
                        System.err.println("Top-level audio file not found: " + audioPath);
                    }
                }
            }
        } else {
            System.out.println("No top-level audio_files array found in session data");
        }

        // Add audio and image files from word-level processing
        if (sessionData.containsKey("words") && sessionData.get("words") instanceof List) {
            List<?> words = (List<?>) sessionData.get("words");
            System.out.println("Processing " + words.size() + " words for audio and image files");

            for (Object wordObj : words) {
                if (wordObj instanceof Map) {
                    Map<?, ?> wordData = (Map<?, ?>) wordObj;
                    String sourceWord = (String) wordData.get("source_word");

                    // Add source audio file
                    String sourceAudioFile = (String) wordData.get("source_audio_file");
                    if (sourceAudioFile != null) {
                        Path audioPath = resolveAudioPath(sourceAudioFile);
                        if (audioPath != null && Files.exists(audioPath)) {
                            String absolutePath = audioPath.toAbsolutePath().toString();
                            if (!addedFiles.contains(absolutePath)) {
                                filesToZip.add(audioPath);
                                addedFiles.add(absolutePath);
                                System.out.println("Adding source audio for '" + sourceWord + "': " + audioPath);
                            }
                        } else {
                            System.err.println("Source audio file not found for '" + sourceWord + "': " + sourceAudioFile + " -> " + audioPath);
                        }
                    }

                    // Add target audio files
                    if (wordData.containsKey("target_audio_files") && wordData.get("target_audio_files") instanceof List) {
                        List<?> targetAudioFiles = (List<?>) wordData.get("target_audio_files");
                        for (Object audioFileObj : targetAudioFiles) {
                            if (audioFileObj instanceof String) {
                                String targetAudioFile = (String) audioFileObj;
                                Path audioPath = resolveAudioPath(targetAudioFile);
                                if (audioPath != null && Files.exists(audioPath)) {
                                    String absolutePath = audioPath.toAbsolutePath().toString();
                                    if (!addedFiles.contains(absolutePath)) {
                                        filesToZip.add(audioPath);
                                        addedFiles.add(absolutePath);
                                        System.out.println("Adding target audio for '" + sourceWord + "': " + audioPath);
                                    }
                                } else {
                                    System.err.println("Target audio file not found for '" + sourceWord + "': " + targetAudioFile + " -> " + audioPath);
                                }
                            }
                        }
                    }

                    // Add image file
                    String imageLocalPath = (String) wordData.get("image_local_path");
                    if (imageLocalPath != null) {
                        Path imgPath = Paths.get(imageLocalPath);
                        if (Files.exists(imgPath)) {
                            String absolutePath = imgPath.toAbsolutePath().toString();
                            if (!addedFiles.contains(absolutePath)) {
                                filesToZip.add(imgPath);
                                addedFiles.add(absolutePath);
                                System.out.println("Adding image for '" + sourceWord + "': " + imgPath);
                            }
                        }
                    }
                }
            }
        } else {
            System.out.println("No words array found in session data");
        }

        // Legacy: Add single image file (for backward compatibility)
        String imageFile = (String) sessionData.get("image_file");
        if (imageFile != null) {
            Path imagePath = Paths.get(imageOutputDirectory, imageFile);
            if (Files.exists(imagePath)) {
                filesToZip.add(imagePath);
            }
        }

        // Legacy: Handle multiple images from old format (for backward compatibility)
        if (sessionData.containsKey("images") && sessionData.get("images") instanceof List) {
            List<?> images = (List<?>) sessionData.get("images");
            for (Object imgData : images) {
                if (imgData instanceof Map) {
                    Map<?, ?> imageMap = (Map<?, ?>) imgData;
                    String localPath = (String) imageMap.get("local_path");
                    if (localPath != null) {
                        Path imgPath = Paths.get(localPath);
                        if (Files.exists(imgPath)) {
                            filesToZip.add(imgPath);
                        }
                    }
                }
            }
        }

        // Create metadata file
        Path metadataPath = createMetadataFile(session, sessionData);
        if (metadataPath != null) {
            filesToZip.add(metadataPath);
        }

        // Log summary
        System.out.println("=== ZIP file collection summary ===");
        System.out.println("Total files to include in ZIP: " + filesToZip.size());
        long audioCount = filesToZip.stream().filter(p -> p.toString().endsWith(".mp3")).count();
        long imageCount = filesToZip.stream().filter(p -> p.toString().endsWith(".jpg") || p.toString().endsWith(".png")).count();
        System.out.println("  - Audio files: " + audioCount);
        System.out.println("  - Image files: " + imageCount);
        System.out.println("  - Other files: " + (filesToZip.size() - audioCount - imageCount));
        System.out.println("===================================");

        // Create the ZIP file
        createZip(filesToZip, zipFilePath);

        // Clean up metadata file
        if (metadataPath != null && Files.exists(metadataPath)) {
            Files.delete(metadataPath);
        }

        System.out.println("Created ZIP file: " + zipFilePath);
        return zipFilePath.toString();
    }

    /**
     * Create a ZIP file from a list of file paths
     *
     * @param files List of files to include
     * @param zipPath Destination ZIP file path
     */
    public void createZip(List<Path> files, Path zipPath) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(zipPath.toFile());
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            for (Path file : files) {
                if (!Files.exists(file)) {
                    System.err.println("File not found, skipping: " + file);
                    continue;
                }

                ZipEntry zipEntry = new ZipEntry(file.getFileName().toString());
                zos.putNextEntry(zipEntry);

                byte[] fileBytes = Files.readAllBytes(file);
                zos.write(fileBytes);
                zos.closeEntry();
            }
        }
    }

    /**
     * Create a metadata text file with session information
     */
    private Path createMetadataFile(TranslationSessionEntity session, Map<String, Object> sessionData) throws IOException {
        Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"));
        Path metadataPath = tempDir.resolve("session_" + session.getId() + "_metadata.txt");

        StringBuilder metadata = new StringBuilder();
        metadata.append("Translation Session Metadata\n");
        metadata.append("============================\n\n");
        metadata.append("Session ID: ").append(session.getId()).append("\n");
        metadata.append("Word/Phrase: ").append(session.getWord()).append("\n");
        metadata.append("Source Language: ").append(session.getSourceLanguage()).append("\n");
        metadata.append("Target Language: ").append(session.getTargetLanguage()).append("\n");
        metadata.append("Created: ").append(session.getCreatedAt()).append("\n");
        metadata.append("\n");

        // Add batch processing info
        if (sessionData.containsKey("total_words")) {
            metadata.append("Total Words Processed: ").append(sessionData.get("total_words")).append("\n");
        }

        // Add audio file count
        if (sessionData.containsKey("audio_files") && sessionData.get("audio_files") instanceof List) {
            int audioCount = ((List<?>) sessionData.get("audio_files")).size();
            metadata.append("Audio Files: ").append(audioCount).append("\n");
        }

        metadata.append("\n");

        // Add word-level data if available
        if (sessionData.containsKey("words") && sessionData.get("words") instanceof List) {
            metadata.append("Word Details\n");
            metadata.append("============\n\n");

            List<?> words = (List<?>) sessionData.get("words");
            int index = 1;
            for (Object wordObj : words) {
                if (wordObj instanceof Map) {
                    Map<?, ?> wordData = (Map<?, ?>) wordObj;
                    metadata.append(index++).append(". ");
                    metadata.append(wordData.get("source_word")).append("\n");

                    if (wordData.containsKey("translations")) {
                        metadata.append("   Translations: ").append(wordData.get("translations")).append("\n");
                    }
                    if (wordData.containsKey("mnemonic_keyword")) {
                        metadata.append("   Mnemonic Keyword: ").append(wordData.get("mnemonic_keyword")).append("\n");
                    }
                    if (wordData.containsKey("mnemonic_sentence")) {
                        metadata.append("   Mnemonic: ").append(wordData.get("mnemonic_sentence")).append("\n");
                    }
                    if (wordData.containsKey("source_audio_file")) {
                        metadata.append("   Source Audio: ").append(wordData.get("source_audio_file")).append("\n");
                    }
                    if (wordData.containsKey("target_audio_files")) {
                        metadata.append("   Target Audio: ").append(wordData.get("target_audio_files")).append("\n");
                    }
                    if (wordData.containsKey("image_file")) {
                        metadata.append("   Image: ").append(wordData.get("image_file")).append("\n");
                    }
                    metadata.append("\n");
                }
            }
        }

        // Legacy: Add single word mnemonic data if available
        if (sessionData.containsKey("mnemonic_keyword")) {
            metadata.append("Mnemonic Keyword: ").append(sessionData.get("mnemonic_keyword")).append("\n");
        }
        if (sessionData.containsKey("mnemonic_sentence")) {
            metadata.append("Mnemonic Sentence: ").append(sessionData.get("mnemonic_sentence")).append("\n");
        }
        if (sessionData.containsKey("image_prompt")) {
            metadata.append("\nImage Prompt:\n").append(sessionData.get("image_prompt")).append("\n");
        }

        metadata.append("\n");
        metadata.append("Usage Instructions\n");
        metadata.append("==================\n");
        metadata.append("This ZIP contains all generated assets (audio + images) for your translations.\n");
        metadata.append("Import the audio and image files into Anki along with your flashcards.\n");
        metadata.append("\nAvailable Anki Placeholders:\n");
        metadata.append("  [source-audio] - Audio file for source word\n");
        metadata.append("  [target-audio] - Audio file for translation\n");
        metadata.append("  [sentence-source-audio] - Audio file for sentence\n");
        metadata.append("  [image] - Generated mnemonic image\n");
        metadata.append("  [mnemonic_keyword] - Keyword used in mnemonic\n");
        metadata.append("  [mnemonic_sentence] - Full mnemonic sentence\n");

        Files.writeString(metadataPath, metadata.toString());
        return metadataPath;
    }

    /**
     * Resolve audio file path - handles both full paths and filenames
     *
     * @param audioFile Audio file path or filename
     * @return Full path to the audio file
     */
    private Path resolveAudioPath(String audioFile) {
        if (audioFile == null || audioFile.trim().isEmpty()) {
            return null;
        }

        Path path = Paths.get(audioFile);

        // If it's already an absolute path, use it directly
        if (path.isAbsolute()) {
            return path;
        }

        // If it looks like a relative path with directories, try it as-is first
        if (audioFile.contains("/") || audioFile.contains("\\")) {
            if (Files.exists(path)) {
                return path;
            }
        }

        // Otherwise, assume it's just a filename and prepend the audio output directory
        Path audioPath = Paths.get(audioOutputDirectory, audioFile);
        return audioPath;
    }
}
