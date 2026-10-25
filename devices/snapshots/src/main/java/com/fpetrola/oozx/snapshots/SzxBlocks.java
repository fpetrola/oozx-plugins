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

package com.fpetrola.oozx.snapshots;

import com.fpetrola.oozx.formats.Codec;
import com.fpetrola.oozx.formats.Cursor;
import com.fpetrola.oozx.formats.Fixed;
import com.fpetrola.oozx.formats.Pages;
import com.fpetrola.oozx.formats.Refused;
import com.fpetrola.oozx.formats.Region;
import com.fpetrola.oozx.formats.Sink;
import com.fpetrola.oozx.formats.SnapshotFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The blocks of an SZX after its header: each an id of four letters, a length, and its bytes. The
 * ones a part's table knows are kept as stretches by id, by page where they come a page a block, or
 * by number where one comes for each drive;
 * the pages of RAM go to their banks, packed with zlib or not; the rest are passed over and said.
 * Written, a block goes only if some part wrote into it, one a page for every page written, and the
 * creator always goes first.
 */
final class SzxBlocks implements Region {

  private static final int COMPRESSED = 0x01;

  /** The blocks the tables of the parts read and write, each written as long as it is declared. */
  private final List<Fixed> known;

  SzxBlocks(List<Fixed> known) {
    this.known = known;
  }

  @Override
  public void parse(Cursor in, SnapshotFile into) {
    boolean swapsAf = false;
    while (in.left() > 0) {
      String id = new String(in.take(4), StandardCharsets.ISO_8859_1);
      int length = in.u32();
      if (length < 0 || length > in.left()) {
        throw new Refused("the block " + id + " says it has " + length + " bytes, and there are " + in.left());
      }
      byte[] payload = in.take(length);
      if (id.equals("RAMP")) {
        bank(payload, into);
      } else if (id.equals("CRTR")) {
        swapsAf = swapsAf(payload);
      } else {
        Fixed block = known.stream().filter(one -> one.toString().equals(id) && one.takes(payload)).findFirst().orElse(null);
        if (block == null) {
          into.note("skipped: the block " + id.trim() + " (" + length + " bytes)");
          into.carry(id.replaceFirst("\0+$", ""), payload);
        } else if (block.isEach()) {
          into.each(block, payload);
        } else if (block.isPaged()) {
          Page page = Page.of(payload, block.length());
          into.page(block, page.number(), page.bytes());
        } else {
          into.stretch(block, payload);
        }
      }
    }
    if (!into.has(SzxFormat.Z80R)) {
      into.note("the file has no Z80R: the processor stays as the machine starts (decision 8)");
    }
    if (into.pages().isEmpty()) {
      into.note("the file has no RAMP: the memory stays as the machine starts (decision 8)");
    }
    if (swapsAf && into.has(SzxFormat.Z80R)) {
      swap(into, 0);
      swap(into, 8);
    }
  }

  /**
   * libspectrum before 0.5.1 wrote A and F the wrong way round, and says so in the creator's custom
   * data; libspectrum reads those files swapped back, and so does this.
   */
  static boolean swapsAf(byte[] creator) {
    if (creator.length <= 36) return false;
    String custom = new String(creator, 36, creator.length - 36, StandardCharsets.ISO_8859_1);
    java.util.regex.Matcher version = java.util.regex.Pattern.compile("libspectrum: (\\d+)\\.(\\d+)\\.(\\d+)").matcher(custom);
    if (!version.find()) return false;
    int major = Integer.parseInt(version.group(1));
    int minor = Integer.parseInt(version.group(2));
    int patch = Integer.parseInt(version.group(3));
    return major == 0 && (minor < 5 || minor == 5 && patch == 0);
  }

  private static void swap(SnapshotFile file, int offset) {
    int first = file.u8(SzxFormat.Z80R, offset);
    file.u8(SzxFormat.Z80R, offset, file.u8(SzxFormat.Z80R, offset + 1));
    file.u8(SzxFormat.Z80R, offset + 1, first);
  }

  private static void bank(byte[] payload, SnapshotFile into) {
    Page page = Page.of(payload, Pages.LENGTH);
    if (into.shape().banks().contains(page.number())) {
      into.page(page.number(), page.bytes());
    } else {
      into.note("skipped: page " + page.number() + ", a bank this machine does not have");
    }
  }

  /** A page as RAMP keeps one, and every block that keeps memory a page at a time: flags, the first saying it is packed, its number, and the page. */
  private record Page(int number, byte[] bytes) {
    static Page of(byte[] payload, int length) {
      if (payload.length < 3) {
        throw new Refused("a page block of " + payload.length + " bytes");
      }
      byte[] data = java.util.Arrays.copyOfRange(payload, 3, payload.length);
      return new Page(payload[2] & 0xff, (payload[0] & COMPRESSED) != 0 ? Codec.ZLIB.decode(data, length) : Codec.RAW.decode(data, length));
    }

    byte[] block() {
      return new Sink().u16(COMPRESSED).u8(number).bytes(Codec.ZLIB.encode(bytes)).toBytes();
    }
  }

  @Override
  public void prepare(SnapshotFile file) {
    known.stream().filter(block -> !block.isPaged() && !block.isEach()).forEach(block -> file.stretch(block, new byte[block.length()]));
  }

  @Override
  public void assemble(SnapshotFile from, Sink out) {
    block(out, "CRTR", creator());
    for (Fixed block : known) {
      if (block.isPaged()) {
        from.pages(block).forEach((number, page) -> block(out, block.toString(), new Page(number, page).block()));
      } else if (block.isEach()) {
        from.each(block).values().forEach(bytes -> block(out, block.toString(), bytes.toBytes()));
      } else if (from.isWritten(block)) {
        block(out, block.toString(), from.bytes(block).toBytes());
      }
    }
    for (int bank : from.shape().banks()) {
      block(out, "RAMP", new Page(bank, from.pageOrZeros(bank)).block());
    }
    from.carried().forEach((name, payload) -> block(out, fourLetters(name), payload));
  }

  /** A block's name is four bytes: a shorter one is filled with NULs, as "AY\0\0" is, and carried back without them. */
  private static String fourLetters(String name) {
    if (name.isEmpty() || name.length() > 4) {
      throw new IllegalArgumentException("an SZX block is named in one to four letters, and not '" + name + "'");
    }
    return (name + "\0\0\0").substring(0, 4);
  }

  /** "OOZX", and its version: who wrote it (decision 1). */
  private static byte[] creator() {
    byte[] name = new byte[32];
    byte[] oozx = "OOZX".getBytes(StandardCharsets.ISO_8859_1);
    System.arraycopy(oozx, 0, name, 0, oozx.length);
    return new Sink().bytes(name).u16(0).u16(2).toBytes();
  }

  private static void block(Sink out, String id, byte[] payload) {
    out.bytes(id.getBytes(StandardCharsets.ISO_8859_1)).u32(payload.length).bytes(payload);
  }
}
