package org.falmdev.anieventmanager.cinematics.model;

public enum CinematicState {
    /** La cinematica existe pero no está ni reproduciéndose ni grabándose. */
    IDLE,
    /** Un admin está grabando waypoints con el magic stick. */
    RECORDING,
    /** La cinematica se está reproduciendo para los jugadores. */
    PLAYING
}