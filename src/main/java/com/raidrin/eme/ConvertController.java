package com.raidrin.eme;

import com.google.cloud.texttospeech.v1.SsmlVoiceGender;
import com.raidrin.eme.codec.Codec;
import com.raidrin.eme.translator.LanguageTranslationCodes;
import com.raidrin.eme.audio.LanguageAudioCodes;
import com.raidrin.eme.audio.TextToAudioGenerator;
import com.raidrin.eme.translator.TranslatorService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Controller
public class ConvertController {
    @Autowired
    TextToAudioGenerator textToAudioGenerator;

    @Autowired
    TranslatorService translatorService;

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @PostMapping("/generate")
    public void generate(
            @RequestParam(name = "text", required = false) String userInput,
            @RequestParam(required = false) String lang,
            @RequestParam(required = false) String front,
            @RequestParam(required = false) String back,
            @RequestParam(required = false) Boolean translation,
            @RequestParam(name = "source-audio", required = false) Boolean sourceAudio,
            @RequestParam(name = "target-audio", required = false) Boolean targetAudio,
            @RequestParam(required = false) String targetLang,
            @RequestParam(required = false) Boolean anki,
            HttpServletResponse response
    ) throws IOException {
        // Initialize the EmeData map
        String[] sourceTextList = Arrays.stream(userInput.split("\n"))
                .map(String::trim).toArray(String[]::new);
        Map<String, EmeData> emeDataMap = new HashMap<>();
        for (String sourceText : sourceTextList) {
            EmeData emeData = new EmeData();

            // Add source text
            emeData.sourceText = sourceText;

            // Populate EmeData

            // Generate Source Audio
            if (sourceAudio) {
                emeData.sourceAudioFileName = Codec.encode(sourceText);
                byte[] audio = generateAudio(getLangAudioOption(targetLang), sourceText);
                emeData.audioByteMap.put(emeData.sourceAudioFileName, audio);
            }

            // Generate Translation
            if (translation) {
                final LanguageTranslationCodes sourceLangCode = getTranslationCode(targetLang);
                emeData.translatedTextList = translatorService.translateText(sourceText, sourceLangCode.getCode());
            }

            // Generate Target Audio
            if (translation && targetAudio) {
                for (String translatedText : emeData.translatedTextList) {
                    String audioFileName = Codec.encode(translatedText);
                    emeData.translatedAudioList.add(audioFileName);
                    emeData.translatedTextAudioFileMap.put(translatedText, audioFileName);
                    byte[] audio = generateAudio(getLangAudioOption(targetLang), translatedText);
                    emeData.audioByteMap.put(audioFileName, audio);
                }
            }

            // Generate Anki Cards
            if (anki) {
                emeData.ankiFront = ankiReplace(front.trim(), emeData);
                emeData.ankiBack = ankiReplace(back.trim(), emeData);
            }

            emeDataMap.put(sourceText, emeData);

            if(sourceAudio || targetAudio) {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream);

                emeData.audioByteMap.forEach((audioFileName, audio) -> {
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
    }


    private String audioAnkiGenerator(String audioFileName) {
        return "[sound:" + audioFileName + ".mp3]";
    }

    private String ankiReplace(String text, EmeData emeData) {
        String updatedText = text
                .replace("[source-text]", emeData.sourceText)
                .replace("[source-audio]", emeData.sourceAudioFileName);

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

//    private void generateAudio(LangAudioOption langAudioOption, List<String> texts, ZipOutputStream zipOutputStream) {
//        if (langAudioOption == null) {
//            throw new RuntimeException("Invalid language code");
//        }
//        texts.forEach(textItem -> {
//            byte[] audio = textToAudioGenerator.generate(
//                    textItem.trim(),
//                    langAudioOption.languageCode,
//                    langAudioOption.voiceGender,
//                    langAudioOption.voiceName
//            );
//            try {
//                zipOutputStream.putNextEntry(new ZipEntry(textItem.trim() + ".mp3"));
//                zipOutputStream.write(audio);
//                zipOutputStream.closeEntry();
//            } catch (Exception e) {
//                throw new RuntimeException("Failed to write to zip file", e);
//            }
//        });
//    }

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
            default -> {
                throw new RuntimeException("Invalid language code");
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
        public List<String> translatedTextList = new ArrayList<>();
        public List<String> translatedAudioList = new ArrayList<>();
        public Map<String, String> translatedTextAudioFileMap = new HashMap<>();
        public Map<String, byte[]> audioByteMap = new HashMap<>();
        public String ankiFront;
        public String ankiBack;
    }

    private static class LangAudioOption {
        public LanguageAudioCodes languageCode;
        public SsmlVoiceGender voiceGender;
        public String voiceName;
    }

    private LangAudioOption getLangAudioOption(String lang) {
        LangAudioOption langAudioOption = new LangAudioOption();
        switch (lang) {
            case "en" -> {
                langAudioOption.languageCode = LanguageAudioCodes.English;
                langAudioOption.voiceGender = SsmlVoiceGender.MALE;
                langAudioOption.voiceName = "en-US-Neural2-A";
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
            default -> {
                throw new RuntimeException("Invalid language code");
            }
        }
    }

    @PostMapping("/g")
    public String gx() {
        return "generated";
    }
}
