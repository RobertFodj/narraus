package com.narraus.domain;

/** Suorittaa push-lähetyksen pyyntösäikeen ulkopuolella, jottei käyttäjän toiminto odota ulkoista palvelua. */
@FunctionalInterface
public interface PushDispatcher {
    void dispatch(Runnable task);
}
