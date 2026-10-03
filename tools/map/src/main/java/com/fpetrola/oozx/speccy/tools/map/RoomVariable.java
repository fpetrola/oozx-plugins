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
import java.util.List;

/**
 * The game's own idea of which room it is in, found in its memory: the bytes that change between
 * rooms and never inside one. Whenever the screen is redrawn - cells that had been standing still
 * change by the dozen, where whatever moves in a room touches a few a frame - and then stands
 * still again, a room has been arrived at; the memory then is compared with the memory in the last room, and,
 * every few frames while here, with the memory on arrival. What differs inside a room - timers,
 * guardians, the score - is struck off, unless a room is arrived at soon after it was first seen
 * to differ, since a game sets where it is going before it draws it; and only when struck twice,
 * well apart, since a room left again before its drawing counted as scenery looks like a byte
 * that changed inside it; what is left and has differed between rooms is
 * the room's identity, as a tuple of their values. A room arrived at twice with screens as alike
 * as a room is to itself - leaving out whatever moved in either visit - strikes off whatever
 * differs between the two visits as well: what was entered by, or how many rooms have been seen,
 * change between rooms without being the room.
 */
public final class RoomVariable {
  public interface Ram {
    int peek(int address);
  }

  /** Below this is the ROM and the screen: nothing a game keeps a room number in. */
  static final int FROM = 0x5B00, TO = 0x10000;
  /**
   * A cell unchanged for this many frames is scenery, and scenery changing is a room being drawn.
   * Short enough for a room left again almost at once to have its cells counted as scenery by then.
   */
  static final int SCENERY = 20;
  /** Cells of scenery changing within a few frames that are a room being drawn, not something passing over it. */
  static final int REDRAWN = 32, WITHIN = 4;
  /** Frames with next to no scenery changing for the drawing to be over, since a game draws a room over several. */
  static final int SETTLING = 8, STILL = 2;
  /** Frames between looks at the memory inside a room, and between the snapshots kept of it. */
  static final int EVERY = 5, KEPT_EVERY = 25;
  /** Frames a byte seen to differ inside a room is given for a room to be drawn, before it is struck. */
  static final int GRACE = 25;
  /** Strikes, at least that many frames apart, before a byte is struck off for good. */
  static final int STRIKES = 2, APART = 100;
  /** Two screens within this many still cells of each other are one room, whatever the memory says; judged this long after arriving, once what moves has moved. */
  static final int LOOKALIKE = 32, JUDGED_AFTER = 50;

  private record Visit(byte[] screen, boolean[] moved, byte[] memory) {
  }

  /** An address to say out loud why it was struck off, while this is being measured. */
  private static final int WATCHED = Integer.getInteger("map.watch", -1);

  private final Ram ram;
  private final boolean[] steady = new boolean[TO];
  private final boolean[] changedBetweenRooms = new boolean[TO];
  private final List<Visit> firstVisits = new ArrayList<>();
  private byte[] previous, before, beforeThat, onArrival;
  /** The frame each byte was first seen to differ from its value on arrival, while it is given grace. */
  private final int[] suspectedAt = new int[TO];
  private final int[] strikes = new int[TO], struckAt = new int[TO];
  private final int[] lastChanged = new int[Screens.CELLS];
  private final int[] sceneryChanged = new int[WITHIN];
  private boolean flipping = true, leaving, redrawn;
  private int still, frames, arrivedAt = -1, leftAt;
  private byte[] screenOnArrival;
  private boolean[] movedSinceArrival;

  public RoomVariable(Ram ram) {
    this.ram = ram;
    java.util.Arrays.fill(steady, FROM, TO, true);
  }

  /**
   * Knowing already which bytes may be the room, from the game played once before: one of them
   * changing is leaving, all of them still is arriving, and whatever is then found to change
   * inside a room, or between two visits to one, is struck off as when finding out.
   */
  public RoomVariable(Ram ram, List<Integer> known) {
    this.ram = ram;
    this.known = List.copyOf(known);
    for (int address : known) steady[address] = true;
  }

  private List<Integer> known;
  private byte[] knownValues, settledValues;

