package org.furb.bolsavalores.model;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
public class StockDataResponse {
    private String ticker;
    private String name;
    private double price;
    private String last_trade_time;
}
