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

/**
 * A stretch of fixed length, whose bytes the tables of the parts read and write at offsets. A
 * virtual one is not in the file: a rule fills it or empties it, like the PC a 48K SNA keeps on the
 * stack. A paged one is in the file once a page, each page that long, and one for each is in it once
 * for each of something, its number in it.
 */
public final class Fixed implements Region {

  private final String name;
  private final int start;
  private final int length;
  private final boolean virtual;
  private final boolean paged;
  private final int numberAt;

  private Fixed(String name, int start, int length, boolean virtual, boolean paged, int numberAt) {
    this.name = name;
    this.start = start;
    this.length = length;
    this.virtual = virtual;
    this.paged = paged;
    this.numberAt = numberAt;
  }

  /** A stretch of that length, its offsets counted from where it starts. */
  public static Fixed of(String name, int length) {
    return new Fixed(name, 0, length, false, false, -1);
  }

  /** A stretch from one offset of the file to another, its offsets the file's. */
  public static Fixed span(String name, int from, int to) {
    return new Fixed(name, from, to - from, false, false, -1);
  }

  /** A stretch no file has, that a rule fills when reading and empties when writing. */
  public static Fixed virtual(String name, int length) {
    return new Fixed(name, 0, length, true, false, -1);
  }

  /** The memory of something plugged in, a page of that length at a time, each by its number. */
  public static Fixed pages(String name, int pageLength) {
    return new Fixed(name, 0, pageLength, false, true, -1);
  }

  /** A block that comes once for each of something, told apart by its number in the byte at numberAt: a disk in each drive. */
  public static Fixed each(String name, int length, int numberAt) {
    return new Fixed(name, 0, length, false, false, numberAt);
  }

  public int length() {
    return length;
  }

  public boolean isPaged() {
    return paged;
  }

  public boolean isEach() {
    return numberAt >= 0;
  }

  public int numberAt() {
    return numberAt;
  }

  /** The same stretch, ending at that offset of the file: what a version of a format that says less has of it. */
  public Fixed upTo(int end) {
    return new Fixed(name, start, end - start, virtual, paged, numberAt);
  }

  /** Whether it reaches that far: a place past where a version cuts it is not in that version. */
  boolean reaches(int offset, int count) {
    return offset >= start && offset + count <= start + length;
  }

  /** The same stretch whatever version cut it: by name and where it starts. */
  @Override
  public boolean equals(Object other) {
    return other instanceof Fixed fixed && fixed.name.equals(name) && fixed.start == start;
  }

  @Override
  public int hashCode() {
    return name.hashCode() * 31 + start;
  }

  Bytes empty() {
    return Bytes.zeros(start, length);
  }

  @Override
  public void parse(Cursor in, SnapshotFile into) {
    into.put(this, virtual ? empty() : Bytes.of(start, in.take(length)));
  }

  @Override
  public void prepare(SnapshotFile file) {
    file.put(this, empty());
  }

  @Override
  public void assemble(SnapshotFile from, Sink out) {
    if (!virtual) {
      out.bytes(from.bytes(this).toBytes());
    }
  }

  @Override
  public String toString() {
    return name;
  }
}
