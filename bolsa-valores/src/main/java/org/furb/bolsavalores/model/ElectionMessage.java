package org.furb.bolsavalores.model;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@Data
@Getter
@Setter
public class ElectionMessage implements Serializable {
    public enum Type { ELECTION, OK, COORDINATOR }

    private String electionId;
    private Type type;
    private String senderPort;
    private long senderStartTime;
    private String payload;
}
