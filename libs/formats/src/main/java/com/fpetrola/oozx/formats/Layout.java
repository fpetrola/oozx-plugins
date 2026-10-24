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

package com.fpetrola.oozx.formats;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * A table for one kind of part: each of its fields at a place of a stretch of the file. Reading and
 * writing are the same table used in the two directions. A place in a stretch the file does not
 * have is not there: one table serves the shapes of a format that have it and those that do not.
 * A table never changes; every line gives a new one.
 */
public final class Layout<P> {

  /** One line of the table. */
  interface Place<P> {
    void read(SnapshotFile file, P part);

    void write(P part, SnapshotFile file);

    List<Object> fields();
  }

  /** A line whose bytes are in one fixed stretch, there only if the file has the stretch and it reaches them. */
  abstract static class InStretch<P> implements Place<P> {
    final Fixed region;
    final int offset;
    final int count;

    InStretch(Fixed region, int offset, int count) {
      this.region = region;
      this.offset = offset;
      this.count = count;
    }

    boolean isIn(SnapshotFile file) {
      return file.has(region, offset, count);
    }

    public final void read(SnapshotFile file, P part) {
      if (isIn(file)) read(file.bytes(region), part);
    }

    public final void write(P part, SnapshotFile file) {
      if (isIn(file)) {
        write(part, file.bytes(region));
        file.wrote(region);
      }
    }

    abstract void read(Bytes bytes, P part);

    abstract void write(P part, Bytes bytes);
  }

  private final List<Place<P>> places;
  private final Predicate<P> written;

  private Layout(List<Place<P>> places, Predicate<P> written) {
    this.places = List.copyOf(places);
    this.written = written;
  }

  public static <P> Layout<P> of() {
    return new Layout<>(List.of(), part -> true);
  }

  /** The table, written only for a part that has something to say: a deck with no tape writes no TAPE. Read whenever the file has it. */
  public Layout<P> writtenWhen(Predicate<P> says) {
    return new Layout<>(places, says);
  }

  /** A byte. */
  public Layout<P> u8(Fixed region, int offset, Field<P, Integer> field) {
    return with(new InStretch<P>(region, offset, 1) {
      void read(Bytes bytes, P part) { field.set(part, bytes.u8(offset)); }
      void write(P part, Bytes bytes) { bytes.u8(offset, field.get(part)); }
      public List<Object> fields() { return List.of(field); }
    });
  }

  /** Two bytes, little-endian. */
  public Layout<P> u16(Fixed region, int offset, Field<P, Integer> field) {
    return with(new InStretch<P>(region, offset, 2) {
      void read(Bytes bytes, P part) { field.set(part, bytes.u16(offset)); }
      void write(P part, Bytes bytes) { bytes.u16(offset, field.get(part)); }
      public List<Object> fields() { return List.of(field); }
    });
  }

  /** Four bytes, little-endian. */
  public Layout<P> u32(Fixed region, int offset, Field<P, Integer> field) {
    return with(new InStretch<P>(region, offset, 4) {
      void read(Bytes bytes, P part) { field.set(part, bytes.u32(offset)); }
      void write(P part, Bytes bytes) { bytes.u32(offset, field.get(part)); }
      public List<Object> fields() { return List.of(field); }
    });
  }

  /** One bit of a byte, the rest being somebody else's. */
  public Layout<P> bit(Fixed region, int offset, int bit, Field<P, Boolean> field) {
    return with(new InStretch<P>(region, offset, 1) {
      void read(Bytes bytes, P part) { field.set(part, (bytes.u8(offset) >> bit & 1) != 0); }
      void write(P part, Bytes bytes) { bytes.u8(offset, bytes.u8(offset) & ~(1 << bit) | (field.get(part) ? 1 << bit : 0)); }
      public List<Object> fields() { return List.of(field); }
    });
  }

  /** Some bits of a byte, from that shift. */
  public Layout<P> bits(Fixed region, int offset, int shift, int mask, Field<P, Integer> field) {
    return with(new InStretch<P>(region, offset, 1) {
      void read(Bytes bytes, P part) { field.set(part, bytes.u8(offset) >> shift & mask); }
      void write(P part, Bytes bytes) { bytes.u8(offset, bytes.u8(offset) & ~(mask << shift) | (field.get(part) & mask) << shift); }
      public List<Object> fields() { return List.of(field); }
    });
  }

