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
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * A file of a format, understood: its fixed stretches by name, its pages by bank, the pages of what
 * is plugged in by block and number, and what was left out on the way. Reading, it is filled whole
 * from the bytes before the machine is touched; writing, the machine's parts fill it and then it is
 * put together in order.
 */
public final class SnapshotFile {

  private final Shape shape;
  private final Map<Fixed, Bytes> fixed = new HashMap<>();
  private final Set<Fixed> written = new java.util.HashSet<>();
  private final Map<Integer, byte[]> pages = new TreeMap<>();
  private final Map<Fixed, Map<Integer, byte[]>> paged = new HashMap<>();
  private final Set<Integer> placedOrWritten = new LinkedHashSet<>();
  private final List<String> notes = new ArrayList<>();
  private final Map<String, byte[]> carried = new java.util.LinkedHashMap<>();
  private Pages.AtTheTop pendingAt;
  private byte[] pending;

  SnapshotFile(Shape shape) {
    this.shape = shape;
  }

  public Shape shape() {
    return shape;
  }

  /** Whether the file has that stretch: a table's places in a stretch the file lacks are not there. */
  public boolean has(Fixed region) {
    return fixed.containsKey(region);
  }

  /** Whether the file has that stretch, and as far as those bytes: a version may cut it shorter. */
  public boolean has(Fixed region, int offset, int count) {
    Bytes bytes = fixed.get(region);
    return bytes != null && offset >= bytes.start() && offset + count <= bytes.start() + bytes.length();
  }

  /** Whether the file has that byte with all those bits on: how a block or a header says what is plugged in. */
  public boolean says(Fixed region, int offset, int bits) {
    return has(region, offset, 1) && (u8(region, offset) & bits) == bits;
  }

  public Bytes bytes(Fixed region) {
    Bytes bytes = fixed.get(region);
    if (bytes == null) {
      throw new IllegalStateException("this file has no " + region);
    }
    return bytes;
  }

  public int u8(Fixed region, int offset) {
    return bytes(region).u8(offset);
  }

  public int u16(Fixed region, int offset) {
    return bytes(region).u16(offset);
  }

  public void u8(Fixed region, int offset, int value) {
    bytes(region).u8(offset, value);
  }

  public void u16(Fixed region, int offset, int value) {
    bytes(region).u16(offset, value);
  }

  void put(Fixed region, Bytes bytes) {
    fixed.put(region, bytes);
  }

  /** A stretch a section read or offers, of the length it has: a block says its own. */
  public void stretch(Fixed region, byte[] bytes) {
    fixed.put(region, Bytes.of(0, bytes));
  }

  /** Whether some part wrote into that stretch: a block nothing wrote is not written. */
  public boolean isWritten(Fixed region) {
    return written.contains(region);
  }

  void wrote(Fixed region) {
    written.add(region);
  }

  /** The banks the file has. */
  public Set<Integer> pages() {
    return pages.keySet();
  }

  public Optional<byte[]> page(int bank) {
    return Optional.ofNullable(pages.get(bank));
  }

  public byte[] pageOrZeros(int bank) {
    return page(bank).orElseGet(() -> new byte[Pages.LENGTH]);
  }

  public void page(int bank, byte[] bytes) {
    if (bytes.length != Pages.LENGTH) {
      throw new Refused("a page of " + bytes.length + " bytes");
    }
    pages.put(bank, bytes.clone());
  }

  /** The pages the file has of that block, by number. */
  public Map<Integer, byte[]> pages(Fixed block) {
    return java.util.Collections.unmodifiableMap(paged.getOrDefault(block, Map.of()));
  }

  public void page(Fixed block, int number, byte[] bytes) {
    if (bytes.length != block.length()) {
      throw new Refused("a page of " + block + " of " + bytes.length + " bytes, where they are " + block.length());
    }
    paged.computeIfAbsent(block, any -> new TreeMap<>()).put(number, bytes.clone());
  }

  /** The RAM from 0x4000 as a 48K sees it, over the file's banks 5, 2 and 0: what a rule reads the stack from. */
  public Ram ram() {
    return new Ram(this);
  }

  public void note(String note) {
    notes.add(note);
  }

  public List<String> notes() {
    return List.copyOf(notes);
  }

  /** Something the file carries beside the machine, which no part reads: under the name it has in the file. */
  public void carry(String name, byte[] bytes) {
    carried.put(name, bytes);
  }

  public Map<String, byte[]> carried() {
    return java.util.Collections.unmodifiableMap(carried);
  }

  void placed(int bank, byte[] bytes) {
    page(bank, bytes);
    placedOrWritten.add(bank);
  }

  boolean isPlaced(int bank) {
    return placedOrWritten.contains(bank);
  }

  byte[] written(int bank) {
    placedOrWritten.add(bank);
    return pageOrZeros(bank);
  }

  boolean isWritten(int bank) {
    return placedOrWritten.contains(bank);
  }

  void pending(Pages.AtTheTop at, byte[] bytes) {
    pendingAt = at;
    pending = bytes;
  }

  void placeWhatWasPending() {
    if (pending == null) {
      return;
    }
    int bank = pendingAt.bank(this);
    if (isPlaced(bank) && !Arrays.equals(pageOrZeros(bank), pending)) {
      throw new Refused("the page at the top, " + bank + ", is there twice and different");
    }
    placed(bank, pending);
    pending = null;
  }
}
