package com.narraus.domain;

public enum EventStatus {
    UPCOMING("Tulossa"), ONGOING("Käynnissä"), ENDED("Päättynyt");

    private final String label;

    EventStatus(String label) { this.label = label; }

    public String label() { return label; }
}
