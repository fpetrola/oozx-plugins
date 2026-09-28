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

/** The RAM of a file from 0x4000 to 0xFFFF as a 48K maps it: banks 5, 2 and 0. */
public final class Ram {

  private static final int[] BANKS = {5, 2, 0};

  private final SnapshotFile file;

  Ram(SnapshotFile file) {
    this.file = file;
  }

  public int u8(int address) {
    return file.pageOrZeros(bankOf(address))[address & 0x3fff] & 0xff;
  }

  public int word(int address) {
    return u8(address) | u8(address + 1 & 0xffff) << 8;
  }

  public Ram u8(int address, int value) {
    int bank = bankOf(address);
    byte[] page = file.pageOrZeros(bank);
    page[address & 0x3fff] = (byte) value;
    file.page(bank, page);
    return this;
  }

  public Ram word(int address, int value) {
    return u8(address, value).u8(address + 1 & 0xffff, value >> 8);
  }

  private static int bankOf(int address) {
    if (address < 0x4000 || address > 0xffff) {
      throw new Refused(String.format("0x%04x is not RAM", address));
    }
    return BANKS[(address - 0x4000) / Pages.LENGTH];
  }
}
