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

import com.fpetrola.emulation.helpers.machine.MachineTypes;
import com.fpetrola.oozx.Speccy;
import com.fpetrola.oozx.formats.CoreFields;
import com.fpetrola.oozx.formats.Shape;
import com.fpetrola.oozx.snapshots.SzxFormat;
import com.fpetrola.oozx.speccy.machine.RomNotLoadedException;
import com.fpetrola.oozx.speccy.machine.Spectrum;
import com.fpetrola.oozx.speccy.modules.sound.SilentSoundDevice;
import com.fpetrola.oozx.speccy.modules.sound.SoundCard;
import com.fpetrola.oozx.speccy.modules.z80.SpectrumZ80Clock;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every machine of the build that a snapshot can name is written as itself and read back onto
 * itself, with every bank of RAM it has: a Pentagon 512 keeps its thirty-two, a Scorpion its
 * sixteen. libspectrum reads the same machine from the file.
 */
class EveryMachineComesBackAsItselfTest {

  static Speccy machine() {
    Speccy speccy = Speccy.create(new SpectrumZ80Clock(), binder -> binder.bind(SoundCard.class).to(SilentSoundDevice.class));
    speccy.init();
    speccy.picture.active = false;
    return speccy;
  }

  @TestFactory
  Stream<DynamicTest> eachOneOnItself() {
    Speccy any = machine();
    try {
      return any.machine.getMachineTypes().stream().filter(model -> model.snapshotModel() != null)
          .map(model -> DynamicTest.dynamicTest(model.getName(), () -> comesBack(model.getClass(), model.snapshotModel())))
          .toList().stream();
    } finally {
      any.end();
    }
  }

  private static void comesBack(Class<? extends Spectrum> model, MachineTypes type) throws Exception {
    Speccy kept = machine(), opened = machine();
    try {
      try {
        kept.machine.select(kept.machine.model(model));
      } catch (RomNotLoadedException noRom) {
        Assumptions.abort("it cannot be switched on here: " + noRom.getMessage());
      }
      for (int bank : Shape.banksOf(type)) {
        byte[] page = new byte[16384];
        Arrays.fill(page, (byte) (bank + 1));
        CoreFields.PAGE.set(kept.banks, bank, page);
      }
      byte[] file = new SzxFormat().write(kept, note -> { });

      new SzxFormat().read(file, opened, note -> { });

      assertEquals(model, opened.machine.current.getClass());
      for (int bank : Shape.banksOf(type)) {
        byte[] page = new byte[16384];
        Arrays.fill(page, (byte) (bank + 1));
        assertArrayEquals(page, CoreFields.PAGE.get(opened.banks, bank), "bank " + bank);
      }
      if (Reference.present()) {
        String read = Reference.read(file, "szx").toString();
        assertTrue(read.contains("machine = " + type.name() + "\n"), "libspectrum reads it as " + read.lines().findFirst().orElse("nothing"));
      }
    } finally {
      kept.end();
      opened.end();
    }
  }
}
