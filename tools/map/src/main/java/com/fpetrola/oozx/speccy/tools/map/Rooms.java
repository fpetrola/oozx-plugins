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

import com.fpetrola.oozx.speccy.tools.map.PlayerVariable.Side;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The rooms of a flip-screen game and how they lie: each known by the game's own memory for it,
 * as {@link RoomVariable} finds it, and each way between two voted for a side every time it is
 * taken, by where the player was when leaving, as {@link PlayerVariable} tells. The map is laid
 * out again from the votes after each arrival, so that a side told wrong once is outvoted by the
 * times after. Two rooms the memory can no longer tell apart, as what identifies a room narrows
 * down, become one. A room is only a room once the player has moved in it: a title, or a room
 * the game has not finished setting up, is let go of when the next one comes, way and all.
 */
public final class Rooms {

  /** One room: the screen it was last seen as, the memory it is known by, where it is on the map, and what it leads to. */
  public static final class Room {
    public final int number;
    public byte[] screen;
    final byte[] memory;
    public int x, y;
    public final Set<Integer> leadsTo = new HashSet<>();
    /** Not yet played in: nobody has moved here. */
    boolean provisional = true;
    /** Placed beside another by a voted way, and so left where it is; the rest wait in the strip below. */
    boolean anchored;

    Room(int number, byte[] screen, byte[] memory) {
      this.number = number;
      this.screen = screen;
      this.memory = memory;
    }
  }

  private final List<Room> rooms = new ArrayList<>();
  /** Votes for the side the second room of each pair lies on from the first, the lower number first. */
  private final Map<Long, int[]> votes = new HashMap<>();
  private int numbered;
  /** Which room each number folded into, for whoever remembers a room by the number it had. */
  private final Map<Integer, Integer> foldedInto = new HashMap<>();
  private Room current, cameFrom;
  private String lastWayOut = "";

  /** A room arrived at: known by these bytes of memory, looking like this, the last one left by that side. */
  public synchronized void arrive(List<Integer> identity, byte[] memory, byte[] screen, Side leftBy) {
    if (current != null && current.provisional) {
      rooms.remove(current);
      current = null;
    }
    Room known = rooms.stream().filter(room -> same(room.memory, memory, identity)).findFirst().orElse(null);
    if (known == null) {
      known = new Room(numbered++, screen.clone(), memory);
      rooms.add(known);
    } else {
      known.screen = screen.clone();
    }
    cameFrom = current != known ? current : null;
    if (cameFrom != null) {
      cameFrom.leadsTo.add(known.number);
      known.leadsTo.add(cameFrom.number);
      vote(cameFrom, known, leftBy, 1);
      lastWayOut = leftBy == null ? "unknown" : leftBy.toString();
    }
    current = known;
    mergeWhatIsNowTheSame(identity);
    layOut();
  }

  /** The way into the room now, told only after arriving: the game moved the player after changing the room. */
  public synchronized void lastWayWas(Side side) {
    if (cameFrom == null || !rooms.contains(cameFrom) || !rooms.contains(current)) return;
    vote(cameFrom, current, side, 1);
    lastWayOut = side.toString();
    layOut();
  }

  /** The player moved in the room now: it is a room. */
  public synchronized void played() {
    if (current != null) current.provisional = false;
  }

  /** The rooms played in: one nobody has moved in yet is not shown, since it may be no room. */
  public synchronized List<Room> rooms() {
    return rooms.stream().filter(room -> !room.provisional).toList();
  }

  public synchronized Room current() {
    return current;
  }

  public synchronized String lastWayOut() {
    return lastWayOut;
  }

  /** The number a room goes by now, after whatever it folded into. */
  public synchronized int numberNow(int number) {
    while (foldedInto.containsKey(number)) number = foldedInto.get(number);
    return number;
  }

  /** The side {@code to} lies on from {@code from} by most votes; null when nobody voted. */
  public synchronized Side sideOf(Room from, Room to) {
    int[] tally = votes.get(key(from, to));
    if (tally == null || tally[best(tally)] == 0) return null;
    Side side = Side.values()[best(tally)];
    return from.number < to.number ? side : opposite(side);
  }

  private static boolean same(byte[] one, byte[] other, List<Integer> identity) {
    for (int address : identity) if (one[address] != other[address]) return false;
    return true;
  }

