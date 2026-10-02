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
  private boolean flipping = true, leaving;
  private int still, frames, arrivedAt = -1;
  private byte[] screenOnArrival;
  private boolean[] movedSinceArrival;

  public RoomVariable(Ram ram) {
    this.ram = ram;
    java.util.Arrays.fill(steady, FROM, TO, true);
  }

  /** The screen at the end of a frame; true when a room has just been arrived at. */
  public boolean frame(byte[] screen) {
    frames++;
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

  /** Whether a room has just been seen to be left: the frame the next one started being drawn in. */
  public boolean leaving() {
    return leaving;
  }

  /** The addresses that have only ever changed between rooms: the room's identity is their values. */
  public List<Integer> survivors() {
    List<Integer> survivors = new ArrayList<>();
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
      else if (frames - suspectedAt[address] > GRACE && frames - struckAt[address] > APART) {
        struckAt[address] = frames;
        suspectedAt[address] = 0;
        if (++strikes[address] >= STRIKES) steady[address] = false;
        if (address == WATCHED) System.out.printf("  0x%04X strike %d at frame %d: differed inside a room since frame %d%n", address, strikes[address], frames, frames - GRACE);
      }
    }
  }

  private byte[] snapshot() {
    byte[] memory = new byte[TO];
    for (int address = FROM; address < TO; address++) memory[address] = (byte) ram.peek(address);
    return memory;
  }
}