  /** A byte that is true when it is anything but 0, and written as 1. */
  public Layout<P> flag(Fixed region, int offset, Field<P, Boolean> field) {
    return with(new InStretch<P>(region, offset, 1) {
      void read(Bytes bytes, P part) { field.set(part, bytes.u8(offset) != 0); }
      void write(P part, Bytes bytes) { bytes.u8(offset, field.get(part) ? 1 : 0); }
      public List<Object> fields() { return List.of(field); }
    });
  }

  /** Some bits of a byte that are a number for a value. */
  public <E> Layout<P> code(Fixed region, int offset, int shift, int mask, Field<P, E> field, Codes<E> codes) {
    return with(new InStretch<P>(region, offset, 1) {
      void read(Bytes bytes, P part) { field.set(part, codes.decode(bytes.u8(offset) >> shift & mask)); }
      void write(P part, Bytes bytes) { bytes.u8(offset, bytes.u8(offset) & ~(mask << shift) | (codes.encode(field.get(part)) & mask) << shift); }
      public List<Object> fields() { return List.of(field); }
    });
  }

  /** So many bytes as they are. */
  public Layout<P> bytes(Fixed region, int offset, int count, Field<P, byte[]> field) {
    return with(new InStretch<P>(region, offset, count) {
      void read(Bytes bytes, P part) { field.set(part, bytes.slice(offset, count)); }
      void write(P part, Bytes bytes) { bytes.put(offset, field.get(part)); }
      public List<Object> fields() { return List.of(field); }
    });
  }

  /**
   * What a field is taken to be when reading, because this format does not carry it: MEMPTR is 0
   * after a file that does not say it, as libspectrum and Fuse leave it. Nothing is written.
   */
  public <V> Layout<P> assuming(Field<P, V> field, V value) {
    return with(new Place<P>() {
      public void read(SnapshotFile file, P part) { field.set(part, value); }
      public void write(P part, SnapshotFile file) { }
      public List<Object> fields() { return List.of(); }
    });
  }

  /** Bytes with a rule of their own, when the file reaches them: a frame counter, R split in two. */
  public Layout<P> encoded(Fixed region, int offset, int count, Encoding<P> encoding) {
    return with(new Place<P>() {
      public void read(SnapshotFile file, P part) {
        if (file.has(region, offset, count)) encoding.read(file.bytes(region), part, file);
      }

      public void write(P part, SnapshotFile file) {
        if (file.has(region, offset, count)) {
          encoding.write(part, file.bytes(region), file);
          file.wrote(region);
        }
      }

      public List<Object> fields() {
        return encoding.fields();
      }
    });
  }

  /** The line before this also sets another field, with what it read: an SNA keeps one IFF for both. */
  public Layout<P> alsoSets(Field<P, Boolean> other) {
    Place<P> last = places.get(places.size() - 1);
    @SuppressWarnings("unchecked")
    Field<P, Boolean> read = (Field<P, Boolean>) last.fields().get(0);
    List<Place<P>> changed = new ArrayList<>(places);
    changed.set(changed.size() - 1, new Place<P>() {
      public void read(SnapshotFile file, P part) {
        last.read(file, part);
        if (last instanceof InStretch<P> stretch && stretch.isIn(file)) other.set(part, read.get(part));
      }

      public void write(P part, SnapshotFile file) {
        last.write(part, file);
      }

      public List<Object> fields() {
        return List.of(read, other);
      }
    });
    return new Layout<>(changed, written);
  }

  /** Every page of the file, to its bank of the part; and every bank the shape has, from the part. */
  public Layout<P> pages(PageField<P> field) {
    return with(new Place<P>() {
      public void read(SnapshotFile file, P part) {
        file.pages().forEach(bank -> field.set(part, bank, file.pageOrZeros(bank)));
      }

      public void write(P part, SnapshotFile file) {
        file.shape().banks().forEach(bank -> file.page(bank, field.get(part, bank)));
      }

      public List<Object> fields() {
        return List.of(field);
      }
    });
  }