  /** Rooms told apart by bytes since found to change inside a room are one room: the later folds into the earlier. */
  private void mergeWhatIsNowTheSame(List<Integer> identity) {
    for (int later = rooms.size() - 1; later > 0; later--) {
      for (int earlier = 0; earlier < later; earlier++) {
        Room kept = rooms.get(earlier), gone = rooms.get(later);
        if (!same(kept.memory, gone.memory, identity)) continue;
        rooms.remove(later);
        foldedInto.put(gone.number, kept.number);
        if (current == gone) current = kept;
        for (Room room : rooms) {
          if (room.leadsTo.remove(gone.number) && room != kept) {
            room.leadsTo.add(kept.number);
            kept.leadsTo.add(room.number);
          }
        }
        kept.leadsTo.remove(kept.number);
        Map<Long, int[]> was = new HashMap<>(votes);
        votes.clear();
        was.forEach((key, tally) -> {
          Room low = byNumber((int) (key >> 32), gone, kept), high = byNumber((int) (long) key, gone, kept);
          if (low == null || high == null || low == high) return;
          for (Side side : Side.values()) vote(low, high, side, tally[side.ordinal()]);
        });
        return;
      }
    }
  }

  /** The room so numbered, with the one gone standing for the one kept; null for a room gone earlier. */
  private Room byNumber(int number, Room gone, Room kept) {
    if (gone != null && number == gone.number) return kept;
    return rooms.stream().filter(room -> room.number == number).findFirst().orElse(null);
  }

  private void vote(Room from, Room to, Side side, int weight) {
    if (side == null || weight == 0) return;
    int[] tally = votes.computeIfAbsent(key(from, to), k -> new int[4]);
    tally[(from.number < to.number ? side : opposite(side)).ordinal()] += weight;
  }

  private static long key(Room one, Room other) {
    return (long) Math.min(one.number, other.number) << 32 | Math.max(one.number, other.number);
  }

  private static int best(int[] tally) {
    int best = 0;
    for (int side = 1; side < 4; side++) if (tally[side] > tally[best]) best = side;
    return best;
  }

  int strengthOf(Room one, Room other) {
    int[] tally = votes.get(key(one, other));
    return tally == null ? 0 : tally[best(tally)];
  }

  static Side opposite(Side side) {
    return switch (side) {
      case LEFT -> Side.RIGHT;
      case RIGHT -> Side.LEFT;
      case UP -> Side.DOWN;
      case DOWN -> Side.UP;
    };
  }

  static int dx(Side side) {
    return side == Side.LEFT ? -1 : side == Side.RIGHT ? 1 : 0;
  }

  static int dy(Side side) {
    return side == Side.UP ? -1 : side == Side.DOWN ? 1 : 0;
  }

  /**
   * Each room put on the grid once, beside the room the best voted way reaches it from, on that
   * side - or on the nearest free square when that one is taken - and left there: a room that
   * moved about with every vote would never be where the player last saw it. A room no voted way
   * reaches yet waits in a strip of its own below, where it takes nobody's square, until a way is
   * voted. Only when the ways voted no longer all fit the grid as drawn is the whole map laid out
   * again, from whichever room makes the best beginning, and only if that leaves fewer votes not
   * drawn as voted, a way along counting double, since the rooms of a floor are a row: a game
   * whose ways do not all fit a grid has some that cannot be, and the map keeps to the most taken.
   */
  private void layOut() {
    if (rooms.isEmpty()) return;
    Map<Room, int[]> laid = new HashMap<>();
    for (Room room : rooms) if (room.anchored) laid.put(room, new int[]{room.x, room.y});
    if (laid.isEmpty()) {
      Room first = rooms.get(0);
      first.anchored = true;
      laid.put(first, new int[]{first.x = 0, first.y = 0});
    }
    for (boolean more = true; more; ) {
      more = false;
      for (Room room : rooms) if (!room.anchored && anchor(room, laid)) more = true;
    }
    for (Room room : rooms) if (!room.anchored) placeBelow(room, laid);
    int undrawn = undrawnIn(laid);
    if (undrawn == 0) return;
    Map<Room, int[]> best = null;
    for (Room root : rooms) {
      Map<Room, int[]> again = layOutFrom(root);
      int count = undrawnIn(again);
      if (count < undrawn) {
        undrawn = count;
        best = again;
      }
    }
    if (best != null) best.forEach((room, at) -> {
      room.x = at[0];
      room.y = at[1];
      room.anchored = reached(room);
    });
  }