  /** The screen at the end of a frame; true when a room has just been arrived at. */
  public boolean frame(byte[] screen) {
    frames++;
    if (known != null) return frameKnowing(screen);
    int scenery = sceneryChanged(screen);
    int lately = 0;
    for (int changed : sceneryChanged) lately += changed;
    leaving = !flipping && lately >= REDRAWN;
    if (leaving) {
      flipping = true;
      still = 0;
      java.util.Arrays.fill(suspectedAt, 0);
    }
    boolean arrived = false;
    if (WATCHED >= 0 && (frames < 12 || frames >= 596 && frames < 606)) System.out.printf("  frame %d: scenery %d lately %d flipping %b still %d%n", frames, scenery, lately, flipping, still);
    if (flipping) {
      still = scenery <= STILL ? still + 1 : 0;
      if (still >= SETTLING) {
        arrive(screen);
        flipping = false;
        arrived = true;
      }
    } else {
      if (frames - arrivedAt == JUDGED_AFTER) judgeLookalikes();
      if (frames % EVERY == 0) lookInside();
      if (frames % KEPT_EVERY == 0) {
        beforeThat = before;
        before = snapshot();
      }
    }
    previous = screen;
    return arrived;
  }

  /** The cells that changed this frame after standing still long enough to be scenery, kept for the last few frames. */
  private int sceneryChanged(byte[] screen) {
    int scenery = 0;
    for (int cell = 0; cell < Screens.CELLS; cell++) {
      boolean changed = false;
      for (int line = 0; line < 8 && !changed && previous != null; line++) {
        int offset = Screens.lineOffset(cell, line);
        changed = previous[offset] != screen[offset];
      }
      if (!changed) continue;
      if (frames - lastChanged[cell] > SCENERY) scenery++;
      lastChanged[cell] = frames;
      if (movedSinceArrival != null) movedSinceArrival[cell] = true;
    }
    sceneryChanged[frames % WITHIN] = scenery;
    return scenery;
  }

  /**
   * Knowing the room's bytes, a change in them is leaving and their standing still is arriving;
   * but the bytes known so far may still hold some that change inside a room, and a change of
   * those looks the same. What tells them apart is the screen: a room left is a screen redrawn.
   * A change the screen never follows is struck off, twice to be sure, and is no arrival.
   */
  private boolean frameKnowing(byte[] screen) {
    int lately = sceneryChanged(screen);
    for (int changed : sceneryChanged) lately += changed;
    byte[] now = new byte[known.size()];
    for (int i = 0; i < now.length; i++) {
      int address = known.get(i);
      now[i] = steady[address] ? (byte) ram.peek(address) : settledValues == null ? 0 : settledValues[i];
    }
    leaving = !flipping && !java.util.Arrays.equals(now, settledValues);
    if (leaving) {
      flipping = true;
      still = 0;
      leftAt = frames;
      redrawn = false;
      java.util.Arrays.fill(suspectedAt, 0);
    }
    boolean arrived = false;
    if (flipping) {
      redrawn |= lately >= REDRAWN;
      // A room is set up within the grace a game gets for it; what still changes after that is a
      // clock, not the room, and it would hold the arrival up forever.
      if (frames - leftAt > GRACE) for (int i = 0; i < now.length; i++) if (now[i] != knownValues[i]) steady[known.get(i)] = false;
      still = java.util.Arrays.equals(now, knownValues) ? still + 1 : 0;
      if (still >= SETTLING) {
        if (redrawn || settledValues == null) {
          arrive(screen);
          arrived = true;
        } else {
          for (int i = 0; i < now.length; i++) if (now[i] != settledValues[i]) strike(known.get(i), "changed with no room drawn");
        }
        settledValues = now;
        flipping = false;
      }
    } else {
      if (frames - arrivedAt == JUDGED_AFTER) judgeLookalikes();
      if (frames % EVERY == 0) lookInside();
    }
    knownValues = now;
    previous = screen;
    return arrived;
  }

  /** Whether a room has just been seen to be left: the frame the next one started being drawn in. */
  public boolean leaving() {
    return leaving;
  }

