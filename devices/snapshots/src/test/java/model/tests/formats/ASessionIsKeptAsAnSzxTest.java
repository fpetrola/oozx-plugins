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

import com.fpetrola.emulation.helpers.snapshots.SnapshotSaver;
import com.fpetrola.oozx.Speccy;
import com.fpetrola.oozx.formats.SnapshotFormat;
import com.fpetrola.oozx.snapshots.SzxFormat;
import com.fpetrola.oozx.speccy.modules.snapshot.Snapshots;
import com.fpetrola.oozx.speccy.modules.sound.SilentSoundDevice;
import com.fpetrola.oozx.speccy.modules.sound.SoundCard;
import com.fpetrola.oozx.speccy.modules.z80.SpectrumZ80Clock;
import com.fpetrola.z80.bytecode.RegistersBase;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A session the desktop keeps in its settings is an SZX now, which keeps the banks, the paging and
 * the AY that the .z80 of before lost; and a session kept before, as a .z80, still opens.
 */
class ASessionIsKeptAsAnSzxTest {

  static Speccy machine() {
    Speccy speccy = Speccy.create(new SpectrumZ80Clock(), binder -> binder.bind(SoundCard.class).to(SilentSoundDevice.class));
    speccy.init();
    speccy.picture.active = false;
    return speccy;
  }

  @Test
  void aMachineKeptAndOpenedAgainIsTheSameMachine() {
    Speccy kept = machine();
    Snapshots.of(kept).load(Path.of("src/test/resources/snapshots/banks.z80").toString());
    String session = Snapshots.of(kept).packed();
    assertTrue(session.startsWith("szx:"), "kept as an SZX");

    Speccy opened = machine();
    Snapshots.of(opened).loadPacked(session);
    assertEquals(MachineDescription.of(kept), MachineDescription.of(opened));
  }

  @Test
  void whatTheEnvironmentCarriesBesideAMachineComesBackFromItsFile() throws Exception {
    Speccy kept = machine();
    Snapshots.of(kept).load(Path.of("src/test/resources/snapshots/banks.z80").toString());
    SnapshotFormat szx = new SzxFormat();
    byte[] file = szx.write(kept, Map.of("OOZX", new byte[]{1, 2, 3}), note -> { });
    assertArrayEquals(new byte[]{1, 2, 3}, szx.carried(file).get("OOZX"));

    Speccy opened = machine();
    szx.read(file, opened, note -> { });
    assertEquals(MachineDescription.of(kept), MachineDescription.of(opened), "what it carries changes nothing of the machine");
  }

  @Test
  void anotherEmulatorReadsTheFilePassingOverWhatTheEnvironmentCarries() throws Exception {
    assumeTrue(Reference.present(), "libspectrum is not installed here");
    Speccy kept = machine();
    Snapshots.of(kept).load(Path.of("src/test/resources/snapshots/banks.z80").toString());
    byte[] file = new SzxFormat().write(kept, Map.of("OOZX", new byte[]{1, 2, 3}), note -> { });
    assertFalse(Reference.read(file, "szx").isRefused(), "libspectrum refused it");
  }

  @Test
  void aSessionKeptBeforeAsAZ80StillOpens() {
    Speccy kept = machine();
    Snapshots.of(kept).load(Path.of("src/test/resources/snapshots/manicminer.z80").toString());
    RegistersBase registers = new RegistersBase(kept.cpu.getOoz80().getState());
    String old = SnapshotSaver.getSnapshotAsUnicodePacked(registers, kept.cpu.getOoz80().getState());

    Speccy opened = machine();
    Snapshots.of(opened).loadPacked(old);
    assertEquals(registers.getRegPC(), new RegistersBase(opened.cpu.getOoz80().getState()).getRegPC());
  }
}
