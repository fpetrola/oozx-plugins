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

package model.tests.formats;

import com.fpetrola.oozx.Speccy;
import com.fpetrola.oozx.snapshots.SzxFormat;
import com.fpetrola.oozx.speccy.devices.zxatasp.ZxataspPeripheral;
import com.fpetrola.oozx.speccy.peripherals.Peripheral;
import com.fpetrola.oozx.speccy.peripherals.Pluggable;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Each device in the blocks SZX has for it: a machine with it writes a file libspectrum reads the same, and a
 * machine that reads the file has it plugged in and as it was. libspectrum's own file of each block reads as its
 * tests say.
 */
class WhatIsPluggedInComesBackTest {

  @Test
  void theZxataspWithItsBanksAndItsEightTwoFiveFive() throws Exception {
    Speccy kept = AMachineComesBackWithItsWindowTest.machine();
    ZxataspPeripheral board = plugged(kept, ZxataspPeripheral.class);
    for (int bank = 0; bank < ZxataspPeripheral.BANKS; bank++) board.contents(bank, pattern(bank, 0x4000));
    board.setUpload(true);
    board.portA(0xab);
    board.portB(0x8c);
    board.portC(0x42);
    board.control(0x9b);
    board.select(5, true);
    byte[] file = new SzxFormat().write(kept, note -> { });

    Speccy opened = AMachineComesBackWithItsWindowTest.machine();
    new SzxFormat().read(file, opened, note -> { });
    assertTrue(opened.peripheralRegistry.isActive(ZxataspPeripheral.class), "the file plugs it in");
    assertEquals(described(board), described((ZxataspPeripheral) opened.peripheralRegistry.find(ZxataspPeripheral.class)));

    assumeTrue(Reference.present(), "libspectrum is not installed here");
    Libspectrum lib = Libspectrum.INSTANCE;
    assertEquals(described(board), Reference.withSnap(file, "szx", snap -> {
      assertEquals(1, lib.libspectrum_snap_zxatasp_active(snap));
      List<Object> said = new ArrayList<>(List.of(lib.libspectrum_snap_zxatasp_upload(snap) != 0,
          lib.libspectrum_snap_zxatasp_writeprotect(snap) != 0, lib.libspectrum_snap_zxatasp_port_a(snap) & 0xff,
          lib.libspectrum_snap_zxatasp_port_b(snap) & 0xff, lib.libspectrum_snap_zxatasp_port_c(snap) & 0xff,
          lib.libspectrum_snap_zxatasp_control(snap) & 0xff, (int) lib.libspectrum_snap_zxatasp_current_page(snap).longValue()));
      for (int bank = 0; bank < ZxataspPeripheral.BANKS; bank++) {
        said.add(Arrays.hashCode(lib.libspectrum_snap_zxatasp_ram(snap, bank).getByteArray(0, 0x4000)));
      }
      return said;
    }).orElseThrow());
  }

  /** libspectrum's ZXAT: uploading, its ports, and bank 0x11 paged in. */
  @Test
  void theZxataspOfLibspectrumsOwnFile() throws Exception {
    Speccy opened = AMachineComesBackWithItsWindowTest.machine();
    new SzxFormat().read(Files.readAllBytes(Fixtures.HERE.resolve("libspectrum/szx-chunks/ZXAT.szx")), opened, note -> { });

    ZxataspPeripheral board = (ZxataspPeripheral) opened.peripheralRegistry.find(ZxataspPeripheral.class);
    assertTrue(opened.peripheralRegistry.isActive(ZxataspPeripheral.class), "the file plugs it in");
    assertEquals(List.of(true, false, 0xab, 0x8c, 0x82, 0xd8, 0x11), described(board).subList(0, 7));
  }

  /** That device of the machine, plugged in as its window plugs it. */
  static <T extends Peripheral & Pluggable> T plugged(Speccy speccy, Class<T> type) {
    T device = type.cast(speccy.peripheralRegistry.find(type));
    device.plugIn(true);
    if (speccy.peripheralRegistry.update()) speccy.machine.reset(true);
    return device;
  }

  /** Its jumpers, its 8255, the bank paged in or 0xff, and what each bank holds. */
  private static List<Object> described(ZxataspPeripheral board) {
    List<Object> said = new ArrayList<>(List.of(board.upload(), board.writeProtect(), board.portA(), board.portB(),
        board.portC(), board.control(), board.isPaged() ? board.bank() : 0xff));
    for (int bank = 0; bank < ZxataspPeripheral.BANKS; bank++) said.add(Arrays.hashCode(board.contents(bank)));
    return said;
  }

  static byte[] pattern(int seed, int length) {
    byte[] bytes = new byte[length];
    for (int at = 0; at < length; at++) bytes[at] = (byte) (seed + at * 7 + at / 256);
    return bytes;
  }
}
