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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where the game keeps whoever is being played, found in its memory as the byte the keys move:
 * it goes up while one key is held and down while another is, almost every time either changes
 * it, and hardly ever moves with no key held. Guardians, timers and scores change whatever is
 * held. Which key is which comes out with the byte, so nothing has to be known about the game.
 * <p>
 * Once found, the byte is read as a position. If the byte after it changes only when this one
 * wraps round, the two are one number. Its steps along, under the keys, and its other steps,
 * which are down or up, say how it packs a column and a row: Jet Set Willy keeps Willy as an
 * address in a screen buffer, so a column is one and a row is thirty-two. A room is left by the
 * edge the position was against just before the game put the player at the next room's door,
 * which is a jump no walk makes.
 */
public final class PlayerVariable {
  static final int FROM = 0x5B00, TO = 0x10000, KEYS = 45;
  /** Changes under a key before the key is believed, changes in all before a byte is, and how consistently they must go one way. */
  static final int ENOUGH = 60, SEEN = 300;
  static final double CONSISTENT = 0.75;
  /** Changes with no key held, as a share of all its changes, beyond which a byte is not the player's. */
  static final double UNBIDDEN = 0.5;
  /** Frames a key is still taken as held after it was seen: the game moves a while after it reads. */
  static final int LINGER = 3;
  /** Frames of the position kept, to look back from a room change to before the player was moved. */
  static final int KEPT = 32;
  /** Frames the byte has to have been followed before its word is taken: the first to show is not always the one. */
  static final int TRUSTED_AFTER = 1500;

  /** A byte the keys move: which key sends it up, which down, and how sure that is. */
  public record Found(int address, int up, int down, int changes, double score) {
  }

  public enum Side { LEFT, RIGHT, UP, DOWN }

  private final RoomVariable.Ram ram;
  private final byte[] previous = new byte[TO];
  private final int[] changes = new int[TO], unbidden = new int[TO], carries = new int[TO], notCarries = new int[TO];
  private final int[][] upUnder = new int[KEYS][], downUnder = new int[KEYS][];
  private final long[] lately = new long[LINGER];
  private int frame;
  private boolean started;

  private Found player;
  private boolean wide;
  private final int[] kept = new int[KEPT];
  private int followed;
  private final Map<Integer, Integer> stepsAlong = new HashMap<>(), otherSteps = new HashMap<>();
  private int xMin = Integer.MAX_VALUE, xMax = -1, yMin = Integer.MAX_VALUE, yMax = -1, modulusWas;

  public PlayerVariable(RoomVariable.Ram ram) {
    this.ram = ram;
  }

  /** At the end of a frame, with the keys the game saw held during it. */
  public void frame(long keys) {
    frame++;
    lately[frame % LINGER] = keys;
    long held = 0;
    for (long seen : lately) held |= seen;
    int wrapped = -2;
    for (int address = FROM; address < TO; address++) {
      byte now = (byte) ram.peek(address);
      int delta = now - previous[address];
      boolean wraps = Math.abs((now & 0xff) - (previous[address] & 0xff)) > 128;
      previous[address] = now;
      if (delta == 0 || !started) continue;
      if (wrapped == address - 1) carries[wrapped]++;
      else if (address > FROM) notCarries[address - 1]++;
      if (wraps) wrapped = address;
      changes[address]++;
      if (held == 0) {
        unbidden[address]++;
        continue;
      }
      for (int key = 0; key < KEYS; key++) {
        if ((held >> key & 1) == 0) continue;
        if (upUnder[key] == null) {
          upUnder[key] = new int[TO];
          downUnder[key] = new int[TO];
        }
        if (delta > 0) upUnder[key][address]++;
        else downUnder[key][address]++;
      }
    }
    started = true;
    if (frame % 50 == 0) choose();
    if (player != null) follow(held);
  }

  /** The surest byte, taken up at once and let go of for one twice as sure: the first to show is not always the one. */
  private void choose() {
    List<Found> found = found();
    if (found.isEmpty() || player != null && (found.get(0).address == player.address || found.get(0).score < 2 * player.score)) return;
    player = found.get(0);
    wide = carries[player.address] > 0 && carries[player.address] >= notCarries[player.address];
    followed = 0;
    stepsAlong.clear();
    otherSteps.clear();
    xMin = yMin = Integer.MAX_VALUE;
    xMax = yMax = -1;
  }

  /** Whether the player has been found, so that where they are can be asked. */
  public boolean known() {
    return player != null;
  }

  public Found player() {
    return player;
  }

  /** Whether the found byte and the next are read as one number. */
  public boolean wide() {
    return wide;
  }

