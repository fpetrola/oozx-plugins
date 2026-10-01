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
import com.fpetrola.oozx.rzx.RzxSession;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

/**
 * The map of Jet Set Willy from a whole game recorded, against the game's own answer: it keeps
 * the room it is in at 0x8420, and each room keeps the rooms to its left, right, above and below
 * at 0xE9 to 0xEC of its 256 bytes from 0xC000. Says how many ways were told right; it is a
 * measurement, not yet a bar to clear.
 */
class JetSetWillyIsMappedTest {

  static final File RECORDING = new File(System.getenv("HOME"), "detodo/spectrum/jsw/jsw-full.rzx");
  static final int ROOM = 0x8420, ROOMS = 0xC000, EXITS = 0xE9;

  @Test
  @org.junit.jupiter.api.Timeout(value = 15, unit = java.util.concurrent.TimeUnit.MINUTES)
  void theWaysBetweenItsRoomsAreTheGamesOwn() {
    Assumptions.assumeTrue(RECORDING.isFile(), "needs " + RECORDING);
    RzxSession session = RzxSession.open(RECORDING);
    Speccy speccy = session.getSpeccy();
    speccy.picture.active = false;
    speccy.sound.output.enabled = false;
    speccy.sound.setCard(new com.fpetrola.oozx.speccy.modules.sound.SilentSoundDevice());
    Rooms rooms = new Rooms();
    java.util.Map<Integer, byte[]> firstScreens = new java.util.TreeMap<>();
    int was = -1, right = 0, wrong = 0, notAWay = 0, willyX = -1, willyY = -1;
    java.util.Map<Integer, java.util.Map<Integer, Integer>> seenAs = new java.util.HashMap<>();
    java.util.Map<Integer, Integer> mapped = new java.util.HashMap<>();
    try {
      for (int frame = 0; frame < 200_000 && session.playFrame(); frame++) {
        byte[] screen = new byte[Rooms.SCREEN];
        for (int offset = 0; offset < screen.length; offset++) screen[offset] = (byte) speccy.memory.peek(0x4000 + offset);
        rooms.frame(screen);
        int inGame = speccy.memory.peek(ROOM) & 0xff;
        if (!firstScreens.containsKey(inGame) && frame % 50 == 0) firstScreens.put(inGame, screen);
        int room = speccy.memory.peek(ROOM) & 0xff;
        Rooms.Room current = rooms.current();
        if (current != null) seenAs.computeIfAbsent(current.number, n -> new java.util.HashMap<>()).merge(room, 1, Integer::sum);
        if (room != was && was >= 0) {
          String said = rooms.lastWayOut();
          String truth = sideIn(speccy, was, room);
          if (Boolean.getBoolean("map.trace")) System.out.printf("frame %d: room %d -> %d, the table says %s, willy was at column %d row %d, map says %s%n", frame, was, room, truth, willyX, willyY, said);
        }
        was = room;
        int at = (speccy.memory.peek(0x85D3) & 0xff) | (speccy.memory.peek(0x85D4) & 0xff) << 8;
        if (room == was) {
          willyX = at & 31;
          willyY = (at - 0x5C00) >> 5;
        }
      }
    } finally {
      session.release();
    }
    seenAs.forEach((ours, theirs) -> mapped.put(ours, theirs.entrySet().stream().max(java.util.Map.Entry.comparingByValue()).orElseThrow().getKey()));
    System.out.printf("ROOMS OF THE MAP TAKEN FOR THE SAME ROOM OF THE GAME: %d%n", mapped.size() - new java.util.HashSet<>(mapped.values()).size());
    for (Rooms.Room one : rooms.rooms()) {
      for (int number : one.leadsTo) {
        Rooms.Room other = rooms.rooms().get(number);
        if (!mapped.containsKey(one.number) || !mapped.containsKey(other.number)) continue;
        String truth = sideIn(speccy, mapped.get(one.number), mapped.get(other.number));
        Rooms.Side voted = rooms.sideOf(one, other);
        String drawn = voted == null ? "unvoted" : voted.name();
        if (truth.equals("none")) notAWay++;
        else if (truth.equals(drawn)) right++;
        else {
          wrong++;
          System.out.printf("voted %s where the game says %s: %d -> %d%n", drawn, truth, mapped.get(one.number), mapped.get(other.number));
        }
      }
    }
    double most = 0, sum = 0; int pairs = 0, over = 0;
    List<byte[]> seen = new java.util.ArrayList<>(firstScreens.values());
    for (int i = 0; i < seen.size(); i++) for (int j = i + 1; j < seen.size(); j++) {
      double a = Rooms.alike(seen.get(i), seen.get(j));
      most = Math.max(most, a); sum += a; pairs++; if (a >= Rooms.SAME) over++;
    }
    int[] fewest = new int[seen.size() * seen.size()]; int k = 0;
    Rooms.Room blank = new Rooms.Room(0, null, 0, 0);
    java.util.List<Integer> diffs = new java.util.ArrayList<>();
    for (int i = 0; i < seen.size(); i++) for (int j = i + 1; j < seen.size(); j++) {
      Rooms.Room r = new Rooms.Room(0, seen.get(i), 0, 0);
      diffs.add(Rooms.differingCells(r, seen.get(j)));
    }
    java.util.Collections.sort(diffs);
    System.out.println("CELLS DIFFERING BETWEEN TWO ROOMS, fewest: " + diffs.subList(0, 12));
    System.out.printf("DIFFERENT ROOMS OF THE GAME %d: alike on average %.3f, at most %.3f, %d pairs of %d over SAME%n", seen.size(), sum / pairs, most, over, pairs);
    System.out.printf("ROOMS %d, ways voted right %d, wrong %d, not a way of the game %d%n", rooms.rooms().size(), right, wrong, notAWay);
  }

  private static String sideIn(Speccy speccy, int from, int to) {
    String[] sides = {"LEFT", "RIGHT", "UP", "DOWN"};
    for (int side = 0; side < 4; side++) {
      if ((speccy.memory.peek(ROOMS + from * 256 + EXITS + side) & 0xff) == to) return sides[side];
    }
    return "none";
  }
}