  /**
   * Every page the file has of that block, to the part's page of that number; and every page the
   * part has, as many as it says, to the file. A page the part does not have is said and left.
   */
  public Layout<P> pages(Fixed block, ToIntFunction<P> count, PageField<P> field) {
    return with(new Place<P>() {
      public void read(SnapshotFile file, P part) {
        file.pages(block).forEach((number, page) -> {
          if (number < count.applyAsInt(part)) field.set(part, number, page);
          else file.note("skipped: page " + number + " of " + block + ", which this part does not have");
        });
      }

      public void write(P part, SnapshotFile file) {
        for (int number = 0; number < count.applyAsInt(part); number++) file.page(block, number, field.get(part, number));
      }

      public List<Object> fields() {
        return List.of(field);
      }
    });
  }

  /**
   * What a block carries after its fields, to its end, that many bytes: a ROM, the RAM of something
   * plugged in. Packed with zlib when that bit of that byte of the block is set, and written packed,
   * with the bit set. Nothing is read from a block that ends with its fields, and nothing is written
   * for a part that has none.
   */
  public Layout<P> rest(Fixed block, int flagAt, int bit, int length, Field<P, byte[]> field) {
    return rest(block, flagAt, bit, -1, length, field);
  }

  /**
   * The same, kept in as many bytes as four bytes at that place of the block say: what follows them is something
   * else's, as the ROM after the RAM of a disk interface.
   */
  public Layout<P> rest(Fixed block, int flagAt, int bit, int lengthAt, int length, Field<P, byte[]> field) {
    return with(new Rest<>(block, lengthAt, length, field, bytes -> (bytes.u8(flagAt) >> bit & 1) != 0,
        bytes -> bytes.or(flagAt, 1 << bit)));
  }

  /** The same, always packed, and how long it is packed in four bytes at that place of the block: a cartridge. */
  public Layout<P> packedRest(Fixed block, int lengthAt, int length, Field<P, byte[]> field) {
    return with(new Rest<>(block, lengthAt, length, field, bytes -> true, bytes -> { }));
  }

  /**
   * A block for each one of something a part has a number of, as many as it says: a disk in each drive. The table for
   * one of them reads its block and writes it, numbered from first; one with nothing to say writes none.
   */
  public <O> Layout<P> each(Fixed block, ToIntFunction<P> count, int first, BiFunction<P, Integer, O> one, Layout<O> table) {
    return with(new Place<P>() {
      public void read(SnapshotFile file, P part) {
        file.each(block).forEach((number, bytes) -> {
          if (number < first || number - first >= count.applyAsInt(part)) {
            file.note("skipped: " + block + " " + number + ", which this part does not have");
          } else {
            table.read(alone(file, block, bytes.toBytes()), one.apply(part, number - first));
          }
        });
      }

      public void write(P part, SnapshotFile file) {
        for (int which = 0; which < count.applyAsInt(part); which++) {
          SnapshotFile alone = alone(file, block, new byte[block.length()]);
          table.write(one.apply(part, which), alone);
          if (alone.isWritten(block)) file.each(block, block.said(alone.bytes(block).u8(block.numberAt(), first + which)).toBytes());
        }
      }

      public List<Object> fields() {
        return table.fields();
      }
    });
  }

  /** A file with that block alone in it: what the table for one of many reads and writes. */
  private static SnapshotFile alone(SnapshotFile of, Fixed block, byte[] bytes) {
    SnapshotFile alone = new SnapshotFile(of.shape());
    alone.stretch(block, bytes);
    return alone;
  }

  /**
   * What is in a drive, as a block that keeps it says it: the file it came from, or the image itself where no file
   * has it as it is - a disk the machine wrote on, a blank one - and the number the block gives its kind of image.
   */
  public record Medium(String file, byte[] image, int kind) {
    public static Medium inFile(String file, int kind) {
      return new Medium(file, null, kind);
    }

    public static Medium inside(byte[] image, int kind) {
      return new Medium(null, image, kind);
    }

    public boolean isInside() {
      return image != null;
    }
  }

