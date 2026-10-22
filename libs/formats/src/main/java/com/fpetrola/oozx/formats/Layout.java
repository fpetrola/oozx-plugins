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

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
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

  private Layout(List<Place<P>> places) {
    this.places = List.copyOf(places);
  }

  public static <P> Layout<P> of() {
    return new Layout<>(List.of());
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
    return new Layout<>(changed);
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
   * What a block carries after its fields, to its end, as long as the part's: a ROM, the RAM of
   * something plugged in. Packed with zlib when that bit of that byte of the block is set, and
   * written packed, with the bit set. Nothing is read from a block that ends with its fields, and
   * nothing is written for a part that has none.
   */
  public Layout<P> rest(Fixed block, int flagAt, int bit, Field<P, byte[]> field) {
    return with(new Rest<>(block, field, bytes -> (bytes.u8(flagAt) >> bit & 1) != 0, (bytes, packed) -> bytes.or(flagAt, 1 << bit)));
  }

  /** The same, always packed, and how long it is packed in four bytes at that place of the block: a cartridge. */
  public Layout<P> packedRest(Fixed block, int lengthAt, Field<P, byte[]> field) {
    return with(new Rest<>(block, field, bytes -> true, (bytes, packed) -> bytes.u32(lengthAt, packed.length)));
  }

  /** The block, written with the part even when there is nothing else to say: that the part is there is what it says. */
  public Layout<P> present(Fixed block) {
    return with(new Place<P>() {
      public void read(SnapshotFile file, P part) { }
      public void write(P part, SnapshotFile file) { if (file.has(block)) file.wrote(block); }
      public List<Object> fields() { return List.of(); }
    });
  }

  /** The bytes after a block's fields, and what in the block says whether they are packed. */
  private record Rest<P>(Fixed block, Field<P, byte[]> field, Predicate<Bytes> isPacked, BiConsumer<Bytes, byte[]> saysPacked)
      implements Place<P> {
    public void read(SnapshotFile file, P part) {
      if (!file.has(block, block.length(), 1)) return;
      Bytes bytes = file.bytes(block);
      byte[] kept = bytes.slice(block.length(), bytes.length() - block.length());
      int length = field.get(part).length;
      field.set(part, isPacked.test(bytes) ? Codec.ZLIB.decode(kept, length) : Codec.RAW.decode(kept, length));
    }

    public void write(P part, SnapshotFile file) {
      byte[] rest = field.get(part);
      if (rest == null || !file.has(block)) return;
      byte[] packed = Codec.ZLIB.encode(rest);
      saysPacked.accept(file.bytes(block), packed);
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
    places.forEach(place -> place.write(part, file));
  }

  /** The fields it places, in order: for the tests that hold a table to what it says. */
  public List<Object> fields() {
    return places.stream().flatMap(place -> place.fields().stream()).toList();
  }

  private Layout<P> with(Place<P> place) {
    List<Place<P>> more = new ArrayList<>(places);
    more.add(place);
    return new Layout<>(more);
  }
}
