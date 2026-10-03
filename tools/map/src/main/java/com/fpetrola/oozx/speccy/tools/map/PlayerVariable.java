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
 * when those are as common, say it packs a column and a row: Jet Set Willy keeps Willy as an
 * address in a screen buffer, so a column is one and a row is thirty-two. Otherwise it is the
 * column alone, and the row is the byte that leaps when a room is left without the column
 * leaping, as happens going up or down, and not when it is left with the column leaping.
 * <p>
 * A room is left by the side the game then puts the player opposite to: leaving on the right,
 * the column leaps from its most to its least, and the leap says the side. A leap from anywhere
 * else, or in the column and the row at once, is a teleport or a death and says nothing.
 */
public final class PlayerVariable {
  static final int FROM = 0x5B00, TO = 0x10000, KEYS = 45;
  /**
   * How sure a key's way has to be: the rises against the falls under it, in standard deviations
   * of what chance gives, and as a share of the two, since a counter of four goes one way three
   * times in four however long it is watched.
   */
  static final double SURE = 3, CONSISTENT = 0.75;
  /** Changes with no key held, as a share of all its changes, beyond which a byte is not the player's. */
  static final double UNBIDDEN = 0.5;
  /** Frames a key is still taken as held after it was seen: the game moves a while after it reads. */
  static final int LINGER = 3;
  /** Frames of the position kept, to look back from a room change to before the player was moved. */
  static final int KEPT = 64;
  /** Frames the byte has to have been followed before its word is taken: the first to show is not always the one. */
  static final int TRUSTED_AFTER = 1500;
  /** Frames with no key held and the player not moving after which nobody is playing: a title showing rooms is not a game. */
  static final int IDLE = 200;
  /** How common the other step has to be against the step along for the byte to pack a row with the column. */
  static final double PACKED = 0.2;
  /** Leaps a byte has to take without the column leaping, net of those with, to be the row; and how small a leap may be. */
  static final int ROW_LEAPS = 3, LEAP = 16;
  /** The share of a position's range, at either end, a leaving leaps from and lands in: a leap from anywhere else is a teleport or a death. */
  static final int EDGE = 4;

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
  private int followed, lastMovedAt = -IDLE, lastKeyAt = -IDLE, steppedAt = -1;
  private boolean learned;
  private final Map<Integer, Integer> stepsAlong = new HashMap<>(), otherSteps = new HashMap<>();
  private int xMin = Integer.MAX_VALUE, xMax = -1, yMin = Integer.MAX_VALUE, yMax = -1, modulusWas;
  private final byte[][] remembered = new byte[KEPT][TO];
  private final int[] rowLeaps = new int[TO], leaps = new int[TO];
  private int rowVotings, enteredAt, arrivedAt;
  private int row = -1;
  private boolean rowWide;
  private int rowMin = Integer.MAX_VALUE, rowMax = -1;

  public PlayerVariable(RoomVariable.Ram ram) {
    this.ram = ram;
  }

