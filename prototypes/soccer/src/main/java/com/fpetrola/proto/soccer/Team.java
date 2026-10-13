package com.fpetrola.proto.soccer;

import com.badlogic.gdx.graphics.Color;

import java.util.ArrayList;
import java.util.List;

final class Team {
    /** 4-4-2 mirando hacia el arco rival; el primero es el arquero. */
    private static final float[][] FORMATION = {
            {-1f, 0f},
            {-0.65f, -0.6f}, {-0.7f, -0.2f}, {-0.7f, 0.2f}, {-0.65f, 0.6f},
            {-0.35f, -0.65f}, {-0.4f, -0.2f}, {-0.4f, 0.2f}, {-0.35f, 0.65f},
            {-0.1f, -0.15f}, {-0.12f, 0.2f},
    };

    final String name;
    final Color shirt, shorts;
    /** +1 ataca hacia x positivo, -1 hacia x negativo. */
    final int side;
    final boolean human;
    final List<Player> players = new ArrayList<>();
    int goals;

    Team(String name, Color shirt, Color shorts, int side, boolean human) {
        this.name = name;
        this.shirt = shirt;
        this.shorts = shorts;
        this.side = side;
        this.human = human;
        for (int i = 0; i < FORMATION.length; i++)
            players.add(new Player(this, i + 1, FORMATION[i][0], FORMATION[i][1] * side, i == 0));
    }

    /** Distancia "hacia adelante" medida en el sentido en que ataca este equipo. */
    float toA(float x) {
        return x * side;
    }

    float toX(float a) {
        return a * side;
    }

    float attackGoalX() {
        return side * Match.HALF_L;
    }

    float ownGoalX() {
        return -side * Match.HALF_L;
    }
}