  /** The addresses that have only ever changed between rooms: the room's identity is their values. */
  public List<Integer> survivors() {
    List<Integer> survivors = new ArrayList<>();
    if (known != null) {
      for (int address : known) if (steady[address]) survivors.add(address);
      return survivors;
    }
    for (int address = FROM; address < TO; address++) if (steady[address] && changedBetweenRooms[address]) survivors.add(address);
    return survivors;
  }

  /** The memory as it was on arriving in the room now, for whoever keeps the identity of each. */
  public byte[] memoryOnArrival() {
    return onArrival;
  }

  private void arrive(byte[] screen) {
    byte[] now = snapshot();
    byte[] lastRoom = beforeThat != null ? beforeThat : before;
    if (lastRoom != null) {
      for (int address = FROM; address < TO; address++) if (lastRoom[address] != now[address]) changedBetweenRooms[address] = true;
    }
    onArrival = now;
    before = now;
    beforeThat = null;
    arrivedAt = frames;
    screenOnArrival = screen.clone();
    movedSinceArrival = new boolean[Screens.CELLS];
  }

  /** The visit now against those before, by what stood still in both: the same room says which bytes are not the room. */
  private void judgeLookalikes() {
    Visit same = firstVisits.stream().filter(visit -> differingStillCells(visit, screenOnArrival, movedSinceArrival) <= LOOKALIKE).findFirst().orElse(null);
    if (WATCHED >= 0) {
      int nearest = firstVisits.stream().mapToInt(visit -> differingStillCells(visit, screenOnArrival, movedSinceArrival)).min().orElse(-1);
      int moved = 0;
      for (boolean cell : movedSinceArrival) if (cell) moved++;
      System.out.printf("  judged at frame %d: %d visits kept, nearest differs by %d still cells, %d cells moved here, %s%n", frames, firstVisits.size(), nearest, moved, same == null ? "new" : "seen before");
    }
    if (same == null) {
      firstVisits.add(new Visit(screenOnArrival, movedSinceArrival.clone(), onArrival));
      return;
    }
    for (int address = FROM; address < TO; address++) {
      if (same.memory[address] == onArrival[address] || !steady[address]) continue;
      steady[address] = false;
      if (address == WATCHED) System.out.printf("  0x%04X struck off at frame %d: a room seen before, where it was %d and is %d%n",
          address, frames, same.memory[address] & 0xff, onArrival[address] & 0xff);
    }
  }

  private static int differingStillCells(Visit visit, byte[] screen, boolean[] moved) {
    int differing = 0;
    for (int cell = 0; cell < Screens.CELLS; cell++) {
      if (visit.moved[cell] || moved[cell]) continue;
      boolean differs = visit.screen[Screens.BITMAP + cell] != screen[Screens.BITMAP + cell];
      for (int line = 0; line < 8 && !differs; line++) {
        int offset = Screens.lineOffset(cell, line);
        differs = visit.screen[offset] != screen[offset];
      }
      if (differs) differing++;
    }
    return differing;
  }

  private void lookInside() {
    for (int address = FROM; address < TO; address++) {
      if (!steady[address] || (byte) ram.peek(address) == onArrival[address]) continue;
      if (suspectedAt[address] == 0) suspectedAt[address] = frames;
      else if (frames - suspectedAt[address] > GRACE) {
        suspectedAt[address] = 0;
        strike(address, "differed inside a room since frame " + (frames - GRACE));
      }
    }
  }

  /** One strike against a byte being the room's, well apart from the last; the second strikes it off. */
  private void strike(int address, String why) {
    if (frames - struckAt[address] <= APART) return;
    struckAt[address] = frames;
    if (++strikes[address] >= STRIKES) steady[address] = false;
    if (address == WATCHED) System.out.printf("  0x%04X strike %d at frame %d: %s%n", address, strikes[address], frames, why);
  }

  private byte[] snapshot() {
    byte[] memory = new byte[TO];
    for (int address = FROM; address < TO; address++) memory[address] = (byte) ram.peek(address);
    return memory;
  }
}