  /** At the end of a frame, with the keys the game saw held during it. */
  public void frame(long keys) {
    frame++;
    if (keys != 0) lastKeyAt = frame;
    lately[frame % LINGER] = keys;
    long held = 0;
    for (long seen : lately) held |= seen;
    int wrapped = -2;
    for (int address = FROM; address < TO; address++) {
      byte now = (byte) ram.peek(address);
      remembered[frame % KEPT][address] = now;
      int delta = now - previous[address];
      boolean wraps = Math.abs((now & 0xff) - (previous[address] & 0xff)) > 128;
      previous[address] = now;
      if (delta == 0 || !started) continue;
      if (Math.abs(delta) >= LEAP) leaps[address]++;
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
    if (frame % 50 == 0 && !learned) choose();
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

  /** Starts out knowing what another one found: the byte, how it is read, and where its edges are. */
  public void learnedFrom(PlayerVariable other) {
    player = other.player;
    wide = other.wide;
    stepsAlong.putAll(other.stepsAlong);
    otherSteps.putAll(other.otherSteps);
    xMin = other.xMin;
    xMax = other.xMax;
    yMin = other.yMin;
    yMax = other.yMax;
    modulusWas = other.modulusWas;
    row = other.row;
    rowWide = other.rowWide;
    rowMin = other.rowMin;
    rowMax = other.rowMax;
    System.arraycopy(other.rowLeaps, 0, rowLeaps, 0, TO);
    System.arraycopy(other.leaps, 0, leaps, 0, TO);
    rowVotings = other.rowVotings;
    followed = TRUSTED_AFTER;
    learned = true;
  }

  /** A room has been arrived at, with the way in told already or still to be looked for while the player stands still. */
  public void roomEntered(boolean wayTold) {
    arrivedAt = frame;
    if (wayTold) waySettled();
  }

  /**
   * The way into the room now is settled, told or given up on: the leap that brought the player
   * here is not a leaving any more, and whether the column leapt in it says which bytes may be the row.
   */
  public void waySettled() {
    if (player != null && !packed()) lookForTheRow(leapOf(this::at, 0) != null);
    enteredAt = frame;
  }

  /** Whether nothing has moved the player since arriving: a leap now still belongs to the way in. */
  public boolean stillSinceArriving() {
    return lastMovedAt < arrivedAt;
  }

  /** Whether the player took a step in the frame just seen: walked, as against being carried or falling. */
  public boolean movedNow() {
    return player != null && steppedAt == frame;
  }

  /** Whether the player has been found, so that where they are can be asked. */
  public boolean known() {
    return player != null;
  }

  /** Whether the player has been followed long enough to be believed: what a map made again from the start can be given. */
  public boolean trusted() {
    return player != null && followed >= TRUSTED_AFTER;
  }

  /** Whether up and down can be told as well as left and right: the row is packed with the column or has been found. */
  public boolean rowKnown() {
    return player != null && (packed() || row >= 0);
  }

  /** What a map made again from the start is taught of the player, to be made once more when the game being played says otherwise. */
  public String taught() {
    return player == null ? "no player" : String.format("0x%04X%s row %d", player.address, wide ? " wide" : "", row);
  }

  public Found player() {
    return player;
  }

  /** Whether the found byte and the next are read as one number. */
  public boolean wide() {
    return wide;
  }

  /**
   * The side the room was just left by, from the leap the game made the position take to put the
   * player at the next room's door: the column from its most to its least is leaving on the
   * right, the row likewise is leaving at the bottom. Null until the player has been followed for
   * a while, when nobody was being played, and when nothing has leapt since the last room was
   * settled into: asked as the next room starts being drawn it may be too soon, and asked again
   * on arriving, or after, it is not.
   */
  public Side sideOfLeaving() {
    if (player == null || followed < TRUSTED_AFTER || frame - lastPlayedAt() > IDLE) return null;
    int down = stepDown();
    int[] leap = leapOf(this::at, packed() ? down : 0);
    if (packed()) {
      if (leap == null) return null;
      int fromRow = leap[0] / down, toRow = leap[1] / down, fromColumn = leap[0] % down, toColumn = leap[1] % down;
      boolean rowLeapt = Math.abs(toRow - fromRow) >= 2, columnLeapt = Math.abs(toColumn - fromColumn) >= Math.max(LEAP, 8 * stepAlong());
      if (rowLeapt && columnLeapt) return null;
      return rowLeapt ? across(fromRow, toRow, yMin, yMax, Side.DOWN, Side.UP) : across(fromColumn, toColumn, xMin, xMax, Side.RIGHT, Side.LEFT);
    }
    int[] rowLeap = row < 0 ? null : leapOf(this::rowValue, 0);
    if (leap != null && rowLeap != null) return null;
    if (leap != null) return across(leap[0], leap[1], xMin, xMax, Side.RIGHT, Side.LEFT);
    return rowLeap == null ? null : across(rowLeap[0], rowLeap[1], rowMin, rowMax, Side.DOWN, Side.UP);
  }

  /**
   * The side a leap from one end of the position's range to the other leaves by: the game puts
   * the player at the door opposite the one they went out of. From the top end to the bottom end
   * is the first side, the other way the second; from anywhere else it is no side, as a teleport
   * or a death puts the player wherever. Nor is a leap in both the column and the row, which no
   * door does: the caller sees to that.
   */
  private static Side across(int from, int to, int min, int max, Side fromTheTop, Side fromTheBottom) {
    int margin = (max - min) / EDGE;
    if (from >= max - margin && to <= min + margin) return fromTheTop;
    if (from <= min + margin && to >= max - margin) return fromTheBottom;
    return null;
  }

  /**
   * The leap a position took lately, as what it was before and after the frames it leapt in, which
   * can be more than one: the game may set the column in one frame and the row in the next. Null
   * when it only ever stepped.
   */
  private int[] leapOf(java.util.function.IntUnaryOperator valueBack, int rowSize) {
    int threshold = Math.max(LEAP, 8 * stepAlong());
    // Only leaps since the room was entered: the one that brought the player in is still in the window.
    int window = Math.min(KEPT, Math.max(1, frame - enteredAt));
    int end = -1;
    for (int back = 1; back < window && end < 0; back++) if (leaps(valueBack.applyAsInt(back), valueBack.applyAsInt(back - 1), rowSize, threshold)) end = back;
    if (end < 0) return null;
    int start = end;
    while (start + 1 < window && leaps(valueBack.applyAsInt(start + 1), valueBack.applyAsInt(start), rowSize, threshold)) start++;
    return new int[]{valueBack.applyAsInt(start), valueBack.applyAsInt(end - 1)};
  }

  private static boolean leaps(int from, int to, int rowSize, int threshold) {
    if (rowSize > 0) return Math.abs(to / rowSize - from / rowSize) >= 2 || Math.abs(to % rowSize - from % rowSize) >= threshold;
    return Math.abs(to - from) >= threshold;
  }

  /** Whether the other step is common enough for the byte to hold a row above the column. */
  private boolean packed() {
    int across = stepAlong(), down = stepDown();
    return down > across && otherSteps.getOrDefault(down, 0) >= PACKED * stepsAlong.getOrDefault(across, 1);
  }

  /**
   * The bytes that leapt lately, with the column still, are told for the row; those that leapt
   * with the column leaping too are told against, since what is drawn afresh leaps either way.
   */
  private void lookForTheRow(boolean columnLeapt) {
    rowVotings++;
    int skip = wide ? 2 : 1;
    for (int address = FROM; address < TO; address++) {
      // Not the column itself, not what seldom changes, and not what leaps all the time, like the stack.
      if (address >= player.address && address < player.address + skip || leaps[address] > 4 * rowVotings) continue;
      int leap = 0;
      for (int back = 1; back < KEPT; back++) {
        int change = (remembered[(frame - back + 1 + KEPT) % KEPT][address] & 0xff) - (remembered[(frame - back + KEPT) % KEPT][address] & 0xff);
        if (Math.abs(change) > Math.abs(leap)) leap = change;
      }
      if (Math.abs(leap) >= LEAP) rowLeaps[address] += columnLeapt ? -1 : 1;
    }
    int best = -1;
    for (int address = FROM; address < TO; address++) if (rowLeaps[address] >= ROW_LEAPS && (best < 0 || rowLeaps[address] > rowLeaps[best])) best = address;
    if (best >= 0 && best != row) {
      row = best;
      rowWide = carries[row] > 0 && carries[row] >= notCarries[row];
      rowMin = Integer.MAX_VALUE;
      rowMax = -1;
    }
  }

  private int rowValue(int back) {
    byte[] then = remembered[(frame - back + KEPT) % KEPT];
    int low = then[row] & 0xff;
    return rowWide ? low | (then[row + 1] & 0xff) << 8 : low;
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
    return packed() ? down : wide ? 0x10000 : 256;
  }

  private void follow(long held) {
    int now = value(), was = at(1);
    kept[frame % KEPT] = now;
    if (++followed < 2) return;
    int delta = wide ? now - was : (byte) (now - was);
    if (delta != 0) {
      boolean along = (held >> player.up & 1) != 0 || (held >> player.down & 1) != 0;
      (along ? stepsAlong : otherSteps).merge(Math.abs(delta), 1, Integer::sum);
      // A move is a step or two, which a jump or a fall is; being put somewhere, as a game does on
      // starting, on a death or at a door, is not. Unpacked, the other steps are such leaps.
      if (Math.abs(delta) <= 2 * (packed() ? Math.max(stepAlong(), stepDown()) : stepAlong())) lastMovedAt = steppedAt = frame;
    }
    // Carried up or down, as by a lift, the player has moved as much as by walking.
    if (row >= 0 && rowValue(0) != rowValue(1) && Math.abs(rowValue(0) - rowValue(1)) < LEAP) lastMovedAt = frame;
    // Where the byte stands while it has not moved for a while, as on a title, is not where the player can be.
    if (frame - lastMovedAt > IDLE) return;
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
    if (row >= 0) {
      rowMin = Math.min(rowMin, rowValue(0));
      rowMax = Math.max(rowMax, rowValue(0));
    }
  }

  /** The last frame anybody played: a key held, or the player moved, as a lift moves them with no key held. */
  private int lastPlayedAt() {
    return Math.max(lastMovedAt, lastKeyAt);
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
    for (int back = 0; back < 40; back++) values.append(at(back)).append(back < 39 ? "," : "");
    return String.format("0x%04X wide %b followed %d across %d down %d packed %b x %d..%d y %d..%d; row byte %s; last values %s",
        player.address, wide, followed, across, down, packed(), xMin, xMax, yMin, yMax, row < 0 ? "none" : String.format("0x%04X%s %d..%d", row, rowWide ? " wide" : "", rowMin, rowMax), values);
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
      if (changes[address] == 0 || unbidden[address] > UNBIDDEN * changes[address]) continue;
      // The key with the most evidence each way: one seen a few times always sending it up says
      // less than one seen hundreds of times sending it up nearly always.
      int up = -1, down = -1;
      double upEvidence = SURE, downEvidence = SURE;
      for (int key = 0; key < KEYS; key++) {
        if (upUnder[key] == null) continue;
        int rises = upUnder[key][address], falls = downUnder[key][address];
        if (Math.max(rises, falls) <= CONSISTENT * (rises + falls)) continue;
        double evidence = Math.abs(rises - falls) / Math.sqrt(rises + falls);
        if (rises > falls && evidence >= upEvidence) {
          upEvidence = evidence;
          up = key;
        }
        if (falls > rises && evidence >= downEvidence) {
          downEvidence = evidence;
          down = key;
        }
      }
      if (up < 0 || down < 0) continue;
      double upSure = upUnder[up][address] / (double) (upUnder[up][address] + downUnder[up][address]);
      double downSure = downUnder[down][address] / (double) (upUnder[down][address] + downUnder[down][address]);
      int explained = upUnder[up][address] + downUnder[up][address] + upUnder[down][address] + downUnder[down][address];
      // The margin over what a counter that wraps round shows, times how much the keys explain: a
      // position goes the key's way almost always, a counter of four does three times in four.
      found.add(new Found(address, up, down, changes[address], (upSure + downSure - 2 * CONSISTENT) * explained));
    }
    found.sort(Comparator.comparingDouble((Found one) -> -one.score));
    return found;
  }
}
