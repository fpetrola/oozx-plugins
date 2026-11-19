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


package com.fpetrola.oozx.speccy.tools.games;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A game's music as it was ripped: an AY file, which is the Z80 code that plays it with the addresses to call. It
 * is played by what it was written for: each song becomes a snapshot of a 128K with the player's memory in place
 * and a few bytes that call it fifty times a second, and a machine runs that.
 * <p>
 * The format wants its own handler where the ROM is, which a snapshot cannot give, so the interrupt goes through a
 * table of this own (IM 2) put where the song left room; a song that sets up its interrupts itself is left to.
 */
final class AyMusic {
  private final byte[] file;
  private final int songs;

  AyMusic(Path path) throws IOException {
    file = Files.readAllBytes(path);
    if (file.length < 20 || !new String(file, 0, 8, java.nio.charset.StandardCharsets.ISO_8859_1).equals("ZXAYEMUL")) {
      throw new IOException("not an AY music file");
    }
    songs = (file[16] & 0xFF) + 1;
  }

  List<String> songs() {
    List<String> names = new ArrayList<>();
    for (int song = 0; song < songs; song++) names.add(text(pointed(pointed(18) + song * 4)));
    return names;
  }

  /** Who wrote it and whatever else the file says of itself, which is often who ripped it. */
  String author() {
    return text(pointed(12));
  }

  String notes() {
    return text(pointed(14));
  }

  /** How long that song lasts in seconds, as the file says; nothing when it does not say, which is a song that loops. */
  int seconds(int song) {
    return word(pointed(pointed(18) + song * 4 + 2) + 4) / 50;
  }

  /** That song as a 128K snapshot, in the SNA format: the 48K in sight, then where it starts, then the other banks. */
  byte[] snapshot(int song) throws IOException {
    int data = pointed(pointed(18) + song * 4 + 2), points = pointed(data + 10), blocks = pointed(data + 12);
    byte[] memory = new byte[0x10000];
    boolean[] taken = new boolean[0x10000];
    int first = word(blocks);
    for (int block = blocks; word(block) != 0; block += 6) {
      int at = word(block), length = Math.min(word(block + 2), 0x10000 - at), from = pointed(block + 4);
      length = Math.min(length, file.length - from);
      System.arraycopy(file, from, memory, at, length);
      java.util.Arrays.fill(taken, at, at + length, true);
    }
    int stack = word(points), init = word(points + 2) == 0 ? first : word(points + 2), interrupt = word(points + 4);
    int player = room(taken, 0x4000, 16);
    // A routine below the RAM is one of the returns the format fills the ROM's place with: nothing to call.
    byte[] calls = interrupt >= 0x4000
        ? new byte[]{(byte) 0xF3, (byte) 0xCD, (byte) init, (byte) (init >> 8), (byte) 0xFB, 0x76, (byte) 0xCD, (byte) interrupt, (byte) (interrupt >> 8), 0x18, (byte) 0xF9}
        : new byte[]{(byte) 0xF3, (byte) 0xCD, (byte) init, (byte) (init >> 8), (byte) 0xFB, 0x76, 0x18, (byte) 0xFC};
    System.arraycopy(calls, 0, memory, player, calls.length);
    int register = 3, mode = 1;
    if (interrupt != 0) {
      // Every vector of the table is the same byte twice, so the handler has to be at an address made of one byte twice.
      for (int high = 0x40; high < 0xFF && mode == 1; high++) {
        int table = room(taken, 0x4100, 257, player, player + 16), handler = high * 257;
        if (table >= 0 && !overlaps(handler, 2, table, 257) && !overlaps(handler, 2, player, 16) && !taken[handler] && !taken[handler + 1]) {
          java.util.Arrays.fill(memory, table, table + 257, (byte) high);
          memory[handler] = (byte) 0xFB;
          memory[handler + 1] = (byte) 0xC9;
          register = table >> 8;
          mode = 2;
        }
      }
    }
    byte high = file[data + 8], low = file[data + 9];
    byte[] sna = new byte[27 + 0xC000 + 4 + 5 * 0x4000];
    sna[0] = (byte) register;
    for (int pair = 1; pair < 19; pair += 2) {
      sna[pair] = low;
      sna[pair + 1] = high;
    }
    sna[19] = 0;
    sna[21] = low;
    sna[22] = high;
    sna[23] = (byte) stack;
    sna[24] = (byte) (stack >> 8);
    sna[25] = (byte) mode;
    System.arraycopy(memory, 0x4000, sna, 27, 0xC000);
    sna[27 + 0xC000] = (byte) player;
    sna[27 + 0xC000 + 1] = (byte) (player >> 8);
    sna[27 + 0xC000 + 2] = 0x10;
    return sna;
  }

  /** A stretch that long which no block of the song uses and nothing put there before does, from that address up. */
  private static int room(boolean[] taken, int from, int length, int... apart) {
    for (int at = (from + 0xFF) & 0xFF00; at + length <= 0x10000; at += 0x100) {
      boolean free = apart.length == 0 || !overlaps(at, length, apart[0], apart[1] - apart[0]);
      for (int each = at; free && each < at + length; each++) free = !taken[each];
      if (free) return at;
    }
    return -1;
  }

  private static boolean overlaps(int one, int length, int other, int otherLength) {
    return one < other + otherLength && other < one + length;
  }

  private int word(int at) {
    return (file[at] & 0xFF) << 8 | file[at + 1] & 0xFF;
  }

  /** Where a pointer leads: they are kept as how far it is from the pointer itself. */
  private int pointed(int at) {
    return at + (short) word(at);
  }

  private String text(int at) {
    int end = at;
    while (end < file.length && file[end] != 0) end++;
    return new String(file, at, end - at, java.nio.charset.StandardCharsets.ISO_8859_1);
  }
}
