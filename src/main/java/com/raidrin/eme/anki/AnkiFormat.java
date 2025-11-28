package com.raidrin.eme.anki;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnkiFormat {
    private String name;
    private List<CardItem> frontCardItems;
    private List<CardItem> backCardItems;
}
