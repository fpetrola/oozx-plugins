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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * The map's parts measured over a whole game of Jet Set Willy, driven as the window drives them,
 * against what the game itself keeps. Twice over the same game: first finding the room and the
 * player in memory as it goes, then again from the start knowing them, which is what a map can do
 * with a recording. The second time is held to the game's own table of ways between rooms, as a
 * map laid out from that table, and both are drawn to target/ to be looked at beside a known map.
 */
class TheGameKeepsTheRoomAndThePlayerTest {

  private static final String[] ROW_KEYS = {"CAPS Z X C V", "A S D F G", "Q W E R T", "1 2 3 4 5", "0 9 8 7 6", "P O I U Y", "ENT L K J H", "SPC SYM M N B"};

  /** What one pass over the game left behind. */
  private record Pass(RoomVariable roomVariable, PlayerVariable playerVariable, Rooms rooms, List<int[]> seenAs, Map<Integer, byte[]> screens) {
  }

  @Test
  @Timeout(value = 20, unit = TimeUnit.MINUTES)
  void theRoomThePlayerAndTheWaysBetweenRoomsAreTheGamesOwn() throws Exception {
    RzxSession session = JetSetWilly.open();
    Pass first = play(session, new RoomVariable(session.getSpeccy().memory::peek), new PlayerVariable(session.getSpeccy().memory::peek), "FINDING OUT");
    List<Integer> survivors = first.roomVariable.survivors();
    System.out.printf("  room variable: %d survivors, 0x8420 %s%n", survivors.size(), survivors.contains(JetSetWilly.ROOM) ? "among them" : "GONE");
    PlayerVariable.Found player = first.playerVariable.player();
    System.out.printf("  player: %s%n", player == null ? "not found" : String.format("0x%04X, %s wide, up %s down %s, 0x85D3 %s",
        player.address(), first.playerVariable.wide() ? "16 bits" : "8 bits", keyName(player.up()), keyName(player.down()), player.address() == JetSetWilly.WILLY ? "indeed" : "NOT IT"));

    session = JetSetWilly.open();
    PlayerVariable knowing = new PlayerVariable(session.getSpeccy().memory::peek);
    knowing.learnedFrom(first.playerVariable);
    Pass second = play(session, new RoomVariable(session.getSpeccy().memory::peek, survivors), knowing, "KNOWING");
    System.out.println("  first changers: " + second.roomVariable.firstChangers());
    compareWithTheGamesTable(session.getSpeccy(), second);
  }

