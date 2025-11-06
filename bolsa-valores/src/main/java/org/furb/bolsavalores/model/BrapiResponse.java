package org.furb.bolsavalores.model;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
public class BrapiResponse {
    private String symbol;
    private String shortName;
    private String longName;
    private double regularMarketPrice;
    private String regularMarketTime;
}
