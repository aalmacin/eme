package com.raidrin.eme;

import com.google.cloud.texttospeech.v1.SsmlVoiceGender;
import com.raidrin.eme.audio.LanguageCodes;
import com.raidrin.eme.audio.TextToAudioGenerator;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Controller
public class ConvertController {
    @Autowired
    TextToAudioGenerator textToAudioGenerator;

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @PostMapping("/generate")
    public void generate(
            @RequestParam(required = false) String text,
            @RequestParam(required = false) String lang,
            @RequestParam(name = "multi-mp3", required = false) Boolean multipleMP3,
            HttpServletResponse response
    ) throws IOException {
        final LanguageCodes languageCode;
        final SsmlVoiceGender voiceGender;
        final String voiceName;

        switch (lang) {
            case "es" -> {
                languageCode = LanguageCodes.Spanish;
                voiceGender = SsmlVoiceGender.MALE;
                voiceName = "es-US-Neural2-B";
            }
            case "fr" -> {
                languageCode = LanguageCodes.French;
                voiceGender = SsmlVoiceGender.MALE;
                voiceName = "fr-FR-Neural2-B";
            }
            case "cafr" -> {
                languageCode = LanguageCodes.CanadianFrench;
                voiceGender = SsmlVoiceGender.FEMALE;
                voiceName = "fr-CA-Neural2-A";
            }
            case "kr" -> {
                languageCode = LanguageCodes.Korean;
                voiceGender = SsmlVoiceGender.FEMALE;
                voiceName = "ko-KR-Standard-A";
            }
            case "jp" -> {
                languageCode = LanguageCodes.Japanese;
                voiceGender = SsmlVoiceGender.MALE;
                voiceName = "ja-JP-Neural2-C";
            }
            default -> {
                throw new RuntimeException("Invalid language code");
            }
        }
        System.out.println("lang: " + lang);
        System.out.println("languageCode: " + languageCode.getCode());
        System.out.println("gender: " + voiceGender.name());

        if (multipleMP3 == null) {
            multipleMP3 = false;
        }

        if (multipleMP3) {
            String[] textList = text.split("\n");
            List<String> texts = new ArrayList<>(
                    new HashSet<>(Arrays.stream(textList).map(String::trim).toList())
            );

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream);

            texts.forEach(textItem -> {
                byte[] audio = textToAudioGenerator.generate(
                        textItem.trim(),
                        languageCode,
                        voiceGender,
                        voiceName
                );
                try {
                    zipOutputStream.putNextEntry(new ZipEntry(textItem.trim() + ".mp3"));
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

            byte[] audio = textToAudioGenerator.generate(
                    text,
                    languageCode,
                    voiceGender,
                    voiceName
            );
            String mp3FileName = Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
            response.setContentType("audio/mpeg");
            response.setHeader("Content-Disposition", "attachment; filename=" + mp3FileName + ".mp3");

            OutputStream outputStream = response.getOutputStream();
            outputStream.write(audio);
            outputStream.flush();
            outputStream.close();
        }

    }

    @PostMapping("/g")
    public String gx() {
        return "generated";
    }
}
