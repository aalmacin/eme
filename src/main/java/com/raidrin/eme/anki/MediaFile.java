package com.raidrin.eme.anki;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class MediaFile {
    private String filename;
    private String path;
    private List<String> fields;
}
