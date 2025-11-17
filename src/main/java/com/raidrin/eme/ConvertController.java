package com.raidrin.eme;

import com.google.cloud.texttospeech.v1.SsmlVoiceGender;
import com.raidrin.eme.anki.AnkiNoteCreatorService;
import com.raidrin.eme.codec.Codec;
import com.raidrin.eme.translator.LanguageTranslationCodes;
import com.raidrin.eme.audio.LanguageAudioCodes;
import com.raidrin.eme.audio.TextToAudioGenerator;
import com.raidrin.eme.translator.TranslationService;
import com.raidrin.eme.sentence.SentenceGenerationService;
import com.raidrin.eme.sentence.SentenceData;
import com.raidrin.eme.storage.entity.CharacterGuideEntity;
import com.raidrin.eme.storage.entity.TranslationSessionEntity;
import com.raidrin.eme.storage.entity.WordEntity;
import com.raidrin.eme.storage.service.CharacterGuideService;
import com.raidrin.eme.storage.service.TranslationSessionService;
import com.raidrin.eme.storage.service.WordService;
import com.raidrin.eme.image.AsyncImageGenerationService;
import com.raidrin.eme.image.ImageStyle;
import com.raidrin.eme.mnemonic.MnemonicGenerationService;
import com.raidrin.eme.mnemonic.MnemonicGenerationService.MnemonicData;
import com.raidrin.eme.util.FileNameSanitizer;
import com.raidrin.eme.session.SessionOrchestrationService;
import com.raidrin.eme.audio.LanguageAudioCodes;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Controller
@RequiredArgsConstructor
public class ConvertController {
    private final AnkiNoteCreatorService ankiNoteCreatorService;
    private final TextToAudioGenerator textToAudioGenerator;
    private final TranslationService translationService;
    private final SentenceGenerationService sentenceGenerationService;
    private final TranslationSessionService translationSessionService;
    private final AsyncImageGenerationService asyncImageGenerationService;
    private final MnemonicGenerationService mnemonicGenerationService;
    private final SessionOrchestrationService sessionOrchestrationService;
    private final WordService wordService;
    private final CharacterGuideService characterGuideService;

    @GetMapping("/")
    public String index() {
        return "index";
    }
    
    @GetMapping("/storage")
    public String storageViewer() {
        return "storage-viewer";
    }

