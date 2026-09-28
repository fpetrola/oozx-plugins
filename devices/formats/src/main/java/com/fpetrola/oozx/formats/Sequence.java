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
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * A record of a file as a sequence of columns, one after the other, each a field of a value: a
 * block of a TZX, of an SZX. Declared once, it reads the columns in order into the value and writes
 * the value's fields back in the same order, so that the two can never disagree.
 */
public final class Sequence<B> {

  /** One column: how it is read, and how it is written from the value. */
  private interface Column<B> {
    Object read(Cursor in);

    void write(B value, Sink out);
  }

  /** What was read, column by column, for the value to be made from. */
  public static final class Read {
    private final List<Object> columns;

    Read(List<Object> columns) {
      this.columns = columns;
    }

    public int i(int column) {
      return (Integer) columns.get(column);
    }

    public byte[] bytes(int column) {
      return (byte[]) columns.get(column);
    }

    public String text(int column) {
      return (String) columns.get(column);
    }

    public int[] ints(int column) {
      return (int[]) columns.get(column);
    }
  }

  private final List<Column<B>> columns;
  private final Function<Read, B> make;

  private Sequence(List<Column<B>> columns, Function<Read, B> make) {
    this.columns = List.copyOf(columns);
    this.make = make;
  }

  public static <B> Sequence<B> of() {
    return new Sequence<>(List.of(), read -> {
      throw new IllegalStateException("a sequence made of nothing");
    });
  }

  /** How the value is made from its columns, once they are read. */
  public Sequence<B> make(Function<Read, B> make) {
    return new Sequence<>(columns, make);
  }

  public Sequence<B> u8(ToIntFunction<B> field) {
    return number(1, field);
  }

  public Sequence<B> u16(ToIntFunction<B> field) {
    return number(2, field);
  }

  public Sequence<B> u24(ToIntFunction<B> field) {
    return number(3, field);
  }

  public Sequence<B> u32(ToIntFunction<B> field) {
    return number(4, field);
  }

  /** Two bytes read as a signed number: a jump back. */
  public Sequence<B> s16(ToIntFunction<B> field) {
    return with(new Column<>() {
      public Object read(Cursor in) { return (int) (short) in.u16(); }
      public void write(B value, Sink out) { out.u16(field.applyAsInt(value)); }
    });
  }

  /** Bytes preceded by their length in so many bytes. */
  public Sequence<B> data(int lengthBytes, Function<B, byte[]> field) {
    return with(new Column<>() {
      public Object read(Cursor in) { return in.take(length(in, lengthBytes)); }
      public void write(B value, Sink out) {
        byte[] data = field.apply(value);
        number(out, lengthBytes, data.length);
        out.bytes(data);
      }
    });
  }

  /** A text preceded by its length in one byte. */
  public Sequence<B> text8(Function<B, String> field) {
    return with(new Column<>() {
      public Object read(Cursor in) { return new String(in.take(in.u8()), StandardCharsets.ISO_8859_1); }
      public void write(B value, Sink out) {
        byte[] text = field.apply(value).getBytes(StandardCharsets.ISO_8859_1);
        out.u8(text.length).bytes(text);
      }
    });
  }

  /** A text of exactly so many bytes, padded with spaces. */
  public Sequence<B> text(int length, Function<B, String> field) {
    return with(new Column<>() {
      public Object read(Cursor in) { return new String(in.take(length), StandardCharsets.ISO_8859_1); }
      public void write(B value, Sink out) {
        out.bytes(Arrays.copyOf(String.format("%-" + length + "s", field.apply(value)).getBytes(StandardCharsets.ISO_8859_1), length));
      }
    });
  }

  /** Exactly so many bytes, as they are. */
  public Sequence<B> fixed(int length, Function<B, byte[]> field) {
    return with(new Column<>() {
      public Object read(Cursor in) { return in.take(length); }
      public void write(B value, Sink out) { out.bytes(Arrays.copyOf(field.apply(value), length)); }
    });
  }

