package co.uk.vturbo.sbus.model;

import lombok.Value;


@Value
public class Message {
    private final String routingKey;
    private final Object body;
}
