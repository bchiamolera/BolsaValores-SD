package org.furb.bolsavalores.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Representa informações básicas sobre um nó participante do cluster.
 *
 * <p>Esta classe é utilizada para troca de informações entre processos
 * durante o processo de eleição e monitoramento do sistema distribuído.</p>
 */
@Data
@AllArgsConstructor
public class NodeInfo {
    /**
     * Endereço do nó no formato "host:porta".
     * <p>Exemplo: {@code localhost:8082}</p>
     */
    private String address;

    /**
     * Momento em que o nó foi iniciado.
     * <p>Representado como timestamp UNIX em milissegundos.</p>
     * <p>Usado para comparar antiguidade entre os processos,
     * permitindo determinar qual nó é mais "velho" durante o algoritmo de eleição.</p>
     */
    private long startTime;
}
