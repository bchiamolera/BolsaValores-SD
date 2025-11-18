package org.furb.bolsavalores.model;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Representa uma ação da bolsa de valores armazenada no MongoDB.
 *
 * <p>Esta classe modela os dados retornados pela API da Brapi,
 * normalizando apenas as informações essenciais para persistência.</p>
 *
 * <p>A coleção MongoDB utilizada é <b>acoes</b>.</p>
 */
@Data
@Getter
@Setter
@Document(collection="acoes")
public class Acao {
    /**
     * Identificador único da ação no MongoDB.
     * Gerado automaticamente pelo banco ao salvar o documento.
     */
    @Id
    private String id;

    /**
     * Código da ação (ex: PETR4, VALE3).
     */
    private String symbol;

    /**
     * Nome curto da empresa ou ativo.
     */
    private String shortName;

    /**
     * Nome completo da empresa ou ativo.
     */
    private String longName;

    /**
     * Preço atual de mercado da ação no momento da consulta.
     */
    private double regularMarketPrice;

    /**
     * Timestamp exato da cotação retornada pela Brapi.
     * Representado em formato UTC via {@link Instant}.
     */
    private Instant regularMarketTime;
}
