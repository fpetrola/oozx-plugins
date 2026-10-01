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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The rooms of a flip-screen game, worked out from nothing but the screen, one frame after another.
 * <p>
 * Inside a room only what moves changes. So a screen that differs from the room it was in by more
 * than {@link #SAME_CELLS} characters, leaving out those where anything has moved in it, is a way
 * out, and the screen once it has been still for {@link #SETTLING} frames is the room arrived at
 * - one already seen if it differs from it by no more than that, a new one otherwise. Comparing
 * whole screens does not tell rooms apart: two of Jet Set Willy's are 97% the same, black and the
 * same panel below, and what makes them two rooms is a handful of characters.
 * Which side it lies on is voted, every time the way between two rooms is taken. Whoever crosses
 * is the one thing that is up against an edge in the last frames of one room and up against the
 * opposite edge, at the same height or across, in the first frames of the next - a guardian at an
 * edge has nobody to pair with on the other side. Worth less, which edge of the old room the new
 * one carries on from. The map is laid out again
 * from those votes after each one, so that a way told wrong once is outvoted by the times after.
 * <p>
 * The edges are those of where things move, leaving out what is busy in every room - the lives
 * dancing, the clock - which changes all the time without anybody going anywhere.
 * Each room gets a place on a grid from the one it was reached from, which is the map.
 */
public final class Rooms {
  static final int SCREEN = 6912;
  static final int BITMAP = 6144;
  static final int COLUMNS = 32, ROWS = 24;
  /** Less than this changing between two frames is a screen standing still. */
  static final double STILL = 0.03;
  /** How alike two screens are at most and still not one room, for whoever measures it. */
  static final double SAME = 0.85;
  /** How many characters, of those where nothing has moved, a room can differ from itself by: whoever plays standing elsewhere, an item taken. Two rooms of Jet Set Willy differ by 45 at the fewest. */
  static final int SAME_CELLS = 24;
  /** Frames a new screen has to stand still to be taken as the room, since a game draws one over several. */
  static final int SETTLING = 8;
  /** How close to an edge, in characters, what moved has to be for leaving to be through it. */
  static final int EDGE = 4;
  /** More bytes than this changing at once is a room being drawn, not something moving in it. */
  static final int MOVING = 400;
  /** Frames before leaving whose movement is looked at to tell which way it was left by. */
  static final int RECENT = 6;
  /** Frames after a new room is arrived at in which whoever came in is still near the edge they came in by. */
  static final int COMING_IN = 25;
  /** A cell changing in more of the frames in a room than this is busy with something that stays. */
  static final double BUSY = 0.15;
  /** How much of an edge has to match the opposite edge of the next room for the scenery to carry on. */
  static final double CARRIES_ON = 0.6;

  public enum Side {
    LEFT(-1, 0), RIGHT(1, 0), UP(0, -1), DOWN(0, 1);
    final int dx, dy;

    Side(int dx, int dy) {
      this.dx = dx;
      this.dy = dy;
    }
  }

  /** One room: the screen it was first seen as, where it is on the map, and what it leads to. */
  public static final class Room {
    public final int number;
    public final byte[] screen;
    public int x, y;
    public final Set<Integer> leadsTo = new HashSet<>();
    /** The characters something has moved in while in this room, which say nothing about which room it is. */
    final boolean[] moved = new boolean[COLUMNS * ROWS];

    Room(int number, byte[] screen, int x, int y) {
      this.number = number;
      this.screen = screen;
      this.x = x;
      this.y = y;
    }
  }

  private final List<Room> rooms = new ArrayList<>();
  private byte[] previous;
  private Room current;
  private Room leaving;
  /** Votes for the side the second room of each pair lies on from the first, the lower number first. */
  private final Map<Long, int[]> votes = new HashMap<>();
  private int still;
  /** The cells that moved in each of the last frames in the room, the oldest first. */
  private final java.util.ArrayDeque<List<Integer>> lately = new java.util.ArrayDeque<>();
  /** What moved in the last frames before leaving, and in the first ones after coming in. */
  private final List<Integer> beforeLeaving = new ArrayList<>(), afterComingIn = new ArrayList<>();
  /** The edge most of what moved last was right up against when the room was left, or null. */
  private Side leftBy;
  /** How the side of the last way out was told, for whoever wants to know why the map is as it is. */
  private String lastWayOut = "";
  /** The lines of characters things have moved in, leaving out what is busy in every room. */
  private int top = ROWS, bottom = -1;
  /** In how many rooms each cell was busy: busy in more than one, it belongs to no room. */
  private final int[] busyIn = new int[COLUMNS * ROWS];
  /** How many of the frames in this room each cell changed in: a cell always changing is a clock or a guardian, not whoever is leaving. */
  private final int[] busy = new int[COLUMNS * ROWS];
  private int framesInTheRoom;
  /** The room just arrived at and the one it was reached from: where whoever came in is says where it lies. */
  private Room arrived, cameFrom;
  private int framesSinceArriving;

  /** The screen as it is at the end of a frame: its 6144 bytes of pixels and 768 of attributes. */
  public synchronized void frame(byte[] screen) {
    if (current != null && previous != null) followWhatMoves(previous, screen);
    if (current != null && differingCells(current, screen) > SAME_CELLS) {
      leaving = current;
      for (int cell = 0; cell < busy.length; cell++) if (framesInTheRoom > 10 && busy[cell] > framesInTheRoom * BUSY) busyIn[cell]++;
      beforeLeaving.clear();
      lately.forEach(beforeLeaving::addAll);
      leftBy = against(beforeLeaving, top, bottom);
      current = null;
      still = 0;
    }
    if (current == null) {
      still = previous != null && changed(previous, screen) < STILL ? still + 1 : 0;
      if (still >= SETTLING) arrive(screen);
    } else {
      if (arrived != null && ++framesSinceArriving == COMING_IN) voteForWhoCrossed();
    }
    previous = screen;
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

  private void arrive(byte[] screen) {
    Room known = rooms.stream().filter(room -> differingCells(room, screen) <= SAME_CELLS)
        .min(java.util.Comparator.comparingInt(room -> differingCells(room, screen))).orElse(null);
    if (known == null) {
      known = new Room(rooms.size(), screen.clone(), 0, 0);
      rooms.add(known);
    }
    if (leaving != null && leaving != known) {
      leaving.leadsTo.add(known.number);
      known.leadsTo.add(leaving.number);
      Side carriesOn = carriedOn(leaving.screen, screen);
      vote(leaving, known, leftBy, 2);
      vote(leaving, known, carriesOn, 1);
      lastWayOut = leftBy != null ? leftBy + " (who was against the edge)"
          : carriesOn != null ? carriesOn + " (the scenery carries on)" : "unknown";
      afterComingIn.clear();
      arrived = known;
      cameFrom = leaving;
      framesSinceArriving = 0;
    }
    current = known;
    lately.clear();
    java.util.Arrays.fill(busy, 0);
    framesInTheRoom = 0;
    layOut();
  }

  /** The side whoever crossed went through, voted for over the scenery when it can be told. */
  private void voteForWhoCrossed() {
    Side crossed = crossing(beforeLeaving, afterComingIn, top, bottom);
    if (crossed != null) {
      vote(cameFrom, arrived, crossed, 3);
      lastWayOut = crossed + " (who crossed)";
      layOut();
    }
    arrived = null;
  }

  /**
   * The side most pairs go through, of a cell that moved up against it before and one that moved
   * up against the opposite edge after, at the same height for a side or across for the top or
   * the bottom; null when nothing pairs.
   */
  /** The edge most of these cells are right up against, a character from it at most; null when none is. */
  static Side against(List<Integer> cells, int top, int bottom) {
    int[] touching = new int[4];
    for (int cell : cells) {
      int x = cell % COLUMNS, y = cell / COLUMNS;
      if (x <= 1) touching[Side.LEFT.ordinal()]++;
      if (x >= COLUMNS - 2) touching[Side.RIGHT.ordinal()]++;
      if (y <= top + 1) touching[Side.UP.ordinal()]++;
      if (y >= bottom - 1) touching[Side.DOWN.ordinal()]++;
    }
    int best = 0;
    for (int side = 1; side < 4; side++) if (touching[side] > touching[best]) best = side;
    return touching[best] == 0 ? null : Side.values()[best];
  }

  static Side crossing(List<Integer> before, List<Integer> after, int top, int bottom) {
    int[] pairs = new int[4];
    for (int one : before) {
      int x = one % COLUMNS, y = one / COLUMNS;
      for (int other : after) {
        int x2 = other % COLUMNS, y2 = other / COLUMNS;
        if (x <= EDGE && x2 >= COLUMNS - 1 - EDGE && Math.abs(y - y2) <= 2) pairs[Side.LEFT.ordinal()]++;
        if (x >= COLUMNS - 1 - EDGE && x2 <= EDGE && Math.abs(y - y2) <= 2) pairs[Side.RIGHT.ordinal()]++;
        if (y <= top + EDGE && y2 >= bottom - EDGE && Math.abs(x - x2) <= 2) pairs[Side.UP.ordinal()]++;
        if (y >= bottom - EDGE && y2 <= top + EDGE && Math.abs(x - x2) <= 2) pairs[Side.DOWN.ordinal()]++;
      }
    }
    int best = 0;
    for (int side = 1; side < 4; side++) if (pairs[side] > pairs[best]) best = side;
    return pairs[best] == 0 ? null : Side.values()[best];
  }

  private void vote(Room from, Room to, Side side, int weight) {
    if (side == null) return;
    boolean inOrder = from.number < to.number;
    int[] tally = votes.computeIfAbsent(key(from, to), k -> new int[4]);
    tally[(inOrder ? side : opposite(side)).ordinal()] += weight;
  }

  private static long key(Room one, Room other) {
    return (long) Math.min(one.number, other.number) << 32 | Math.max(one.number, other.number);
  }

  /** The side {@code to} lies on from {@code from} by most votes, and how many; null when nobody voted. */
  Side sideOf(Room from, Room to) {
    int[] tally = votes.get(key(from, to));
    if (tally == null) return null;
    int best = 0;
    for (int side = 1; side < 4; side++) if (tally[side] > tally[best]) best = side;
    if (tally[best] == 0) return null;
    Side side = Side.values()[best];
    return from.number < to.number ? side : opposite(side);
  }

  private int strengthOf(Room one, Room other) {
    int[] tally = votes.get(key(one, other));
    return tally == null ? 0 : Arrays.stream(tally).max().orElse(0);
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
          Room to = rooms.get(number);
          if (placed.contains(to)) continue;
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
      placeNear(bestTo, bestFrom.x + (side == null ? 0 : side.dx), bestFrom.y + (side == null ? 1 : side.dy), taken, placed);
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

  private static Side opposite(Side side) {
    return switch (side) {
      case LEFT -> Side.RIGHT;
      case RIGHT -> Side.LEFT;
      case UP -> Side.DOWN;
      case DOWN -> Side.UP;
    };
  }

  /**
   * What moved between two frames, when little did: every cell marked as one the room says nothing
   * by, and those that are not busy kept as the movement of the last frames - whoever leaves
   * among it.
   */
  private void followWhatMoves(byte[] before, byte[] now) {
    boolean[] changedCell = new boolean[busy.length];
    framesInTheRoom++;
    for (int offset = 0; offset < BITMAP; offset++) {
      if (before[offset] != now[offset]) changedCell[(offset >> 8 & 0x18 | offset >> 5 & 7) * COLUMNS + (offset & 31)] = true;
    }
    int changes = 0;
    for (boolean changed : changedCell) if (changed) changes++;
    if (changes * 8 >= MOVING) return;
    List<Integer> moving = new ArrayList<>();
    for (int cell = 0; cell < busy.length; cell++) {
      if (!changedCell[cell]) continue;
      busy[cell]++;
      current.moved[cell] = true;
      if (busyIn[cell] < 2 && (framesInTheRoom <= 10 || busy[cell] <= framesInTheRoom * BUSY)) moving.add(cell);
    }
    if (moving.isEmpty()) return;
    for (int cell : moving) {
      top = Math.min(top, cell / COLUMNS);
      bottom = Math.max(bottom, cell / COLUMNS);
    }
    lately.addLast(moving);
    while (lately.size() > RECENT) lately.removeFirst();
    if (arrived != null) afterComingIn.addAll(moving);
  }

  /**
   * The edge of the old room the new one carries on from: the attributes along each edge of the
   * old one against those along the opposite edge of the new, the side where they agree most, if
   * they agree well enough for it to be the same scenery going on.
   */
  static Side carriedOn(byte[] old, byte[] next) {
    Side best = null;
    double bestAgreement = CARRIES_ON;
    for (Side side : Side.values()) {
      int agree = 0, along = side.dx != 0 ? ROWS : COLUMNS;
      for (int i = 0; i < along; i++) {
        int oldCell = side.dx != 0 ? i * COLUMNS + (side.dx > 0 ? COLUMNS - 1 : 0) : (side.dy > 0 ? ROWS - 1 : 0) * COLUMNS + i;
        int newCell = side.dx != 0 ? i * COLUMNS + (side.dx > 0 ? 0 : COLUMNS - 1) : (side.dy > 0 ? 0 : ROWS - 1) * COLUMNS + i;
        if (old[BITMAP + oldCell] == next[BITMAP + newCell]) agree++;
      }
      double agreement = agree / (double) along;
      if (agreement > bestAgreement) {
        bestAgreement = agreement;
        best = side;
      }
    }
    return best;
  }

  /** The characters a screen differs from a room in, leaving out those something has moved in there. */
  static int differingCells(Room room, byte[] screen) {
    int differing = 0;
    for (int cell = 0; cell < COLUMNS * ROWS; cell++) {
      if (room.moved[cell]) continue;
      int x = cell % COLUMNS, y = cell / COLUMNS;
      boolean differs = room.screen[BITMAP + cell] != screen[BITMAP + cell];
      for (int line = 0; line < 8 && !differs; line++) {
        int offset = (y & 0x18) << 8 | line << 8 | (y & 7) << 5 | x;
        differs = room.screen[offset] != screen[offset];
      }
      if (differs) differing++;
    }
    return differing;
  }

  static double changed(byte[] one, byte[] other) {
    return 1 - alike(one, other);
  }

  static double alike(byte[] one, byte[] other) {
    int same = 0;
    for (int offset = 0; offset < SCREEN; offset++) if (one[offset] == other[offset]) same++;
    return same / (double) SCREEN;
  }
}
