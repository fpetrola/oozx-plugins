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
import com.fpetrola.oozx.config.Settings;
import com.fpetrola.oozx.mappings.SzxSession;
import com.fpetrola.oozx.snapshots.SzxFormat;
import com.fpetrola.oozx.speccy.devices.EmulatorWindow;
import com.fpetrola.oozx.speccy.modules.snapshot.Snapshots;
import com.fpetrola.oozx.speccy.modules.sound.SilentSoundDevice;
import com.fpetrola.oozx.speccy.modules.sound.SoundCard;
import com.fpetrola.oozx.speccy.modules.z80.SpectrumZ80Clock;
import com.fpetrola.oozx.speccy.peripherals.MockEmulatorCore;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.JInternalFrame;
import javax.swing.JPanel;
import java.awt.Rectangle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A machine's window kept as a file of the session and opened again: the machine from the file as
 * any file opens, and then where its window was, whether it was paused, the file it came from, and
 * the settings of the machine and its devices. Another emulator reads the same file.
 */
class AMachineComesBackWithItsWindowTest {

  /** A machine's window, as far as the session can tell: where it is, and which machine it shows. */
  static final class Window extends JInternalFrame implements EmulatorWindow {
    private final Speccy speccy;

    Window(Speccy speccy) {
      this.speccy = speccy;
    }

    public JComponent picture() {
      return this;
    }

    public Speccy machine() {
      return speccy;
    }
  }

  /** What the desk's machine does with a pause and a file, which is all of it the session asks. */
  static final class Control extends MockEmulatorCore {
    private boolean paused;
    private String file;

    Control() {
      super(new JPanel());
    }

    public boolean isPaused() {
      return paused;
    }

    public void setGeneralOption(String option, Object value) {
      if (option.equals("pause")) paused = (Boolean) value;
      else super.setGeneralOption(option, value);
    }

    public String getFilename() {
      return file;
    }

    public void setFilename(String file) {
      this.file = file;
    }
  }

  static Speccy machine() {
    Speccy speccy = Speccy.create(new SpectrumZ80Clock(), binder -> binder.bind(SoundCard.class).to(SilentSoundDevice.class));
    speccy.init();
    speccy.picture.active = false;
    speccy.control = new Control();
    return speccy;
  }

  static Settings.Configurable settings(Speccy speccy, String name) {
    return speccy.settings().devices().stream().filter(one -> one.device().name().equals(name)).findFirst().orElseThrow();
  }

  /** A 128K with its banks paged, in a window put somewhere, paused, quieter and muted. */
  static Window kept() {
    Speccy speccy = machine();
    Snapshots.of(speccy).load(Fixtures.HERE.resolve("banks.z80").toString());
    Window window = new Window(speccy);
    window.setBounds(40, 30, 520, 410);
    speccy.control.setGeneralOption("pause", true);
    speccy.control.setFilename("/games/banks.tap");
    settings(speccy, "sound.volume").values().set("volume", 37);
    settings(speccy, "sound").values().set("enabled", false);
    return window;
  }

  @Test
  void itsMachineAndWhatWasAroundItComeBack() throws Exception {
    Window kept = kept();
    byte[] file = new SzxSession().write(kept);

    Speccy opened = machine();
    new SzxFormat().read(file, opened, note -> { });
    Window window = new Window(opened);
    new SzxSession().bringBack(file, window);

    assertEquals(MachineDescription.of(kept.machine()), MachineDescription.of(opened));
    assertEquals(new Rectangle(40, 30, 520, 410), window.getBounds());
    assertTrue(opened.control.isPaused(), "paused");
    assertEquals("/games/banks.tap", opened.control.getFilename());
    assertEquals(37, settings(opened, "sound.volume").values().get("volume"));
    assertEquals(false, settings(opened, "sound").values().get("enabled"), "muted");
  }

  @Test
  void anotherEmulatorReadsTheFilePassingOverWhatIsAroundTheMachine() throws Exception {
    assumeTrue(Reference.present(), "libspectrum is not installed here");
    assertFalse(Reference.read(new SzxSession().write(kept()), "szx").isRefused(), "libspectrum refused it");
  }
}
