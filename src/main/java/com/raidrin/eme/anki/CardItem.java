package com.raidrin.eme.anki;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CardItem {
    private CardType cardType;
    private Integer order;
    private Boolean isToggled;
}
