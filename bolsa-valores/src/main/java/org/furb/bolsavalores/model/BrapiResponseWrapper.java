package org.furb.bolsavalores.model;

import lombok.Data;

import java.util.List;

@Data
public class BrapiResponseWrapper {
    private List<BrapiResponse> results;
}
