package org.furb.bolsavalores.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class NodeInfo {
    private String address;
    private long startTime;
}