    @PostMapping("/generate")
    public String generate(
            @RequestParam(name = "text", required = false) String userInput,
            @RequestParam(required = false) String lang,
            @RequestParam(required = false) String front,
            @RequestParam(required = false) String back,
            @RequestParam(required = false) String deck,
            @RequestParam(required = false) Boolean translation,
            @RequestParam(name = "override-translation", required = false) Boolean overrideTranslation,
            @RequestParam(name = "source-audio", required = false) Boolean sourceAudio,
            @RequestParam(name = "target-audio", required = false) Boolean targetAudio,
            @RequestParam(name = "target-lang", required = false) String targetLang,
            @RequestParam(required = false) Boolean anki,
            @RequestParam(name = "sentence-generation", required = false) Boolean sentenceGeneration,
            @RequestParam(name = "image-generation", required = false) Boolean imageGeneration,
            @RequestParam(name = "image-style", required = false) String imageStyle
    ) {
        translation = translation != null && translation;
        overrideTranslation = overrideTranslation != null && overrideTranslation;
        sourceAudio = sourceAudio != null && sourceAudio;
        targetAudio = targetAudio != null && targetAudio;
        anki = anki != null && anki;
        sentenceGeneration = sentenceGeneration != null && sentenceGeneration;
        imageGeneration = imageGeneration != null && imageGeneration;

        // Parse source words
        List<String> sourceWords = Arrays.stream(userInput.split("\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();

        if (sourceWords.isEmpty()) {
            return "redirect:/?error=no-words";
        }

        // Create a single session for all words
        String sessionWord = sourceWords.size() == 1
                ? sourceWords.get(0)
                : sourceWords.size() + " words";

        TranslationSessionEntity session = translationSessionService.createSession(
                sessionWord,
                lang != null ? lang : "en",
                targetLang != null ? targetLang : "en",
                imageGeneration,
                (sourceAudio || targetAudio),
                sentenceGeneration,
                anki,
                overrideTranslation,
                deck,
                front,
                back
        );

        System.out.println("Created session " + session.getId() + " for " + sourceWords.size() + " words");

        // Build processing request
        SessionOrchestrationService.BatchProcessingRequest request =
                new SessionOrchestrationService.BatchProcessingRequest();

        request.setSourceWords(sourceWords);
        request.setSourceLanguage(lang != null ? lang : "en");
        request.setTargetLanguage(targetLang != null ? targetLang : "en");
        request.setSourceLanguageCode(getTranslationCode(lang != null ? lang : "en").getCode());
        request.setTargetLanguageCode(getTranslationCode(targetLang != null ? targetLang : "en").getCode());

        // Audio configuration
        request.setEnableSourceAudio(sourceAudio);
        request.setEnableTargetAudio(targetAudio);

        LangAudioOption sourceLangAudio = getLangAudioOption(lang != null ? lang : "en");
        request.setSourceAudioLanguageCode(sourceLangAudio.languageCode);
        request.setSourceVoiceGender(sourceLangAudio.voiceGender);
        request.setSourceVoiceName(sourceLangAudio.voiceName);

        if (translation) {
            LangAudioOption targetLangAudio = getLangAudioOption(targetLang != null ? targetLang : "en");
            request.setTargetAudioLanguageCode(targetLangAudio.languageCode);
            request.setTargetVoiceGender(targetLangAudio.voiceGender);
            request.setTargetVoiceName(targetLangAudio.voiceName);
        }

        // Feature flags
        request.setEnableTranslation(translation);
        request.setEnableSentenceGeneration(sentenceGeneration);
        request.setEnableImageGeneration(imageGeneration);
        request.setOverrideTranslation(overrideTranslation);

        // Image style configuration
        ImageStyle imageStyleEnum = ImageStyle.fromString(imageStyle);
        request.setImageStyle(imageStyleEnum);

        // Start async processing
        sessionOrchestrationService.processTranslationBatchAsync(session.getId(), request);

        // Return immediately with redirect to session view
        System.out.println("Started async processing for session " + session.getId());
        return "redirect:/sessions/" + session.getId() + "?message=processing-started";
    }

    @PostMapping("/generate/review")
    public String generateReview(
            @RequestParam(name = "text", required = false) String userInput,
            @RequestParam(required = false) String lang,
            @RequestParam(required = false) String front,
            @RequestParam(required = false) String back,
            @RequestParam(required = false) String deck,
            @RequestParam(required = false) Boolean translation,
            @RequestParam(name = "override-translation", required = false) Boolean overrideTranslation,
            @RequestParam(name = "source-audio", required = false) Boolean sourceAudio,
            @RequestParam(name = "target-audio", required = false) Boolean targetAudio,
            @RequestParam(name = "target-lang", required = false) String targetLang,
            @RequestParam(required = false) Boolean anki,
            @RequestParam(name = "sentence-generation", required = false) Boolean sentenceGeneration,
            @RequestParam(name = "image-generation", required = false) Boolean imageGeneration,
            @RequestParam(name = "image-style", required = false) String imageStyle,
            Model model
    ) {
        translation = translation != null && translation;
        overrideTranslation = overrideTranslation != null && overrideTranslation;
        sourceAudio = sourceAudio != null && sourceAudio;
        targetAudio = targetAudio != null && targetAudio;
        anki = anki != null && anki;
        sentenceGeneration = sentenceGeneration != null && sentenceGeneration;
        imageGeneration = imageGeneration != null && imageGeneration;

        // Parse source words
        List<String> sourceWords = Arrays.stream(userInput.split("\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();

        if (sourceWords.isEmpty()) {
            return "redirect:/?error=no-words";
        }

        // Fetch existing word data from database
        String sourceLang = lang != null ? lang : "en";
        String targetLanguage = targetLang != null ? targetLang : "en";

        List<ReviewWordData> reviewWords = sourceWords.stream().map(word -> {
            ReviewWordData reviewWord = new ReviewWordData();
            reviewWord.setWord(word);
            reviewWord.setSourceLanguage(sourceLang);
            reviewWord.setTargetLanguage(targetLanguage);

            String transliteration = null;

            // Try to fetch existing data from database
            Optional<WordEntity> existingWord = wordService.findWord(word, sourceLang, targetLanguage);
            if (existingWord.isPresent()) {
                WordEntity wordEntity = existingWord.get();
                reviewWord.setMnemonicKeyword(wordEntity.getMnemonicKeyword());

                // Deserialize translations if they exist
                if (wordEntity.getTranslation() != null && !wordEntity.getTranslation().isEmpty()) {
                    Set<String> translations = wordService.deserializeTranslations(wordEntity.getTranslation());
                    reviewWord.setTranslation(String.join(", ", translations));
                }

                // Get transliteration
                transliteration = wordEntity.getSourceTransliteration();
            }

            // If no transliteration from database, try to get it from translation service
            if (transliteration == null || transliteration.trim().isEmpty()) {
                try {
                    transliteration = translationService.getTransliteration(word, sourceLang);
                } catch (Exception e) {
                    System.err.println("Could not fetch transliteration for " + word + ": " + e.getMessage());
                }
            }

            reviewWord.setTransliteration(transliteration);

            // Look up character from character guide using transliteration
            if (transliteration != null && !transliteration.trim().isEmpty()) {
                Optional<CharacterGuideEntity> character = characterGuideService.findMatchingCharacterForWord(
                        word, sourceLang, transliteration
                );
                if (character.isPresent()) {
                    reviewWord.setCharacterName(character.get().getCharacterName());
                    reviewWord.setCharacterContext(character.get().getCharacterContext());
                    System.out.println("Found character for '" + word + "': " + character.get().getCharacterName() +
                            " from " + character.get().getCharacterContext());
                } else {
                    System.out.println("No character found for '" + word + "' with transliteration: " + transliteration);
                }
            }

            return reviewWord;
        }).collect(Collectors.toList());

        // Add data to model for the review page
        model.addAttribute("reviewWords", reviewWords);
        model.addAttribute("sourceLang", sourceLang);
        model.addAttribute("sourceLanguageName", getLanguageName(sourceLang));
        model.addAttribute("targetLang", targetLanguage);
        model.addAttribute("targetLanguageName", getLanguageName(targetLanguage));

        // Pass through all the form parameters so they can be submitted in the confirm step
        model.addAttribute("front", front);
        model.addAttribute("back", back);
        model.addAttribute("deck", deck);
        model.addAttribute("translation", translation);
        model.addAttribute("overrideTranslation", overrideTranslation);
        model.addAttribute("sourceAudio", sourceAudio);
        model.addAttribute("targetAudio", targetAudio);
        model.addAttribute("anki", anki);
        model.addAttribute("sentenceGeneration", sentenceGeneration);
        model.addAttribute("imageGeneration", imageGeneration);
        model.addAttribute("imageStyle", imageStyle != null ? imageStyle : "REALISTIC_CINEMATIC");

        return "review";
    }

    @PostMapping("/generate/confirm")
    public String generateConfirm(
            @RequestParam(name = "words", required = false) List<String> words,
            @RequestParam(name = "mnemonicKeywords", required = false) List<String> mnemonicKeywords,
            @RequestParam(name = "translations", required = false) List<String> translations,
            @RequestParam(name = "sourceLanguages", required = false) List<String> sourceLanguages,
            @RequestParam(required = false) String targetLang,
            @RequestParam(required = false) String front,
            @RequestParam(required = false) String back,
            @RequestParam(required = false) String deck,
            @RequestParam(required = false) Boolean translation,
            @RequestParam(name = "override-translation", required = false) Boolean overrideTranslation,
            @RequestParam(name = "source-audio", required = false) Boolean sourceAudio,
            @RequestParam(name = "target-audio", required = false) Boolean targetAudio,
            @RequestParam(required = false) Boolean anki,
            @RequestParam(name = "sentence-generation", required = false) Boolean sentenceGeneration,
            @RequestParam(name = "image-generation", required = false) Boolean imageGeneration,
            @RequestParam(name = "image-style", required = false) String imageStyle
    ) {
        translation = translation != null && translation;
        overrideTranslation = overrideTranslation != null && overrideTranslation;
        sourceAudio = sourceAudio != null && sourceAudio;
        targetAudio = targetAudio != null && targetAudio;
        anki = anki != null && anki;
        sentenceGeneration = sentenceGeneration != null && sentenceGeneration;
        imageGeneration = imageGeneration != null && imageGeneration;

        if (words == null || words.isEmpty()) {
            return "redirect:/?error=no-words";
        }

        // Update word data in database based on user edits
        for (int i = 0; i < words.size(); i++) {
            String word = words.get(i);
            String sourceLang = sourceLanguages != null && i < sourceLanguages.size() ? sourceLanguages.get(i) : "en";
            String targetLanguage = targetLang != null ? targetLang : "en";
            String mnemonicKeyword = mnemonicKeywords != null && i < mnemonicKeywords.size() ? mnemonicKeywords.get(i) : null;
            String translationStr = translations != null && i < translations.size() ? translations.get(i) : null;

            // Fetch existing word to check if data was modified
            Optional<WordEntity> existingWordOpt = wordService.findWord(word, sourceLang, targetLanguage);

            boolean mnemonicChanged = false;
            boolean translationChanged = false;

            if (existingWordOpt.isPresent()) {
                WordEntity existingWord = existingWordOpt.get();

                // Check if mnemonic keyword was modified
                if (mnemonicKeyword != null && !mnemonicKeyword.trim().isEmpty()) {
                    String existingMnemonic = existingWord.getMnemonicKeyword();
                    if (!mnemonicKeyword.equals(existingMnemonic)) {
                        mnemonicChanged = true;
                    }
                }

                // Check if translation was modified
                if (translationStr != null && !translationStr.trim().isEmpty()) {
                    String existingTranslation = existingWord.getTranslation();
                    if (existingTranslation != null) {
                        Set<String> existingTranslations = wordService.deserializeTranslations(existingTranslation);
                        Set<String> newTranslations = Arrays.stream(translationStr.split(","))
                                .map(String::trim)
                                .filter(s -> !s.isEmpty())
                                .collect(Collectors.toSet());
                        if (!existingTranslations.equals(newTranslations)) {
                            translationChanged = true;
                        }
                    } else {
                        translationChanged = true;
                    }
                }
            } else {
                // New word - any provided data counts as manual entry
                mnemonicChanged = mnemonicKeyword != null && !mnemonicKeyword.trim().isEmpty();
                translationChanged = translationStr != null && !translationStr.trim().isEmpty();
            }

            // Update word in database with appropriate timestamp updates
            if (mnemonicChanged && mnemonicKeyword != null && !mnemonicKeyword.trim().isEmpty()) {
                wordService.updateMnemonicKeywordWithManualOverride(word, sourceLang, targetLanguage, mnemonicKeyword, null);
            }

            if (translationChanged && translationStr != null && !translationStr.trim().isEmpty()) {
                Set<String> translationSet = Arrays.stream(translationStr.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toSet());
                if (!translationSet.isEmpty()) {
                    wordService.updateTranslationWithManualOverride(word, sourceLang, targetLanguage, translationSet);
                }
            }
        }

        // Now proceed with the original /generate logic
        String sessionWord = words.size() == 1 ? words.get(0) : words.size() + " words";

        // Use the first source language (they should all be the same from the review page)
        String lang = sourceLanguages != null && !sourceLanguages.isEmpty() ? sourceLanguages.get(0) : "en";

        TranslationSessionEntity session = translationSessionService.createSession(
                sessionWord,
                lang,
                targetLang != null ? targetLang : "en",
                imageGeneration,
                (sourceAudio || targetAudio),
                sentenceGeneration,
                anki,
                overrideTranslation,
                deck,
                front,
                back
        );

        System.out.println("Created session " + session.getId() + " for " + words.size() + " words");

        // Build processing request
        SessionOrchestrationService.BatchProcessingRequest request =
                new SessionOrchestrationService.BatchProcessingRequest();

        request.setSourceWords(words);
        request.setSourceLanguage(lang);
        request.setTargetLanguage(targetLang != null ? targetLang : "en");
        request.setSourceLanguageCode(getTranslationCode(lang).getCode());
        request.setTargetLanguageCode(getTranslationCode(targetLang != null ? targetLang : "en").getCode());

        // Audio configuration
        request.setEnableSourceAudio(sourceAudio);
        request.setEnableTargetAudio(targetAudio);

        LangAudioOption sourceLangAudio = getLangAudioOption(lang);
        request.setSourceAudioLanguageCode(sourceLangAudio.languageCode);
        request.setSourceVoiceGender(sourceLangAudio.voiceGender);
        request.setSourceVoiceName(sourceLangAudio.voiceName);

        if (translation) {
            LangAudioOption targetLangAudio = getLangAudioOption(targetLang != null ? targetLang : "en");
            request.setTargetAudioLanguageCode(targetLangAudio.languageCode);
            request.setTargetVoiceGender(targetLangAudio.voiceGender);
            request.setTargetVoiceName(targetLangAudio.voiceName);
        }

        // Feature flags
        request.setEnableTranslation(translation);
        request.setEnableSentenceGeneration(sentenceGeneration);
        request.setEnableImageGeneration(imageGeneration);
        request.setOverrideTranslation(overrideTranslation);

        // Image style configuration
        ImageStyle imageStyleEnum = ImageStyle.fromString(imageStyle);
        request.setImageStyle(imageStyleEnum);

        // Start async processing
        sessionOrchestrationService.processTranslationBatchAsync(session.getId(), request);

        // Return immediately with redirect to session view
        System.out.println("Started async processing for session " + session.getId());
        return "redirect:/sessions/" + session.getId() + "?message=processing-started";
    }

    // Keep old synchronous generate method for backward compatibility (if needed)
    @PostMapping("/generate-sync")
    public void generateSync(
            @RequestParam(name = "text", required = false) String userInput,
            @RequestParam(required = false) String lang,
            @RequestParam(required = false) String front,
            @RequestParam(required = false) String back,
            @RequestParam(required = false) String deck,
            @RequestParam(required = false) Boolean translation,
            @RequestParam(name = "source-audio", required = false) Boolean sourceAudio,
            @RequestParam(name = "target-audio", required = false) Boolean targetAudio,
            @RequestParam(name = "target-lang", required = false) String targetLang,
            @RequestParam(required = false) Boolean anki,
            @RequestParam(name = "sentence-generation", required = false) Boolean sentenceGeneration,
            @RequestParam(name = "image-generation", required = false) Boolean imageGeneration,
            HttpServletResponse response
    ) throws IOException {
        translation = translation != null && translation;
        sourceAudio = sourceAudio != null && sourceAudio;
        targetAudio = targetAudio != null && targetAudio;
        anki = anki != null && anki;
        sentenceGeneration = sentenceGeneration != null && sentenceGeneration;
        imageGeneration = imageGeneration != null && imageGeneration;

        // Initialize the EmeData map
        String[] sourceTextList = Arrays.stream(userInput.split("\n"))
                .map(String::trim)
                .distinct().toArray(String[]::new);
        Set<EmeData> emeDataList = new HashSet<>();
        Map<String, byte[]> audioFileMap = new HashMap<>();

        for (String sourceText : sourceTextList) {
            EmeData emeData = new EmeData();

            // Add source text
            emeData.sourceText = sourceText;

            // Populate EmeData

            // Generate Source Audio
            if (sourceAudio) {
                emeData.sourceAudioFileName = Codec.encodeForAudioFileName(sourceText);
                if(!audioFileMap.containsKey(emeData.sourceAudioFileName)) {
                    byte[] audio = generateAudio(getLangAudioOption(lang), sourceText);
                    audioFileMap.put(emeData.sourceAudioFileName, audio);
                }
            }

            // Generate Translation
            if (translation) {
                final LanguageTranslationCodes sourceLangCode = getTranslationCode(lang);
                final LanguageTranslationCodes targetLangCode = getTranslationCode(targetLang);
                com.raidrin.eme.translator.TranslationData translationData = translationService.translateText(sourceText, sourceLangCode.getCode(), targetLangCode.getCode());
                emeData.translatedTextList = translationData.getTranslations();
            }

            // Generate Target Audio
            if (translation && targetAudio) {
                for (String translatedText : emeData.translatedTextList) {
                    String audioFileName = Codec.encodeForAudioFileName(translatedText);
                    emeData.translatedAudioList.add(audioFileName);
                    emeData.translatedTextAudioFileMap.put(translatedText, audioFileName);
                    if(!audioFileMap.containsKey(audioFileName)) {
                        byte[] audio = generateAudio(getLangAudioOption(targetLang), translatedText);
                        audioFileMap.put(audioFileName, audio);
                    }
                }
            }

            // Generate Sentences
            if (sentenceGeneration) {
                String sourceLangCode = lang;
                String targetLangCode = translation ? targetLang : "en";
                emeData.sentenceData = sentenceGenerationService.generateSentence(sourceText, sourceLangCode, targetLangCode);

                // Generate audio for sentence source (in source language - e.g., Hindi sentence)
                if (emeData.sentenceData != null && emeData.sentenceData.getSourceLanguageSentence() != null) {
                    String sentenceSourceText = emeData.sentenceData.getSourceLanguageSentence();
                    emeData.sentenceSourceAudioFileName = Codec.encodeForAudioFileName(sentenceSourceText);

                    if (!audioFileMap.containsKey(emeData.sentenceSourceAudioFileName)) {
                        // Generate audio in the source language (e.g., Hindi) for the sentence
                        System.out.println("DEBUG: Generating audio for sentence source text: " + sentenceSourceText);
                        System.out.println("DEBUG: Using language code: " + lang);
                        LangAudioOption audioOption = getLangAudioOption(lang);
                        System.out.println("DEBUG: Audio option - languageCode: " + audioOption.languageCode + ", voiceName: " + audioOption.voiceName);
                        byte[] sentenceAudio = generateAudio(audioOption, sentenceSourceText);
                        audioFileMap.put(emeData.sentenceSourceAudioFileName, sentenceAudio);
                    }
                }
            }

            // Generate Mnemonic Images (Async)
            if (imageGeneration && translation && !emeData.translatedTextList.isEmpty()) {
                // Get the first translation (primary translation)
                String primaryTranslation = emeData.translatedTextList.iterator().next();

                // Generate mnemonic synchronously to get the filename
                System.out.println("Generating mnemonic for: " + sourceText + " -> " + primaryTranslation);
                MnemonicData mnemonicData = mnemonicGenerationService.generateMnemonic(
                    sourceText, primaryTranslation, lang, targetLang
                );

                // Calculate the filename that will be used
                String imageFileName = FileNameSanitizer.fromMnemonicSentence(
                    mnemonicData.getMnemonicSentence(), "jpg"
                );

                // Store mnemonic data and filename in EmeData
                emeData.mnemonicKeyword = mnemonicData.getMnemonicKeyword();
                emeData.mnemonicSentence = mnemonicData.getMnemonicSentence();
                emeData.imageFileName = imageFileName;

                // Create a translation session for tracking
                TranslationSessionEntity session = translationSessionService.createSession(
                    sourceText, lang, targetLang, true, (sourceAudio || targetAudio)
                );

                // Store session ID for reference
                emeData.imageSessionId = session.getId();

                // Start async image generation with pre-generated mnemonic data and filename
                asyncImageGenerationService.generateImagesAsync(
                    session.getId(),
                    mnemonicData,
                    imageFileName
                );

                System.out.println("Started async image generation for: " + sourceText + " -> " + primaryTranslation + " (Session ID: " + session.getId() + ", Filename: " + imageFileName + ")");
            }

            // Generate Anki Cards
            if (anki) {
                emeData.ankiFront = ankiReplace(front.trim(), emeData);
                emeData.ankiBack = ankiReplace(back.trim(), emeData);
                ankiNoteCreatorService.addNote(deck, emeData.ankiFront, emeData.ankiBack);
            }

            emeDataList.add(emeData);

        }

        System.out.println("User input: " + userInput);
        System.out.println("Language: " + lang);
        System.out.println("Source audio: " + sourceAudio);
        System.out.println("Target audio: " + targetAudio);
        System.out.println("Translation: " + translation);
        System.out.println("Anki: " + anki);
        System.out.println("Front: " + front);
        System.out.println("Back: " + back);

        if (sourceAudio || targetAudio) {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream);

            audioFileMap.forEach((audioFileName, audio) -> {
                try {
                    zipOutputStream.putNextEntry(new ZipEntry(audioFileName + ".mp3"));
                    zipOutputStream.write(audio);
                    zipOutputStream.closeEntry();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to write to zip file", e);
                }
            });

            zipOutputStream.close();

            response.setContentType("application/zip");
            response.setHeader("Content-Disposition", "attachment; filename=\"audio.zip\"");
            response.getOutputStream().write(byteArrayOutputStream.toByteArray());
        } else {
            response.setContentType("text/plain");
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write("Success message");
        }
    }

    private String audioAnkiGenerator(String audioFileName) {
        return "[sound:" + audioFileName + ".mp3]";
    }

    private String ankiReplace(String text, EmeData emeData) {
        String updatedText = text
                .replace("[source-text]", emeData.sourceText)
                .replace("[source-audio]", audioAnkiGenerator(emeData.sourceAudioFileName));

        // Replace sentence generation placeholders
        if (emeData.sentenceData != null) {
            updatedText = updatedText
                .replace("[sentence-latin]", emeData.sentenceData.getTargetLanguageLatinCharacters())
                .replace("[sentence-target]", emeData.sentenceData.getTargetLanguageSentence())
                .replace("[sentence-transliteration]", emeData.sentenceData.getTargetLanguageTransliteration())
                .replace("[sentence-source]", emeData.sentenceData.getSourceLanguageSentence())
                .replace("[sentence-structure]", emeData.sentenceData.getSourceLanguageStructure())
                .replace("[sentence-source-audio]", emeData.sentenceSourceAudioFileName != null ? audioAnkiGenerator(emeData.sentenceSourceAudioFileName) : "");
        }

        // Replace mnemonic image placeholders
        // Note: Images are generated asynchronously, so these placeholders will be empty during card creation
        // Users will need to download images from the sessions page and manually add them
        if (emeData.imageFileName != null) {
            updatedText = updatedText.replace("[image]", "<img src=\"" + emeData.imageFileName + "\" />");
        } else if (emeData.imageSessionId != null) {
            // Image generation is enabled but not yet complete - add empty img tag as placeholder
            updatedText = updatedText.replace("[image]", "<img src=\"\" />");
        } else {
            updatedText = updatedText.replace("[image]", "");
        }

        if (emeData.mnemonicKeyword != null) {
            updatedText = updatedText.replace("[mnemonic_keyword]", emeData.mnemonicKeyword);
        } else {
            updatedText = updatedText.replace("[mnemonic_keyword]", "");
        }

        if (emeData.mnemonicSentence != null) {
            updatedText = updatedText.replace("[mnemonic_sentence]", emeData.mnemonicSentence);
        } else {
            updatedText = updatedText.replace("[mnemonic_sentence]", "");
        }

        boolean hasTargetAudio = text.contains("[target-audio]");
        if (hasTargetAudio) {
            updatedText = updatedText.replace("[target-audio]", "");
        }

        if (!emeData.translatedTextList.isEmpty()) {
            StringBuilder translatedTextListSb = new StringBuilder();
            translatedTextListSb.append("<ul>");
            emeData.translatedTextList.forEach(t -> {
                StringBuilder translationSb = new StringBuilder();
                translationSb.append("<span class='translated-text'>").append(t).append("</span>");
                if (hasTargetAudio && !emeData.translatedAudioList.isEmpty()) {
                    translationSb.append("<br />");
                    translationSb.append(audioAnkiGenerator(emeData.translatedTextAudioFileMap.get(t))).append(t).append("</span>");
                }

                translatedTextListSb.append("<li>").append(translationSb).append("</li>");
            });
            translatedTextListSb.append("</ul>");
            updatedText = updatedText.replace("[target-text]", translatedTextListSb.toString());
        }
        return updatedText;
    }

    private byte[] generateAudio(LangAudioOption langAudioOption, String text) {
        return textToAudioGenerator.generate(
                text,
                langAudioOption.languageCode,
                langAudioOption.voiceGender,
                langAudioOption.voiceName
        );
    }

    private LanguageTranslationCodes getTranslationCode(String lang) {
        switch (lang) {
            case "en" -> {
                return LanguageTranslationCodes.English;
            }
            case "es" -> {
                return LanguageTranslationCodes.Spanish;
            }
            case "fr", "cafr" -> {
                return LanguageTranslationCodes.French;
            }
            case "kr" -> {
                return LanguageTranslationCodes.Korean;
            }
            case "jp" -> {
                return LanguageTranslationCodes.Japanese;
            }
            case "hi" -> {
                return LanguageTranslationCodes.Hindi;
            }
            case "pa" -> {
                return LanguageTranslationCodes.Punjabi;
            }
            case "tl" -> {
                return LanguageTranslationCodes.Tagalog;
            }
            default -> throw new RuntimeException("Invalid language code");
        }
    }
    
    private String getLanguageName(String lang) {
        switch (lang) {
            case "en" -> {
                return "English";
            }
            case "es" -> {
                return "Spanish";
            }
            case "fr" -> {
                return "French";
            }
            case "cafr" -> {
                return "Canadian French";
            }
            case "kr" -> {
                return "Korean";
            }
            case "jp" -> {
                return "Japanese";
            }
            case "hi" -> {
                return "Hindi";
            }
            case "pa" -> {
                return "Punjabi";
            }
            case "tl" -> {
                return "Tagalog";
            }
            default -> {
                return "English";
            }
        }
    }

    /**
     * A single result of a translation/ audio generation.
     * Fields are populated based on the request parameters.
     */
    private static class EmeData {
        public String sourceText;
        public String sourceAudioFileName;
        public Set<String> translatedTextList = new HashSet<>();
        public Set<String> translatedAudioList = new HashSet<>();
        public Map<String, String> translatedTextAudioFileMap = new HashMap<>();
        public String ankiFront;
        public String ankiBack;
        public SentenceData sentenceData;
        public String sentenceSourceAudioFileName;

        // Mnemonic image generation fields
        public Long imageSessionId;
        public String mnemonicKeyword;
        public String mnemonicSentence;
        public String imageFileName;

        @Override
        public int hashCode() {
            return 1;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null) return false;
            if (this.getClass() != o.getClass()) return false;
            EmeData data = (EmeData) o;
            return sourceText.equals(data.sourceText);
        }
    }

    private static class LangAudioOption {
        public LanguageAudioCodes languageCode;
        public SsmlVoiceGender voiceGender;
        public String voiceName;
    }

    public static class ReviewWordData {
        private String word;
        private String sourceLanguage;
        private String targetLanguage;
        private String mnemonicKeyword;
        private String translation;
        private String transliteration;
        private String characterName;
        private String characterContext;

        public String getWord() {
            return word;
        }

        public void setWord(String word) {
            this.word = word;
        }

        public String getSourceLanguage() {
            return sourceLanguage;
        }

        public void setSourceLanguage(String sourceLanguage) {
            this.sourceLanguage = sourceLanguage;
        }

        public String getTargetLanguage() {
            return targetLanguage;
        }

        public void setTargetLanguage(String targetLanguage) {
            this.targetLanguage = targetLanguage;
        }

        public String getMnemonicKeyword() {
            return mnemonicKeyword;
        }

        public void setMnemonicKeyword(String mnemonicKeyword) {
            this.mnemonicKeyword = mnemonicKeyword;
        }

        public String getTranslation() {
            return translation;
        }

        public void setTranslation(String translation) {
            this.translation = translation;
        }

        public String getTransliteration() {
            return transliteration;
        }

        public void setTransliteration(String transliteration) {
            this.transliteration = transliteration;
        }

        public String getCharacterName() {
            return characterName;
        }

        public void setCharacterName(String characterName) {
            this.characterName = characterName;
        }

        public String getCharacterContext() {
            return characterContext;
        }

        public void setCharacterContext(String characterContext) {
            this.characterContext = characterContext;
        }
    }

    private LangAudioOption getLangAudioOption(String lang) {
        LangAudioOption langAudioOption = new LangAudioOption();
        switch (lang) {
            case "en" -> {
                langAudioOption.languageCode = LanguageAudioCodes.English;
                langAudioOption.voiceGender = SsmlVoiceGender.MALE;
                langAudioOption.voiceName = "en-US-Neural2-A";
                return langAudioOption;
            }
            case "es" -> {
                langAudioOption.languageCode = LanguageAudioCodes.Spanish;
                langAudioOption.voiceGender = SsmlVoiceGender.MALE;
                langAudioOption.voiceName = "es-US-Neural2-B";
                return langAudioOption;
            }
            case "fr" -> {
                langAudioOption.languageCode = LanguageAudioCodes.French;
                langAudioOption.voiceGender = SsmlVoiceGender.MALE;
                langAudioOption.voiceName = "fr-FR-Neural2-B";
                return langAudioOption;
            }
            case "cafr" -> {
                langAudioOption.languageCode = LanguageAudioCodes.CanadianFrench;
                langAudioOption.voiceGender = SsmlVoiceGender.FEMALE;
                langAudioOption.voiceName = "fr-CA-Neural2-A";
                return langAudioOption;
            }
            case "kr" -> {
                langAudioOption.languageCode = LanguageAudioCodes.Korean;
                langAudioOption.voiceGender = SsmlVoiceGender.FEMALE;
                langAudioOption.voiceName = "ko-KR-Standard-A";
                return langAudioOption;
            }
            case "jp" -> {
                langAudioOption.languageCode = LanguageAudioCodes.Japanese;
                langAudioOption.voiceGender = SsmlVoiceGender.MALE;
                langAudioOption.voiceName = "ja-JP-Neural2-C";
                return langAudioOption;
            }
            case "hi" -> {
                langAudioOption.languageCode = LanguageAudioCodes.Hindi;
                langAudioOption.voiceGender = SsmlVoiceGender.FEMALE;
                langAudioOption.voiceName = "hi-IN-Neural2-A";
                return langAudioOption;
            }
            case "pa" -> {
                langAudioOption.languageCode = LanguageAudioCodes.Punjabi;
                langAudioOption.voiceGender = SsmlVoiceGender.FEMALE;
                langAudioOption.voiceName = "pa-IN-Standard-A";
                return langAudioOption;
            }
            case "tl" -> {
                langAudioOption.languageCode = LanguageAudioCodes.Tagalog;
                langAudioOption.voiceGender = SsmlVoiceGender.FEMALE;
                langAudioOption.voiceName = "fil-PH-Standard-A";
                return langAudioOption;
            }
            default -> throw new RuntimeException("Invalid language code");
        }
    }

    @PostMapping("/g")
    public String gx() {
        return "generated";
    }
}
