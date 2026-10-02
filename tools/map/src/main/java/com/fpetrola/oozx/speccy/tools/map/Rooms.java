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
 * down, become one.
 */
public final class Rooms {

  /** One room: the screen it was first seen as, the memory it is known by, where it is on the map, and what it leads to. */
  public static final class Room {
    public final int number;
    public final byte[] screen;
    final byte[] memory;
    public int x, y;
    public final Set<Integer> leadsTo = new HashSet<>();

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
  private Room current;
  private String lastWayOut = "";

  /** A room arrived at: known by these bytes of memory, looking like this, the last one left by that side. */
  public synchronized void arrive(List<Integer> identity, byte[] memory, byte[] screen, Side leftBy) {
    Room known = rooms.stream().filter(room -> same(room.memory, memory, identity)).findFirst().orElse(null);
    if (known == null) {
      known = new Room(numbered++, screen.clone(), memory);
      rooms.add(known);
    }
    if (current != null && current != known) {
      current.leadsTo.add(known.number);
      known.leadsTo.add(current.number);
      vote(current, known, leftBy, 1);
      lastWayOut = leftBy == null ? "unknown" : leftBy.toString();
    }
    current = known;
    mergeWhatIsNowTheSame(identity);
    layOut();
  }

  public synchronized List<Room> rooms() {
    return List.copyOf(rooms);
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

  private int strengthOf(Room one, Room other) {
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
   * Every room put on the grid again from the first: the best voted way from a room already placed
   * to one that is not, again and again, each next to the other on its side - or on the nearest
   * free square when that one is taken, which is where a map stops being a grid.
   */
  private void layOut() {
    Map<Long, Room> taken = new HashMap<>();
    Set<Room> placed = new HashSet<>();
    if (rooms.isEmpty()) return;
    place(rooms.get(0), 0, 0, taken, placed);
    while (placed.size() < rooms.size()) {
      Room bestFrom = null, bestTo = null;
      int bestStrength = -1;
      for (Room from : placed) {
        for (int number : from.leadsTo) {
          Room to = byNumber(number, null, null);
          if (to == null || placed.contains(to)) continue;
          int strength = strengthOf(from, to);
          if (strength > bestStrength) {
            bestStrength = strength;
            bestFrom = from;
            bestTo = to;
          }
        }
      }
      if (bestTo == null) {
        Room alone = rooms.stream().filter(room -> !placed.contains(room)).findFirst().orElseThrow();
        int below = placed.stream().mapToInt(room -> room.y).max().orElse(0) + 2;
        placeNear(alone, 0, below, taken, placed);
        continue;
      }
      Side side = sideOf(bestFrom, bestTo);
      placeNear(bestTo, bestFrom.x + (side == null ? 0 : dx(side)), bestFrom.y + (side == null ? 1 : dy(side)), taken, placed);
    }
  }

  private void placeNear(Room room, int x, int y, Map<Long, Room> taken, Set<Room> placed) {
    for (int ring = 0; ; ring++) {
      for (int dy = -ring; dy <= ring; dy++) {
        for (int dx = -ring; dx <= ring; dx++) {
          if (Math.max(Math.abs(dx), Math.abs(dy)) == ring && !taken.containsKey(cell(x + dx, y + dy))) {
            place(room, x + dx, y + dy, taken, placed);
            return;
          }
        }
      }
    }
  }

  private static void place(Room room, int x, int y, Map<Long, Room> taken, Set<Room> placed) {
    room.x = x;
    room.y = y;
    taken.put(cell(x, y), room);
    placed.add(room);
  }

  private static long cell(int x, int y) {
    return (long) x << 32 | (y & 0xffffffffL);
  }
}
