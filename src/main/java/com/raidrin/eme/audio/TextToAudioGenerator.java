package com.raidrin.eme.audio;

import com.google.cloud.texttospeech.v1.*;
import org.springframework.stereotype.Component;

@Component
public class TextToAudioGenerator {
    public byte[] generate(String text, LanguageCodes languageCode, SsmlVoiceGender gender, String voiceName) {
        try {
            try (TextToSpeechClient textToSpeechClient = TextToSpeechClient.create()) {
                // Set the text input to be synthesized
                SynthesisInput input = SynthesisInput.newBuilder().setText(text).build();

                // Build the voice request, select the language code ("en-US") and the ssml voice gender
                // ("neutral")
                VoiceSelectionParams voice =
                        VoiceSelectionParams.newBuilder()
                                .setLanguageCode(languageCode.getCode())
                                .setName(voiceName)
                                .setSsmlGender(gender)
                                .build();


                // Select the type of audio file you want returned
                AudioConfig audioConfig =
                        AudioConfig.newBuilder().setAudioEncoding(AudioEncoding.MP3).build();

                // Perform the text-to-speech request on the text input with the selected voice parameters and
                // audio file type
                SynthesizeSpeechResponse response =
                        textToSpeechClient.synthesizeSpeech(input, voice, audioConfig);

                // Get the audio contents from the response
                return response.getAudioContent().toByteArray();
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format("Failed to generate audio for: %s", text),
                    e
            );
        }
    }
}
