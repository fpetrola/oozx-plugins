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
 * A second game, with nothing changed for it and nothing known of it: Dynamite Dan, forty-eight
 * screens eight by six wrapped round into a cylinder, with teleports between far screens and a
 * lift up the middle. The game keeps its screen number at 0xCFCA, which gives the truth to hold
 * every vote against, as 0x8420 does for Jet Set Willy; the map is drawn at
 * target/dynamite-dan-map.png to be looked at beside https://maps.speccy.cz/maps/DynamiteDan1.png.
 */
class DynamiteDanIsMappedTest {

  static final File RECORDING = new File(System.getenv("HOME"), "detodo/spectrum/dynamitedan/dynamitedan.rzx");
  static final int SCREEN = 0xCFCA, ACROSS = 8;

  /** The map's column and row of a screen number: 48 minus the number is eight rows of columns counted from one, the eighth column being the next row's first. */
  static int[] cellOf(int screen) {
    int counted = 48 - screen - 1;
    return new int[]{(counted % ACROSS + 1) % ACROSS, counted / ACROSS};
  }

  /** The side one screen lies on from another, going round the cylinder; null when they are not next to each other. */
  static PlayerVariable.Side sideFrom(int from, int to) {
    int[] a = cellOf(from), b = cellOf(to);
    int across = Math.floorMod(b[0] - a[0], ACROSS), down = b[1] - a[1];
    if (down == 0 && across == 1) return PlayerVariable.Side.RIGHT;
    if (down == 0 && across == ACROSS - 1) return PlayerVariable.Side.LEFT;
    if (across == 0 && down == 1) return PlayerVariable.Side.DOWN;
    if (across == 0 && down == -1) return PlayerVariable.Side.UP;
    return null;
  }

  @Test
  @Timeout(value = 20, unit = TimeUnit.MINUTES)
  void itsScreensComeOutOnAGridAndEveryWayVotedIsTheGames() throws Exception {
    Map<String, int[]> sides = new TreeMap<>();
    Map<Integer, Set<Integer>> screensOfRoom = new HashMap<>(), roomsOfScreen = new HashMap<>();
    Mapping.Pass pass = Mapping.twice(RECORDING, new Mapping.Watching() {
    }, new Mapping.Watching() {
      int screenBefore = -1, roomBefore = -1, screenLeft = -1;
      PlayerVariable.Side truth;

      @Override
      public void arrived(Speccy speccy, int frames, Rooms rooms, PlayerVariable player, RoomVariable roomVariable, PlayerVariable.Side voted) {
        int screen = speccy.memory.peek(SCREEN), room = rooms.current().number;
        screensOfRoom.computeIfAbsent(room, r -> new HashSet<>()).add(screen);
        roomsOfScreen.computeIfAbsent(screen, s -> new HashSet<>()).add(room);
        truth = null;
        screenLeft = -1;
        if (roomBefore >= 0 && room != roomBefore) {
          truth = sideFrom(screenBefore, screen);
          screenLeft = screenBefore;
        }
        screenBefore = screen;
        roomBefore = room;
        if (screenLeft >= 0) tally(truth, voted, 1);
      }

      @Override
      public void wayToldLate(PlayerVariable.Side side) {
        if (screenLeft < 0) return;
        tally(truth, null, -1);
        tally(truth, side, 1);
      }

      private void tally(PlayerVariable.Side truth, PlayerVariable.Side voted, int by) {
        sides.computeIfAbsent(truth == null ? "no way" : truth.toString(), s -> new int[2])[voted == truth ? 0 : 1] += by;
        if (by > 0 && voted != truth) System.out.printf("  screen %d -> %d: %s, voted %s%n", screenLeft, screenBefore, truth == null ? "not a way" : "the game says " + truth, voted);
      }
    });
    System.out.print("  sides of leaving:");
    sides.forEach((side, tally) -> System.out.printf(" %s right %d wrong %d;", side, tally[0], tally[1]));
    System.out.println();
    screensOfRoom.forEach((room, screens) -> { if (screens.size() > 1) System.out.printf("  our room %d was the game's screens %s%n", room, screens); });
    roomsOfScreen.forEach((screen, rooms) -> { if (rooms.size() > 1) System.out.printf("  the game's screen %d was our rooms %s%n", screen, rooms); });
    System.out.printf("MAP: %d screens of the game seen, %d rooms on the map, %d of %d voted ways not drawn as voted%n",
        roomsOfScreen.size(), pass.rooms().rooms().size(), Mapping.undrawn(pass.rooms()), Mapping.votedWays(pass.rooms()));
    Mapping.draw(pass.rooms(), new File("target/dynamite-dan-map.png"));
  }
}
