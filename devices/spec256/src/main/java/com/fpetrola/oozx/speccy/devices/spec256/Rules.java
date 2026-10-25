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


package com.fpetrola.oozx.speccy.devices.spec256;


import com.google.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * What a game says about how its colours meet the machine's own, in the file EmuZWin writes
 * beside it: lines of {@code key=value}, and this is the only place those keys are known.
 * <p>
 * Most games say nothing and the defaults are what they get. The defaults are not "do nothing":
 * the top sixty-four colours are mixed with the attribute the machine would have painted with,
 * which is what a game counts on when it wants a highlight to follow the colour of the room.
 */
@Singleton
public final class Rules {
  /** With a picture underneath, the last colour lets it through as the first one does. */
  public boolean backgroundOverTheLast;
  /** The first colour is the attribute's paper and the last one its ink, so an uncoloured graphic looks as it did. */
  public boolean paperForNoneInkForAll;
  /** Where a cell's ink and paper are the same, that colour covers it: it is how a game clears. */
  public boolean hiddenWhereInkIsPaper = true;
  /**
   * Whether a cell that flashes lets the picture underneath through while it is reversed, which is
   * how a game with a picture reveals it. A game that flashes for its own reasons - to say that a
   * thing kills - was not drawn for that, and against a picture its hazards read as blinking holes.
   */
  public boolean pictureThroughTheFlash = true;
  /** How many colours from the top, and from the bottom, are mixed half and half with the attribute's. */
  public int mixedFromTheTop = 64;
  public int mixedFromTheBottom;
  /** Whether the mix uses the bright eight when the attribute says bright, or the plain ones anyway. */
  public boolean brightInTheMix;
  /** Whether the three logical instructions work on colours rather than on bits, in the followers. */
  public boolean levelledOr, levelledAnd, levelledXor;

  /**
   * Whether a follower reads where the machine reads. Only where a picture is: eight planes that
   * all say the same thing are a table, and a follower that looks one up with a colour of its own
   * is right to go where the machine did not - that is how a mirrored sprite keeps its colours.
   */
  public boolean readingWhereTheMachineReads = true;

  /** Which registers a follower takes from the machine before every instruction. */
  public String registersTaken = Alignment.BY_DEFAULT;

  public Rules() {
    asTheyComeByDefault();
  }

  public void asTheyComeByDefault() {
    backgroundOverTheLast = false;
    paperForNoneInkForAll = false;
    hiddenWhereInkIsPaper = true;
    pictureThroughTheFlash = true;
    mixedFromTheTop = 64;
    mixedFromTheBottom = 0;
    brightInTheMix = false;
    levelledOr = levelledAnd = levelledXor = false;
    readingWhereTheMachineReads = true;
    registersTaken = Alignment.BY_DEFAULT;
  }

  /**
   * How many colours a game says are mixed. A game that says none says nothing: measured against
   * the Renegade running in the emulator these rules come from, a file that says zero still has
   * the top sixty-four mixed, so zero is the key not being said rather than the answer being none.
   */
  private int saying(String value, int byDefault) {
    int said = number(value, byDefault);
    return said == 0 ? byDefault : said;
  }

  /** Whether a colour is one of those mixed with what the machine would have painted. */
  public boolean mixed(int colour) {
    return colour < mixedFromTheBottom || colour > 255 - mixedFromTheTop;
  }

  /** A key a game's file says, what these rules say for it, and what it sets when a file says it. */
  private record Key(String name, Function<Rules, Object> said, BiConsumer<Rules, String> says) {
  }

  /** The keys EmuZWin reads, and one of this emulator's own, which a window sets and EmuZWin passes over. */
  private static final List<Key> KEYS = List.of(
      new Key("BkOverFF", rules -> rules.backgroundOverTheLast, (rules, value) -> rules.backgroundOverTheLast = on(value)),
      new Key("Paper00InkFF", rules -> rules.paperForNoneInkForAll, (rules, value) -> rules.paperForNoneInkForAll = on(value)),
      new Key("HideSameInkPaper", rules -> rules.hiddenWhereInkIsPaper, (rules, value) -> rules.hiddenWhereInkIsPaper = on(value)),
      new Key("BkThroughFlash", rules -> rules.pictureThroughTheFlash, (rules, value) -> rules.pictureThroughTheFlash = on(value)),
      new Key("UpColorsMixed", rules -> rules.mixedFromTheTop, (rules, value) -> rules.mixedFromTheTop = rules.saying(value, rules.mixedFromTheTop)),
      new Key("DownColorsMixed", rules -> rules.mixedFromTheBottom, (rules, value) -> rules.mixedFromTheBottom = number(value, rules.mixedFromTheBottom)),
      new Key("UseBrightInMix", rules -> rules.brightInTheMix, (rules, value) -> rules.brightInTheMix = on(value)),
      new Key("GFXLeveledOR", rules -> rules.levelledOr, (rules, value) -> rules.levelledOr = on(value)),
      new Key("GFXLeveledAND", rules -> rules.levelledAnd, (rules, value) -> rules.levelledAnd = on(value)),
      new Key("GFXLeveledXOR", rules -> rules.levelledXor, (rules, value) -> rules.levelledXor = on(value)),
      new Key("ZXPAlignRegs", rules -> rules.registersTaken, (rules, value) -> rules.registersTaken = value),
      new Key("ReadsWhereTheMachineReads", rules -> rules.readingWhereTheMachineReads, (rules, value) -> rules.readingWhereTheMachineReads = on(value)));

  /**
   * A game's own file, if it brought one. Whatever it says that is not one of these is some other
   * emulator's business, and whatever it says badly is left at its default.
   */
  public void read(byte[] said) {
    asTheyComeByDefault();
    if (said == null) return;
    for (String line : new String(said, StandardCharsets.ISO_8859_1).split("\\R")) {
      int equals = line.indexOf('=');
      if (equals <= 0) continue;
      String key = line.substring(0, equals).trim(), value = line.substring(equals + 1).trim();
      KEYS.stream().filter(one -> one.name.equalsIgnoreCase(key)).forEach(one -> one.says.accept(this, value));
    }
  }

  /** These rules as a game's file says them, which read back are these rules again. */
  public byte[] said() {
    return KEYS.stream().map(key -> key.name + "=" + inAFile(key.said.apply(this))).collect(Collectors.joining("\n"))
        .getBytes(StandardCharsets.ISO_8859_1);
  }

  private static String inAFile(Object value) {
    return value instanceof Boolean yes ? yes ? "1" : "0" : String.valueOf(value);
  }

  private static boolean on(String value) {
    return !value.isEmpty() && !"0".equals(value);
  }

  private static int number(String value, int otherwise) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException notANumber) {
      return otherwise;
    }
  }
}
