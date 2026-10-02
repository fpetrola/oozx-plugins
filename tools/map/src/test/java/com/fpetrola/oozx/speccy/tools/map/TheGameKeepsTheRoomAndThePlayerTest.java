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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * The map's parts measured over a whole game of Jet Set Willy, driven as the window drives them,
 * against what the game itself keeps: whether the room found in memory is the one at 0x8420 and
 * tells the rooms apart, whether the player found is Willy at 0x85D3, which side each room was
 * left by, and how the ways between rooms come out on the map. Numbers to read, not yet bars to
 * clear.
 */
class TheGameKeepsTheRoomAndThePlayerTest {

  private static final String[] ROW_KEYS = {"CAPS Z X C V", "A S D F G", "Q W E R T", "1 2 3 4 5", "0 9 8 7 6", "P O I U Y", "ENT L K J H", "SPC SYM M N B"};

  @Test
  @Timeout(value = 15, unit = TimeUnit.MINUTES)
  void theRoomThePlayerAndTheWaysBetweenRoomsAreTheGamesOwn() {
    RzxSession session = JetSetWilly.open();
    Speccy speccy = session.getSpeccy();
    InputsSeen inputs = new InputsSeen(speccy);
    RoomVariable roomVariable = new RoomVariable(speccy.memory::peek);
    PlayerVariable playerVariable = new PlayerVariable(speccy.memory::peek);
    Rooms rooms = new Rooms();
    List<Integer> arrivedIn = new ArrayList<>();
    List<int[]> seenAs = new ArrayList<>();
    Map<String, int[]> sides = new TreeMap<>();
    int frames = 0, roomWas = -1, roomBefore = -1, changedAt = -1000, leftRoom = -1;
    PlayerVariable.Side leftBy = null;
    String stateAtLeaving = "";
    try {
      for (; frames < 200_000 && session.playFrame(); frames++) {
        byte[] screen = JetSetWilly.screenOf(speccy);
        long keys = inputs.frame();
        int room = JetSetWilly.room(speccy);
        if (room != roomWas && roomWas >= 0) {
          roomBefore = roomWas;
          changedAt = frames;
        }
        roomWas = room;
        boolean arrived = roomVariable.frame(screen);
        if (roomVariable.leaving()) {
          leftBy = playerVariable.sideOfLeaving();
          stateAtLeaving = playerVariable.state();
          leftRoom = frames - changedAt <= 30 ? roomBefore : room;
        }
        if (arrived) {
          rooms.arrive(roomVariable.survivors(), roomVariable.memoryOnArrival(), screen, leftBy);
          arrivedIn.add(room);
          seenAs.add(new int[]{rooms.current().number, room});
          if (leftRoom >= 0 && leftRoom != room) {
            String truth = JetSetWilly.sideFrom(speccy, leftRoom, room);
            sides.computeIfAbsent(truth, k -> new int[2])[truth.equals(String.valueOf(leftBy)) ? 0 : 1]++;
            if (leftBy != null && !truth.equals(leftBy.toString())) System.out.printf("  left %d for %d at frame %d: said %s, game says %s; %s%n", leftRoom, room, frames, leftBy, truth, stateAtLeaving);
          }
          leftRoom = -1;
        }
        playerVariable.frame(keys);
      }
    } finally {
      inputs.off();
      session.release();
    }

    List<Integer> survivors = roomVariable.survivors();
    System.out.printf("ROOM VARIABLE: %d survivors, 0x8420 %s; %d arrivals in %d rooms of the game%n", survivors.size(),
        survivors.contains(JetSetWilly.ROOM) ? "among them" : "GONE", arrivedIn.size(), new HashSet<>(arrivedIn).size());
    PlayerVariable.Found player = playerVariable.player();
    System.out.printf("PLAYER: %s%n", player == null ? "not found" : String.format("0x%04X, %s wide, up %s down %s, 0x85D3 %s",
        player.address(), playerVariable.wide() ? "16 bits" : "8 bits", keyName(player.up()), keyName(player.down()), player.address() == JetSetWilly.WILLY ? "indeed" : "NOT IT"));
    System.out.print("SIDES OF LEAVING, asked as the next room starts being drawn:");
    sides.forEach((side, tally) -> System.out.printf(" %s right %d wrong %d;", side, tally[0], tally[1]));
    System.out.println();

    Map<Integer, Map<Integer, Integer>> gameRoomsOf = new TreeMap<>();
    for (int[] seen : seenAs) gameRoomsOf.computeIfAbsent(rooms.numberNow(seen[0]), r -> new HashMap<>()).merge(seen[1], 1, Integer::sum);
    gameRoomsOf.forEach((ours, theirs) -> { if (theirs.size() > 1) System.out.printf("  our room %d was the game's %s%n", ours, theirs); });
    Map<Integer, List<Integer>> oursOf = new TreeMap<>();
    gameRoomsOf.forEach((ours, theirs) -> theirs.keySet().forEach(game -> oursOf.computeIfAbsent(game, g -> new ArrayList<>()).add(ours)));
    oursOf.forEach((game, ours) -> { if (ours.size() > 1) System.out.printf("  the game's room %d was our %s%n", game, ours); });
    Map<Integer, Integer> gameRoomOf = new HashMap<>();
    gameRoomsOf.forEach((ours, theirs) -> gameRoomOf.put(ours, theirs.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey()));
    long doubled = gameRoomOf.values().size() - new HashSet<>(gameRoomOf.values()).size();
    int right = 0, wrong = 0, unvoted = 0, notAWay = 0;
    for (Rooms.Room one : rooms.rooms()) {
      for (int number : one.leadsTo) {
        Rooms.Room other = rooms.rooms().stream().filter(room -> room.number == number).findFirst().orElse(null);
        if (other == null || !gameRoomOf.containsKey(one.number) || !gameRoomOf.containsKey(number)) continue;
        String truth = JetSetWilly.sideFrom(speccy, gameRoomOf.get(one.number), gameRoomOf.get(number));
        PlayerVariable.Side voted = rooms.sideOf(one, other);
        if (truth.equals("none")) notAWay++;
        else if (voted == null) unvoted++;
        else if (truth.equals(voted.toString())) right++;
        else {
          wrong++;
          System.out.printf("  voted %s where the game says %s: %d -> %d%n", voted, truth, gameRoomOf.get(one.number), gameRoomOf.get(number));
        }
      }
    }
    System.out.printf("MAP: %d rooms (%d twice for one of the game's), ways voted right %d, wrong %d, unvoted %d, not a way of the game %d%n",
        rooms.rooms().size(), doubled, right, wrong, unvoted, notAWay);
  }

  private static String keyName(int key) {
    if (key >= InputsSeen.KEMPSTON) return "Kempston bit " + (key - InputsSeen.KEMPSTON);
    return ROW_KEYS[key / 5].split(" ")[key % 5];
  }
}
