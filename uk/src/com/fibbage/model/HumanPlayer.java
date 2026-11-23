package com.fibbage.model;

/**
 * Demonstrates OOP Pillar 3: Inheritance.
 * Represents a human player connected via client GUI socket.
 */
public class HumanPlayer extends Player {
    private final String clientId;

    public HumanPlayer(String name, String clientId) {
        super(name);
        this.clientId = clientId;
    }

    public String getClientId() {
        return clientId;
    }

    @Override
    public boolean isAI() {
        return false;
    }
}