  /** Beside the placed room the best voted way reaches it from, on that side; false while no placed room does. */
  private boolean anchor(Room room, Map<Room, int[]> laid) {
    Room from = null;
    for (int number : room.leadsTo) {
      Room other = byNumber(number, null, null);
      if (other != null && laid.containsKey(other) && sideOf(other, room) != null && (from == null || strengthOf(other, room) > strengthOf(from, room))) from = other;
    }
    if (from == null) return false;
    Side side = sideOf(from, room);
    placeNear(room, laid.get(from)[0] + dx(side), laid.get(from)[1] + dy(side), laid, takenIn(laid));
    room.x = laid.get(room)[0];
    room.y = laid.get(room)[1];
    return room.anchored = true;
  }

  private void placeBelow(Room room, Map<Room, int[]> laid) {
    int below = laid.values().stream().mapToInt(at -> at[1]).max().orElse(-2) + 2;
    placeNear(room, 0, below, laid, takenIn(laid));
    room.x = laid.get(room)[0];
    room.y = laid.get(room)[1];
  }

  private static Set<Long> takenIn(Map<Room, int[]> laid) {
    Set<Long> taken = new HashSet<>();
    for (int[] at : laid.values()) taken.add(cell(at[0], at[1]));
    return taken;
  }

  /** Whether any voted way reaches the room, so that it has a place beside another rather than in the strip. */
  private boolean reached(Room room) {
    for (int number : room.leadsTo) {
      Room other = byNumber(number, null, null);
      if (other != null && sideOf(room, other) != null) return true;
    }
    return false;
  }

  /** The votes a layout does not draw as voted, a way along counting double. */
  private int undrawnIn(Map<Room, int[]> laid) {
    int undrawn = 0;
    for (Room from : rooms) {
      for (int number : from.leadsTo) {
        Room to = byNumber(number, null, null);
        Side side = to == null ? null : sideOf(from, to);
        if (side != null && (laid.get(to)[0] != laid.get(from)[0] + dx(side) || laid.get(to)[1] != laid.get(from)[1] + dy(side))) {
          undrawn += strengthOf(from, to) * (dx(side) != 0 ? 2 : 1);
        }
      }
    }
    return undrawn;
  }

  private record Way(int farOut, int strength, Room from, Room to) {
  }


  private Map<Room, int[]> layOutFrom(Room root) {
    Map<Room, int[]> laid = new HashMap<>();
    Set<Long> taken = new HashSet<>();
    java.util.PriorityQueue<Way> ways = new java.util.PriorityQueue<>(
        java.util.Comparator.comparingInt((Way way) -> way.farOut).thenComparingInt(way -> -way.strength));
    placeNear(root, 0, 0, laid, taken);
    waysOutOf(root, 1, ways);
    while (laid.size() < rooms.size()) {
      Way way = ways.poll();
      if (way == null) {
        Room alone = rooms.stream().filter(room -> !laid.containsKey(room)).findFirst().orElseThrow();
        int below = laid.values().stream().mapToInt(at -> at[1]).max().orElse(0) + 2;
        placeNear(alone, 0, below, laid, taken);
        waysOutOf(alone, 1, ways);
        continue;
      }
      if (laid.containsKey(way.to)) continue;
      Side side = sideOf(way.from, way.to);
      placeNear(way.to, laid.get(way.from)[0] + dx(side), laid.get(way.from)[1] + dy(side), laid, taken);
      waysOutOf(way.to, way.farOut + 1, ways);
    }
    return laid;
  }

  /** The voted ways out of a room just placed that far out, for the rooms they reach to be placed next. */
  private void waysOutOf(Room from, int farOut, java.util.PriorityQueue<Way> ways) {
    for (int number : from.leadsTo) {
      Room to = byNumber(number, null, null);
      if (to != null && sideOf(from, to) != null) ways.add(new Way(farOut, strengthOf(from, to), from, to));
    }
  }

  private static void placeNear(Room room, int x, int y, Map<Room, int[]> laid, Set<Long> taken) {
    for (int ring = 0; ; ring++) {
      for (int dy = -ring; dy <= ring; dy++) {
        for (int dx = -ring; dx <= ring; dx++) {
          if (Math.max(Math.abs(dx), Math.abs(dy)) == ring && taken.add(cell(x + dx, y + dy))) {
            laid.put(room, new int[]{x + dx, y + dy});
            return;
          }
        }
      }
    }
  }

  private static long cell(int x, int y) {
    return (long) x << 32 | (y & 0xffffffffL);
  }
}