  /**
   * The side the room was just left by: the edge the player was against before the game moved
   * them to the next room's door, or, away from every edge, the way they were last going. Null
   * until the player has been followed for a while.
   */
  public Side sideOfLeaving() {
    if (player == null || followed < TRUSTED_AFTER) return null;
    int across = stepAlong(), down = stepDown();
    int modulus = modulus(across, down);
    int before = 1;
    for (int back = 1; back < KEPT / 2; back++) {
      int from = at(back), to = at(back - 1);
      boolean leapt = Math.abs(to % modulus - from % modulus) > (xMax - xMin) / 2 || down > 0 && Math.abs(to / modulus - from / modulus) > 2;
      if (leapt) before = back + 1;
    }
    int x = at(before) % modulus, y = at(before) / modulus;
    int lastMove = 0;
    for (int back = before; back + 1 < KEPT && lastMove == 0; back++) lastMove = at(back) - at(back + 1);
    // Packed, the columns run from nought to the row's width, and the player is wider than one.
    boolean packed = down > across;
    int left = packed ? 0 : xMin, right = packed ? modulus - 1 - across : xMax;
    boolean atLeft = x <= left + across, atRight = x >= right - across, atTop = y <= yMin + 1, atBottom = y >= yMax - 1;
    boolean movedAlong = down == 0 || Math.abs(lastMove) < down / 2;
    if ((atLeft || atRight) && (movedAlong || !atTop && !atBottom)) return atLeft ? Side.LEFT : Side.RIGHT;
    if (atTop || atBottom) return atTop ? Side.UP : Side.DOWN;
    return lastMove < 0 ? Side.UP : Side.DOWN;
  }

  /** The position as it was that many frames ago. */
  private int at(int back) {
    return kept[(frame - back + KEPT) % KEPT];
  }

  private int value() {
    int low = ram.peek(player.address) & 0xff;
    return wide ? low | (ram.peek(player.address + 1) & 0xff) << 8 : low;
  }

  private int modulus(int across, int down) {
    return down > across ? down : wide ? 0x10000 : 256;
  }

  private void follow(long held) {
    int now = value(), was = at(1);
    kept[frame % KEPT] = now;
    if (++followed < 2) return;
    int delta = wide ? now - was : (byte) (now - was);
    if (delta != 0) {
      boolean along = (held >> player.up & 1) != 0 || (held >> player.down & 1) != 0;
      (along ? stepsAlong : otherSteps).merge(Math.abs(delta), 1, Integer::sum);
    }
    int modulus = modulus(stepAlong(), stepDown());
    if (modulus != modulusWas) {
      xMin = yMin = Integer.MAX_VALUE;
      xMax = yMax = -1;
      modulusWas = modulus;
    }
    xMin = Math.min(xMin, now % modulus);
    xMax = Math.max(xMax, now % modulus);
    yMin = Math.min(yMin, now / modulus);
    yMax = Math.max(yMax, now / modulus);
  }

  private int stepAlong() {
    return stepsAlong.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(1);
  }

  private int stepDown() {
    int across = stepAlong();
    return otherSteps.entrySet().stream().filter(step -> step.getKey() != across).max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(0);
  }

  /** How the position is being read right now, for whoever is measuring. */
  public String state() {
    if (player == null) return "no player yet";
    int across = stepAlong(), down = stepDown(), modulus = modulus(across, down);
    StringBuilder values = new StringBuilder();
    for (int back = 0; back < 12; back++) values.append(at(back)).append(back < 11 ? "," : "");
    return String.format("0x%04X wide %b followed %d across %d down %d modulus %d x %d..%d y %d..%d; last values %s",
        player.address, wide, followed, across, down, modulus, xMin, xMax, yMin, yMax, values);
  }

  /** What was seen of one byte, for whoever is measuring. */
  public String describe(int address) {
    StringBuilder text = new StringBuilder(String.format("0x%04X: %d changes, %d unbidden, carries %d not %d;", address, changes[address], unbidden[address], carries[address], notCarries[address]));
    for (int key = 0; key < KEYS; key++) {
      if (upUnder[key] != null && upUnder[key][address] + downUnder[key][address] > 0) text.append(String.format(" key %d up %d down %d", key, upUnder[key][address], downUnder[key][address]));
    }
    return text.toString();
  }

  /** The bytes some key sends up and another down, the surest first. */
  public List<Found> found() {
    List<Found> found = new ArrayList<>();
    for (int address = FROM; address < TO; address++) {
      if (changes[address] < SEEN || unbidden[address] > UNBIDDEN * changes[address]) continue;
      int up = -1, down = -1;
      double upSure = CONSISTENT, downSure = CONSISTENT;
      for (int key = 0; key < KEYS; key++) {
        if (upUnder[key] == null) continue;
        int rises = upUnder[key][address], falls = downUnder[key][address];
        if (rises + falls < ENOUGH) continue;
        if (rises / (double) (rises + falls) >= upSure) {
          upSure = rises / (double) (rises + falls);
          up = key;
        }
        if (falls / (double) (rises + falls) >= downSure) {
          downSure = falls / (double) (rises + falls);
          down = key;
        }
      }
      if (up < 0 || down < 0 || upSure + downSure <= 2 * CONSISTENT) continue;
      int explained = upUnder[up][address] + downUnder[up][address] + upUnder[down][address] + downUnder[down][address];
      // The margin over what a counter that wraps round shows, times how much the keys explain: a
      // position goes the key's way almost always, a counter of four does three times in four.
      found.add(new Found(address, up, down, changes[address], (upSure + downSure - 2 * CONSISTENT) * explained));
    }
    found.sort(Comparator.comparingDouble((Found one) -> -one.score));
    return found;
  }
}
