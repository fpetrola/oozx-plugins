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

package com.fpetrola.oozx.speccy.tools.dock;

import com.fpetrola.oozx.Speccy;
import com.fpetrola.oozx.speccy.devices.EmulatorWindow;
import com.fpetrola.oozx.speccy.machine.Tc2068;
import model.harness.MachineTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JComponent;
import javax.swing.JInternalFrame;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockFrameTest {

  @TempDir
  Path folder;

  private static JInternalFrame windowOf(Speccy speccy) {
    class Machine extends JInternalFrame implements EmulatorWindow {
      public JComponent picture() {
        return this;
      }

      public Speccy machine() {
        return speccy;
      }
    }
    return new Machine();
  }

  /** A dock block with eight K of ROM in its first page, all 0x11. */
  private File aCartridge() throws IOException {
    byte[] file = new byte[9 + 0x2000];
    file[1] = 2;
    Arrays.fill(file, 9, file.length, (byte) 0x11);
    Path dck = folder.resolve("game.dck");
    Files.write(dck, file);
    return dck.toFile();
  }

  @Test
  void aCartridgeOpenedOnTheDeskGoesIntoTheDockOfTheTimexItIsClippedTo() throws IOException {
    Speccy speccy = MachineTest.silentMachine();
    speccy.machine.select(speccy.machine.model(Tc2068.class));
    DockFrame window = new DockFrame();
    window.attachTo(windowOf(speccy));
    assertTrue(window.empty());
    window.open(aCartridge());
    speccy.loop.doOpcodes();
    assertEquals("game", ((Tc2068) speccy.machine.current).cartridge().name());
    assertFalse(window.empty());
    speccy.ports.write(0xf4, (byte) 0x01);
    assertEquals(0x11, speccy.memory.peek(0) & 0xff, "the machine did not start again on it");
  }

  @Test
  void theDeskAsksTheDockAboutADck() {
    assertTrue(new DockEquipment().opens(new File("Flight Simulator.dck")));
    assertFalse(new DockEquipment().opens(new File("game.tap")));
  }
}
