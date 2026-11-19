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

package com.fpetrola.oozx.speccy.devices.mouse;

/**
 * Kempston Mouse protocol state: two wrapping 8-bit delta counters and three buttons. Programs
 * read the counters as relative movement, not absolute position, and a button bit reads low
 * (not high) while held.
 */
public class KempstonMouse {

  /** Counter wrap value; vertical counts increase moving down, opposite of screen Y. */
  private static final int WRAP = 0xFF;

  /** Observer hook exposing both raw hand motion and program reads together, needed since
   * neither alone explains an apparent pointer jump. */
  public interface Watcher {
    void handMoved(int dx, int dy, int x, int y);

    void programRead(String which, int value);
  }

  private static final Watcher NOBODY = new Watcher() {
    public void handMoved(int dx, int dy, int x, int y) {
    }

    public void programRead(String which, int value) {
    }
  };

  private volatile Watcher watcher = NOBODY;

  public void watch(Watcher listening) {
    watcher = listening == null ? NOBODY : listening;
  }

  /**
   * The most the hand's movement shows in one reading. Programs speed a big step up, the way a
   * desk's pointer does, so a hand going a hundred pixels in one reading sends the pointer three
   * hundred; in steps this small the OCP Art Studio goes a pixel a count, and its pointer stays
   * under the hand. What is beyond it goes in the readings after.
   */
  private static final int NOT_SPED_UP = 6;

  /** What can be owed at most: further than that is off the screen anyway, where every program stops. */
  private static final int A_SCREEN = 256;

  private int x;
  private int y;
  private int buttons = WRAP;

  /** Unreported motion, at most a screen of it. */
  private int owedX;
  private int owedY;

  /** Toggles whether excess motion carries to the next read (accurate but delayed) or is
   * dropped (immediate but undercounts fast strokes); left as a user preference. */
  private boolean carryOver = true;

  public void setCarryOver(boolean wanted) {
    carryOver = wanted;
    if (!wanted) {
      owedX = owedY = 0;
    }
  }

  public boolean isCarryOver() {
    return carryOver;
  }

  /** Accumulated counter movement since the last program read; this total, not any single
   * step, is what must stay within the steps a program does not speed up. */
  private int shownX;
  private int shownY;

  /** Accepts a raw hand-motion delta, in whatever units the input device reports. */
  public void moved(int dx, int dy) {
    owedX += dx;
    owedY -= dy;
    payWhatCanBeRead();
    int most = carryOver ? A_SCREEN : 0;
    owedX = Math.max(-most, Math.min(most, owedX));
    owedY = Math.max(-most, Math.min(most, owedY));
    watcher.handMoved(dx, dy, x, y);
  }

  /**
   * Takes the program's pointer to that many counts from the top left corner, wherever it was.
   * <p>
   * A program only ever hears differences, so where its pointer is cannot be told; but every
   * program stops its pointer at the edge of the screen. So the pointer is first sent further up
   * and left than any screen is, which leaves it in the corner, and then that far from it. The two
   * are owed apart: added together they would cancel, and the corner would never be reached.
   */
  public void placeAt(int across, int down) {
    cornerX = -FURTHER_THAN_ANY_SCREEN;
    cornerY = FURTHER_THAN_ANY_SCREEN;
    owedX = across;
    owedY = -down;
    payWhatCanBeRead();
  }

  private static final int FURTHER_THAN_ANY_SCREEN = A_SCREEN + 32;
  private int cornerX;
  private int cornerY;
  /** Reached the corner and not read since: what comes next waits, or the program would hear the two as one, and less. */
  private boolean inTheCornerX;
  private boolean inTheCornerY;


  /** Moves as much owed motion into the counters as still fits within the read limit. */
  private void payWhatCanBeRead() {
    if (cornerX != 0 || cornerY != 0) {
      int payX = whatFits(cornerX, shownX, NOT_SPED_UP), payY = whatFits(cornerY, shownY, NOT_SPED_UP);
      cornerX -= payX;
      cornerY -= payY;
      show(payX, payY);
      inTheCornerX = inTheCornerY = cornerX == 0 && cornerY == 0;
    } else {
      int payX = inTheCornerX ? 0 : whatFits(owedX, shownX, NOT_SPED_UP), payY = inTheCornerY ? 0 : whatFits(owedY, shownY, NOT_SPED_UP);
      owedX -= payX;
      owedY -= payY;
      show(payX, payY);
    }
  }

  private void show(int payX, int payY) {
    x = (x + payX) & WRAP;
    y = (y + payY) & WRAP;
    shownX += payX;
    shownY += payY;
  }

  /** What is left before one reading would show more than that much. */
  private static int whatFits(int owed, int shown, int most) {
    return owed > 0 ? Math.max(0, Math.min(owed, most - shown)) : Math.min(0, Math.max(owed, -most - shown));
  }

  /** Total unreported motion still pending, summed across both axes. */
  public int owed() {
    return Math.abs(owedX) + Math.abs(owedY);
  }

  /** Sets one button's state; button numbering is 0=left, 1=right, 2=middle. */
  public void button(int which, boolean down) {
    if (which >= 0 && which < 8) {
      buttons = down ? buttons & ~(1 << which) & WRAP : buttons | 1 << which;
    }
  }

  /** Resets to the unplugged state: no buttons held, no pending or shown movement. */
  public void rest() {
    buttons = WRAP;
    owedX = owedY = shownX = shownY = cornerX = cornerY = 0;
  }

  /** The counters as they stand, for a window to show: not a reading, so it lets nothing owed through. */
  public int standingX() {
    return x;
  }

  public int standingY() {
    return y;
  }

  public int x() {
    int reading = x;
    watcher.programRead("x", reading);
    // Resets the read baseline and releases any motion that was waiting for room.
    shownX = 0;
    inTheCornerX = false;
    payWhatCanBeRead();
    return reading;
  }

  public int y() {
    int reading = y;
    watcher.programRead("y", reading);
    shownY = 0;
    inTheCornerY = false;
    payWhatCanBeRead();
    return reading;
  }

  public int buttons() {
    watcher.programRead("buttons", buttons);
    return buttons;
  }

  /** True if the given button is currently held (its bit reads low). */
  public boolean isHeld(int which) {
    return (buttons & 1 << which) == 0;
  }
}
