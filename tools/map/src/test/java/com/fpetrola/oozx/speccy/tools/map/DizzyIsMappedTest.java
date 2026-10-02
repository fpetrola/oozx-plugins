/*
 * Copyright (c) 2023-2026 Fernando Damian Petrola
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.fpetrola.oozx.speccy.tools.map;

import com.fpetrola.oozx.Speccy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * A third game, with nothing changed for it: Dizzy (1987), which changes the screen first and
 * only then puts the egg at the far door, keeps its screen number at 0xD1EC, one more per screen
 * to the right, and reads its keys every other frame. The recordings to hand are short, three
 * minutes of walking right: this one is the only one with the egg going both ways, which the
 * player search needs. Each way voted is held against the screen number, and the map is drawn at
 * target/dizzy-map.png to be looked at beside https://maps.speccy.cz/maps/Dizzy1.png.
 */
class DizzyIsMappedTest {

  static final File RECORDING = new File(System.getenv("HOME"), "detodo/spectrum/dizzy_r1-ext2.rzx");
  static final int SCREEN = 0xD1EC;

  /** The side one screen lies on from another, as far as the recordings go: along the row only. */
  static PlayerVariable.Side sideFrom(int from, int to) {
    if (from == 0 || to == 0) return null;
    return to - from == 1 ? PlayerVariable.Side.RIGHT : from - to == 1 ? PlayerVariable.Side.LEFT : null;
  }

  @Test
  @Timeout(value = 20, unit = TimeUnit.MINUTES)
  void everyWayVotedIsTheGames() throws Exception {
    Map<String, int[]> sides = new TreeMap<>();
    Map<Integer, Set<Integer>> roomsOfScreen = new HashMap<>();
    Mapping.Pass pass = Mapping.twice(RECORDING, new Mapping.Watching() {
    }, new Mapping.Watching() {
      int screenBefore = -1, roomBefore = -1;
      PlayerVariable.Side truth;
      boolean counted;

      @Override
      public void arrived(Speccy speccy, int frames, Rooms rooms, PlayerVariable player, RoomVariable roomVariable, PlayerVariable.Side voted) {
        int screen = speccy.memory.peek(SCREEN), room = rooms.current().number;
        roomsOfScreen.computeIfAbsent(screen, s -> new HashSet<>()).add(room);
        counted = roomBefore >= 0 && room != roomBefore && screen != screenBefore;
        if (counted) {
          truth = sideFrom(screenBefore, screen);
          tally(screenBefore, screen, truth, voted, 1);
        }
        screenBefore = screen;
        roomBefore = room;
      }

      @Override
      public void wayToldLate(PlayerVariable.Side side) {
        if (!counted) return;
        tally(-1, -1, truth, null, -1);
        tally(-1, screenBefore, truth, side, 1);
      }

      private void tally(int from, int to, PlayerVariable.Side truth, PlayerVariable.Side voted, int by) {
        sides.computeIfAbsent(truth == null ? "no way" : truth.toString(), s -> new int[2])[voted == truth ? 0 : 1] += by;
        if (by > 0 && voted != truth) System.out.printf("  screen %s -> %d: %s, voted %s%s%n", from < 0 ? "same" : String.valueOf(from), to, truth == null ? "not a way along the row" : "the game says " + truth, voted, from < 0 ? " (late)" : "");
      }
    });
    System.out.print("  sides of leaving:");
    sides.forEach((side, tally) -> System.out.printf(" %s right %d wrong %d;", side, tally[0], tally[1]));
    System.out.println();
    roomsOfScreen.forEach((screen, rooms) -> { if (rooms.size() > 1) System.out.printf("  the game's screen %d was our rooms %s%n", screen, rooms); });
    System.out.printf("MAP: %d screens of the game seen, %d rooms on the map, %d of %d voted ways not drawn as voted%n",
        roomsOfScreen.size(), pass.rooms().rooms().size(), Mapping.undrawn(pass.rooms()), Mapping.votedWays(pass.rooms()));
    Mapping.draw(pass.rooms(), new File("target/dizzy-map.png"));
  }
}
