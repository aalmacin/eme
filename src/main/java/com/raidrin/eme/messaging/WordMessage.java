package com.raidrin.eme.messaging;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WordMessage {
    private Long wordId;
    private String word;
    private String sourceLanguage;
    private String targetLanguage;
}
