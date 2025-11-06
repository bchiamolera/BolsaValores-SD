package org.furb.bolsavalores.model;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Getter
@Setter
@Document(collection="acoes")
public class Acao {
    @Id
    private String id;

    private String symbol;
    private String shortName;
    private String longName;
    private double regularMarketPrice;
    private String regularMarketTime;
}