  /** Numbers of so many bytes each, preceded by how many there are in so many bytes. */
  public Sequence<B> numbers(int countBytes, int eachBytes, boolean signed, Function<B, int[]> field) {
    return with(new Column<>() {
      public Object read(Cursor in) {
        int[] numbers = new int[length(in, countBytes)];
        for (int at = 0; at < numbers.length; at++) {
          int number = length(in, eachBytes);
          numbers[at] = signed && eachBytes == 2 ? (short) number : number;
        }
        return numbers;
      }

      public void write(B value, Sink out) {
        int[] numbers = field.apply(value);
        number(out, countBytes, numbers.length);
        for (int number : numbers) number(out, eachBytes, number);
      }
    });
  }

  /** Records of so many bytes each, preceded by how many there are in so many bytes; kept as the bytes after the count. */
  public Sequence<B> records(int countBytes, int eachLength, Function<B, byte[]> field) {
    return with(new Column<>() {
      public Object read(Cursor in) { return in.take(length(in, countBytes) * eachLength); }
      public void write(B value, Sink out) {
        byte[] records = field.apply(value);
        number(out, countBytes, records.length / eachLength);
        out.bytes(records);
      }
    });
  }

  /** A length in so many bytes, and inside it another sequence, whose columns fill it. */
  public Sequence<B> within(int lengthBytes, Sequence<B> inner) {
    return with(new Column<>() {
      public Object read(Cursor in) {
        return inner.columnsOf(Cursor.over(in.take(length(in, lengthBytes))));
      }

      public void write(B value, Sink out) {
        byte[] body = inner.bytesOf(value);
        number(out, lengthBytes, body.length);
        out.bytes(body);
      }
    }).flattening(inner.columns.size());
  }

  /** Whatever is left of the record, as it is: the last column of a sequence inside a length. */
  public Sequence<B> rest(Function<B, byte[]> field) {
    return with(new Column<>() {
      public Object read(Cursor in) { return in.rest(); }
      public void write(B value, Sink out) { out.bytes(field.apply(value)); }
    });
  }

  public B read(Cursor in) {
    return make.apply(new Read(columnsOf(in)));
  }

  public void write(B value, Sink out) {
    columns.forEach(column -> column.write(value, out));
  }

  public byte[] bytesOf(B value) {
    Sink out = new Sink();
    write(value, out);
    return out.toBytes();
  }

  private List<Object> columnsOf(Cursor in) {
    List<Object> read = new ArrayList<>();
    for (Column<B> column : columns) {
      Object value = column.read(in);
      if (value instanceof Flattened flattened) {
        read.addAll(flattened.values);
      } else {
        read.add(value);
      }
    }
    return read;
  }

  /** The columns of a sequence inside another count as the outer one's, in their order. */
  private record Flattened(List<Object> values) {
  }

  private Sequence<B> flattening(int inner) {
    List<Column<B>> changed = new ArrayList<>(columns);
    Column<B> last = changed.remove(changed.size() - 1);
    changed.add(new Column<>() {
      @SuppressWarnings("unchecked")
      public Object read(Cursor in) { return new Flattened((List<Object>) last.read(in)); }
      public void write(B value, Sink out) { last.write(value, out); }
    });
    return new Sequence<>(changed, make);
  }

  private Sequence<B> number(int bytes, ToIntFunction<B> field) {
    return with(new Column<>() {
      public Object read(Cursor in) { return length(in, bytes); }
      public void write(B value, Sink out) { number(out, bytes, field.applyAsInt(value)); }
    });
  }

  private static int length(Cursor in, int bytes) {
    return switch (bytes) {
      case 1 -> in.u8();
      case 2 -> in.u16();
      case 3 -> in.u16() | in.u8() << 16;
      default -> in.u32();
    };
  }

  private static void number(Sink out, int bytes, int value) {
    switch (bytes) {
      case 1 -> out.u8(value);
      case 2 -> out.u16(value);
      case 3 -> out.u16(value).u8(value >> 16);
      default -> out.u32(value);
    }
  }

  private Sequence<B> with(Column<B> column) {
    List<Column<B>> more = new ArrayList<>(columns);
    more.add(column);
    return new Sequence<>(more, make);
  }
}