  private Pass play(RzxSession session, RoomVariable roomVariable, PlayerVariable playerVariable, String label) {
    Speccy speccy = session.getSpeccy();
    InputsSeen inputs = new InputsSeen(speccy);
    Rooms rooms = new Rooms();
    List<int[]> seenAs = new ArrayList<>();
    Map<Integer, byte[]> screens = new HashMap<>();
    Map<String, int[]> sides = new TreeMap<>();
    int frames = 0, roomWas = -1, roomBefore = -1, changedAt = -1000, leftRoom = -1, arrivals = 0;
    PlayerVariable.Side leftBy = null;
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
          leftRoom = frames - changedAt <= 30 ? roomBefore : room;
        }
        if (arrived) {
          arrivals++;
          rooms.arrive(roomVariable.survivors(), roomVariable.memoryOnArrival(), screen, leftBy);
          seenAs.add(new int[]{rooms.current().number, room});
          screens.putIfAbsent(room, screen);
          if (leftRoom >= 0 && leftRoom != room) {
            String truth = JetSetWilly.sideFrom(speccy, leftRoom, room);
            sides.computeIfAbsent(truth, k -> new int[2])[truth.equals(String.valueOf(leftBy)) ? 0 : 1]++;
          }
          leftRoom = -1;
        }
        playerVariable.frame(keys);
        if (playerVariable.movedNow()) rooms.played();
      }
    } finally {
      inputs.off();
      session.release();
    }
    System.out.printf("%s: %d arrivals in %d rooms of the game, %d rooms on the map%n", label, arrivals, screens.size(), rooms.rooms().size());
    System.out.print("  sides of leaving:");
    sides.forEach((side, tally) -> System.out.printf(" %s right %d wrong %d;", side, tally[0], tally[1]));
    System.out.println();
    return new Pass(roomVariable, playerVariable, rooms, seenAs, screens);
  }

  /** The map against the game's own table of ways: the rooms as the game numbers them, the ways voted, and where each room was put. */
  private void compareWithTheGamesTable(Speccy speccy, Pass pass) throws Exception {
    Map<Integer, Map<Integer, Integer>> gameRoomsOf = new TreeMap<>();
    java.util.Set<Integer> standing = pass.rooms.rooms().stream().map(room -> room.number).collect(java.util.stream.Collectors.toSet());
    for (int[] seen : pass.seenAs) {
      int ours = pass.rooms.numberNow(seen[0]);
      if (standing.contains(ours)) gameRoomsOf.computeIfAbsent(ours, r -> new HashMap<>()).merge(seen[1], 1, Integer::sum);
    }
    Map<Integer, Integer> gameRoomOf = new HashMap<>();
    gameRoomsOf.forEach((ours, theirs) -> gameRoomOf.put(ours, theirs.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey()));
    gameRoomsOf.forEach((ours, theirs) -> { if (theirs.size() > 1) System.out.printf("  our room %d was the game's %s%n", ours, theirs); });
    List<Integer> identity = pass.roomVariable.survivors();
    Map<Integer, List<Rooms.Room>> oursOfGame = new TreeMap<>();
    pass.rooms.rooms().forEach(room -> { if (gameRoomOf.containsKey(room.number)) oursOfGame.computeIfAbsent(gameRoomOf.get(room.number), g -> new ArrayList<>()).add(room); });
    oursOfGame.forEach((game, ours) -> {
      if (ours.size() < 2) return;
      StringBuilder differing = new StringBuilder();
      int count = 0;
      for (int address : identity) if (ours.get(0).memory[address] != ours.get(1).memory[address] && count++ < 12) differing.append(String.format(" 0x%04X(%d/%d)", address, ours.get(0).memory[address] & 0xff, ours.get(1).memory[address] & 0xff));
      System.out.printf("  the game's room %d is our %s; of the %d identity bytes %d differ:%s%n", game, ours.stream().map(r -> r.number).toList(), identity.size(), count, differing);
    });
    long doubled = gameRoomOf.size() - new HashSet<>(gameRoomOf.values()).size();

    int right = 0, wrong = 0, unvoted = 0, notAWay = 0;
    for (Rooms.Room one : pass.rooms.rooms()) {
      for (int number : one.leadsTo) {
        Rooms.Room other = pass.rooms.rooms().stream().filter(room -> room.number == number).findFirst().orElse(null);
        if (other == null || !gameRoomOf.containsKey(one.number) || !gameRoomOf.containsKey(number)) continue;
        String truth = JetSetWilly.sideFrom(speccy, gameRoomOf.get(one.number), gameRoomOf.get(number));
        PlayerVariable.Side voted = pass.rooms.sideOf(one, other);
        if (truth.equals("none")) {
          notAWay++;
          if (one.number < number) System.out.printf("  not a way of the game: %d -> %d, voted %s%n", gameRoomOf.get(one.number), gameRoomOf.get(number), voted);
        }
        else if (voted == null) unvoted++;
        else if (truth.equals(voted.toString())) right++;
        else {
          wrong++;
          System.out.printf("  voted %s where the game says %s: %d -> %d%n", voted, truth, gameRoomOf.get(one.number), gameRoomOf.get(number));
        }
      }
    }
    System.out.printf("MAP: %d rooms (%d twice for one of the game's), ways voted right %d, wrong %d, unvoted %d, not a way of the game %d%n",
        pass.rooms.rooms().size(), doubled, right, wrong, unvoted, notAWay);

    // The game's table, laid out from the bathroom by the same rule a map uses: each room beside the one it is reached from.
    Map<Integer, int[]> table = new HashMap<>();
    Deque<Integer> toVisit = new ArrayDeque<>(List.of(33));
    table.put(33, new int[]{0, 0});
    int clashes = 0;
    while (!toVisit.isEmpty()) {
      int room = toVisit.pop();
      for (int side = 0; side < 4; side++) {
        int next = speccy.memory.peek(JetSetWilly.ROOMS + room * 256 + JetSetWilly.EXITS + side) & 0xff;
        if (next == room || next >= 61) continue;
        int[] at = {table.get(room)[0] + (side == 1 ? 1 : side == 0 ? -1 : 0), table.get(room)[1] + (side == 3 ? 1 : side == 2 ? -1 : 0)};
        int[] known = table.get(next);
        if (known == null) {
          table.put(next, at);
          toVisit.add(next);
        } else if (known[0] != at[0] || known[1] != at[1]) clashes++;
      }
    }
    // Lined up by the shift most rooms agree on, since neither map has a corner to start from.
    Map<String, Integer> shifts = new HashMap<>();
    for (Rooms.Room room : pass.rooms.rooms()) {
      Integer game = gameRoomOf.get(room.number);
      if (game != null && table.containsKey(game)) shifts.merge((room.x - table.get(game)[0]) + "," + (room.y - table.get(game)[1]), 1, Integer::sum);
    }
    String[] shift = shifts.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey().split(",");
    int shiftX = Integer.parseInt(shift[0]), shiftY = Integer.parseInt(shift[1]);
    int placedAlike = 0, placedElsewhere = 0;
    StringBuilder elsewhere = new StringBuilder();
    for (Rooms.Room room : pass.rooms.rooms()) {
      Integer game = gameRoomOf.get(room.number);
      if (game == null || !table.containsKey(game)) continue;
      int[] truth = table.get(game);
      if (room.x - shiftX == truth[0] && room.y - shiftY == truth[1]) placedAlike++;
      else {
        placedElsewhere++;
        elsewhere.append(String.format(" %d at %d,%d not %d,%d;", game, room.x - shiftX, room.y - shiftY, truth[0], truth[1]));
      }
    }
    System.out.printf("LAYOUT: the game's table reaches %d rooms from the bathroom with %d ways disagreeing on where a room lies; ours puts %d rooms where the table does and %d elsewhere:%s%n",
        table.size(), clashes, placedAlike, placedElsewhere, elsewhere);
    draw(pass.rooms.rooms().stream().collect(java.util.stream.Collectors.toMap(room -> gameRoomOf.getOrDefault(room.number, -1), room -> new int[]{room.x, room.y}, (a, b) -> a)),
        pass.screens, new File("target/jsw-map-ours.png"));
    draw(table, pass.screens, new File("target/jsw-map-table.png"));
  }

  /** Every room drawn on its square, numbered as the game numbers it. */
  private static void draw(Map<Integer, int[]> where, Map<Integer, byte[]> screens, File into) throws Exception {
    int minX = where.values().stream().mapToInt(at -> at[0]).min().orElse(0), minY = where.values().stream().mapToInt(at -> at[1]).min().orElse(0);
    int maxX = where.values().stream().mapToInt(at -> at[0]).max().orElse(0), maxY = where.values().stream().mapToInt(at -> at[1]).max().orElse(0);
    BufferedImage map = new BufferedImage((maxX - minX + 1) * 256, (maxY - minY + 1) * 192, BufferedImage.TYPE_INT_RGB);
    Graphics2D pen = map.createGraphics();
    pen.setColor(Color.DARK_GRAY);
    pen.fillRect(0, 0, map.getWidth(), map.getHeight());
    where.forEach((game, at) -> {
      int x = (at[0] - minX) * 256, y = (at[1] - minY) * 192;
      byte[] screen = screens.get(game);
      if (screen != null) pen.drawImage(Screens.picture(screen, null), x, y, null);
      pen.setColor(Color.YELLOW);
      pen.drawString(String.valueOf(game), x + 4, y + 14);
    });
    pen.dispose();
    into.getParentFile().mkdirs();
    javax.imageio.ImageIO.write(map, "png", into);
  }

  private static String keyName(int key) {
    if (key >= InputsSeen.KEMPSTON) return "Kempston bit " + (key - InputsSeen.KEMPSTON);
    return ROW_KEYS[key / 5].split(" ")[key % 5];
  }
}
