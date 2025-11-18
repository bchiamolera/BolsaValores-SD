package org.furb.bolsavalores.model;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * Representa uma mensagem trocada entre processos durante o algoritmo
 * de eleição distribuída. Esta mensagem circula via RabbitMQ entre os
 * nós do cluster para coordenar a escolha do líder.
 *
 * <p>A mensagem transporta informações essenciais como:
 * <ul>
 *   <li>ID único da eleição</li>
 *   <li>Tipo da mensagem (ELECTION, OK, COORDINATOR)</li>
 *   <li>Identificação da porta do remetente</li>
 *   <li>Timestamp de início do processo remetente</li>
 *   <li>Payload opcional</li>
 * </ul>
 * </p>
 *
 * <p>Implementa {@link java.io.Serializable} para permitir transporte
 * seguro via mensagens AMQP.</p>
 */
@Data
@Getter
@Setter
public class ElectionMessage implements Serializable {

    /**
     * Tipos possíveis de mensagens utilizadas no protocolo de eleição.
     *
     * <ul>
     *   <li><b>ELECTION</b>: enviada por um nó iniciando uma eleição.</li>
     *   <li><b>OK</b>: enviada por um nó mais “velho” (iniciado antes) para
     *       informar que participará da eleição.</li>
     *   <li><b>COORDINATOR</b>: broadcast enviado pelo processo vencedor, informando
     *       que ele se tornou o líder atual.</li>
     * </ul>
     */
    public enum Type { ELECTION, OK, COORDINATOR }

    /**
     * Identificador único da eleição.
     *
     * <p>Permite que cada rodada de eleição seja distinguida das demais,
     * especialmente importante quando múltiplas eleições ocorrem simultaneamente.</p>
     */
    private String electionId;

    /**
     * Tipo da mensagem enviada (ELECTION, OK ou COORDINATOR).
     *
     * <p>Define o comportamento que o receptor deve executar ao processar
     * esta mensagem.</p>
     */
    private Type type;

    /**
     * Porta do processo remetente.
     *
     * <p>Utilizada para construir a rota de resposta e identificar
     * qual processo enviou a mensagem.</p>
     *
     * <p>Exemplo: {@code "8082"}</p>
     */
    private String senderPort;

    /**
     * Timestamp (em milissegundos) indicando o momento em que
     * o processo remetente foi iniciado.
     *
     * <p>Usado pelo algoritmo de eleição para determinar qual processo
     * é mais antigo, garantindo a escolha do líder “mais velho”.</p>
     */
    private long senderStartTime;

    /**
     * Campo opcional para transportar dados adicionais.
     *
     * <p>Não é utilizado pelo algoritmo principal, mas permite
     * extensões futuras ou inclusão de metadados.</p>
     */
    private String payload;
}