  /**
   * What a block carries after its fields that is the medium in a drive: the name of its file, ended by a nul, or
   * the image itself packed with zlib, as two bits of the byte at flagsAt say. How long the image is once unpacked
   * and how long what follows the fields is go at expandedAt and keptAt, in the blocks that say them; the kind of
   * image goes in the byte at kindAt. A place a block does not have is -1. Nothing is written for a part with nothing
   * in it.
   */
  public Layout<P> medium(Fixed block, int flagsAt, int insideBit, int packedBit, int expandedAt, int keptAt, int kindAt,
                          Field<P, Medium> field) {
    return with(new Place<P>() {
      public void read(SnapshotFile file, P part) {
        if (!file.has(block, block.length(), 1)) return;
        Bytes bytes = file.bytes(block);
        int there = bytes.length() - block.length();
        byte[] kept = bytes.slice(block.length(), keptAt < 0 ? there : Math.min(bytes.u32(keptAt), there));
        int kind = kindAt < 0 ? 0 : bytes.u8(kindAt);
        if ((bytes.u8(flagsAt) >> insideBit & 1) == 0) {
          String named = new String(kept, StandardCharsets.UTF_8);
          field.set(part, Medium.inFile(named.indexOf(0) < 0 ? named : named.substring(0, named.indexOf(0)), kind));
        } else {
          boolean packed = (bytes.u8(flagsAt) >> packedBit & 1) != 0;
          field.set(part, Medium.inside(packed ? Codec.ZLIB.decode(kept, expandedAt < 0 ? -1 : bytes.u32(expandedAt)) : kept, kind));
        }
      }

      public void write(P part, SnapshotFile file) {
        Medium medium = field.get(part);
        if (medium == null || !file.has(block)) return;
        Bytes bytes = file.bytes(block);
        byte[] kept = medium.isInside() ? Codec.ZLIB.encode(medium.image()) : (medium.file() + "\0").getBytes(StandardCharsets.UTF_8);
        if (medium.isInside()) bytes.or(flagsAt, 1 << insideBit | 1 << packedBit);
        if (expandedAt >= 0) bytes.u32(expandedAt, medium.isInside() ? medium.image().length : kept.length);
        if (keptAt >= 0) bytes.u32(keptAt, kept.length);
        if (kindAt >= 0) bytes.u8(kindAt, medium.kind());
        bytes.append(kept);
        file.wrote(block);
      }

      public List<Object> fields() {
        return List.of(field);
      }
    });
  }

  /** The block, written with the part even when there is nothing else to say: that the part is there is what it says. */
  public Layout<P> present(Fixed block) {
    return with(new Place<P>() {
      public void read(SnapshotFile file, P part) { }
      public void write(P part, SnapshotFile file) { if (file.has(block)) file.wrote(block); }
      public List<Object> fields() { return List.of(); }
    });
  }

  /**
   * The bytes after a block's fields, what in the block says whether they are packed, and where it says how many
   * there are, or -1 for all there are.
   */
  private record Rest<P>(Fixed block, int lengthAt, int length, Field<P, byte[]> field, Predicate<Bytes> isPacked,
                         Consumer<Bytes> saysPacked) implements Place<P> {
    public void read(SnapshotFile file, P part) {
      if (!file.has(block, block.length(), 1)) return;
      Bytes bytes = file.bytes(block);
      int there = bytes.length() - block.length();
      byte[] kept = bytes.slice(block.length(), lengthAt < 0 ? there : Math.min(bytes.u32(lengthAt), there));
      field.set(part, isPacked.test(bytes) ? Codec.ZLIB.decode(kept, length) : Codec.RAW.decode(kept, length));
    }

    public void write(P part, SnapshotFile file) {
      byte[] rest = field.get(part);
      if (rest == null || !file.has(block)) return;
      byte[] packed = Codec.ZLIB.encode(rest);
      saysPacked.accept(file.bytes(block));
      if (lengthAt >= 0) file.bytes(block).u32(lengthAt, packed.length);
      file.bytes(block).append(packed);
      file.wrote(block);
    }

    public List<Object> fields() {
      return List.of(field);
    }
  }

  public void read(SnapshotFile file, P part) {
    places.forEach(place -> place.read(file, part));
  }

  public void write(P part, SnapshotFile file) {
    if (written.test(part)) places.forEach(place -> place.write(part, file));
  }

  /** The fields it places, in order: for the tests that hold a table to what it says. */
  public List<Object> fields() {
    return places.stream().flatMap(place -> place.fields().stream()).toList();
  }

  private Layout<P> with(Place<P> place) {
    List<Place<P>> more = new ArrayList<>(places);
    more.add(place);
    return new Layout<>(more, written);
  }
}
