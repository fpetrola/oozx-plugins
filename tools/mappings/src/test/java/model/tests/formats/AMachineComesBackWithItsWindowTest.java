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
import com.fpetrola.oozx.formats.CoreFields;
import com.fpetrola.oozx.mappings.SzxSession;
import com.fpetrola.oozx.snapshots.SzxFormat;
import com.fpetrola.oozx.speccy.devices.Desk;
import com.fpetrola.oozx.speccy.devices.EmulatorWindow;
import com.fpetrola.oozx.speccy.devices.Equipment;
import com.fpetrola.oozx.speccy.devices.MachineFrame;
import com.fpetrola.oozx.speccy.devices.melodik.MelodikPeripheral;
import com.fpetrola.oozx.speccy.devices.mouse.MouseEquipment;
import com.fpetrola.oozx.speccy.devices.printer.PrinterEquipment;
import com.fpetrola.oozx.speccy.devices.specdrum.SpecDrumEquipment;
import com.fpetrola.oozx.speccy.devices.specdrum.SpecDrumPeripheral;
import com.fpetrola.oozx.speccy.machine.SpecPlus3;
import com.fpetrola.oozx.speccy.machine.Tc2048;
import com.fpetrola.oozx.speccy.modules.display.Picture;
import com.fpetrola.oozx.speccy.modules.snapshot.Snapshots;
import com.fpetrola.oozx.speccy.modules.sound.SilentSoundDevice;
import com.fpetrola.oozx.speccy.modules.sound.SoundCard;
import com.fpetrola.oozx.speccy.modules.z80.SpectrumZ80Clock;
import com.fpetrola.oozx.speccy.peripherals.MockEmulatorCore;
import com.fpetrola.oozx.speccy.pokes.PokFile;
import com.fpetrola.oozx.speccy.pokes.PokesTool;
import com.fpetrola.oozx.speccy.screen.SpeccyScreen;
import com.fpetrola.oozx.speccy.windows.AttachedFrame;
import org.junit.jupiter.api.Test;

import javax.swing.JDesktopPane;
import javax.swing.JInternalFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Rectangle;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A machine's window kept as a file of the session and opened again: the machine from the file as
 * any file opens, with its settings and what was plugged into it, and then where its window was and
 * how it showed the picture, whether it was paused, the file it came from, the pokes applied to it
 * and the equipment clipped onto it. Another emulator reads the same file.
 */
class AMachineComesBackWithItsWindowTest {

  /** A machine's window, as far as the session can tell: where it is, which machine it shows, and how. */
  static final class Window extends JInternalFrame implements EmulatorWindow {
    private final Speccy speccy;
    private final SpeccyScreen screen;

    Window(Speccy speccy) {
      this.speccy = speccy;
      this.screen = new SpeccyScreen(speccy.picture.pixels, Picture.STRIDE);
    }

    public SpeccyScreen picture() {
      return screen;
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

  /**
   * A desk offering a few pieces of equipment, on a desktop of its own, that keeps the windows it
   * opened of each: what the application's does with them and nothing more. It hands back the
   * ones clipped onto a machine in no particular order, as the application's do: newest first here.
   */
  record Desktop(JDesktopPane pane, Map<MachineFrame, String> opened) implements Desk {
    static final Map<String, Equipment> OFFERED = Map.of("SpecDrum", new SpecDrumEquipment(),
        "ZX Printer", new PrinterEquipment(), "Kempston Mouse", new MouseEquipment());

    Desktop() {
      this(new JDesktopPane(), new LinkedHashMap<>());
      pane.setSize(1600, 1200);
    }

    public Map<MachineFrame, String> equipmentOn(EmulatorWindow machine) {
      Map<MachineFrame, String> on = new LinkedHashMap<>();
      opened.keySet().stream().filter(window -> window.isAttached() && window.getMachineWindow() == machine)
          .toList().reversed().forEach(window -> on.put(window, opened.get(window)));
      return on;
    }

    public MachineFrame equipment(String name) {
      MachineFrame window = OFFERED.get(name).open();
      opened.put(window, name);
      pane.add(window);
      window.setVisible(true);
      return window;
    }
  }

  /**
   * Done on the event thread, as the desk does it: the resizes a window posts wait until it is done,
   * and are delivered before the next thing asked of that thread.
   */
  static <T> T onTheEventThread(Callable<T> work) throws Exception {
    FutureTask<T> task = new FutureTask<>(work);
    SwingUtilities.invokeAndWait(task);
    return task.get();
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

  /** How its picture was shown comes back too: a look of its own, not the one a new window opens with, three times its size. */
  @Test
  void itsPictureIsShownAsItWas() throws Exception {
    Window kept = kept();
    kept.picture().getScreenSettings().apply(Map.of("scaler", "Sharp bilinear", "tv", "Aerial (RF)", "scanlines", "0.3",
        "mask", "SLOT_MASK", "shade", "Green phosphor", "brightness", "1.2", "border", "true"));
    kept.picture().setZoom(3);
    byte[] file = new SzxSession().write(kept);

    Speccy opened = machine();
    new SzxFormat().read(file, opened, note -> { });
    Window window = new Window(opened);
    new SzxSession().bringBack(file, window);

    assertEquals(kept.picture().getScreenSettings().values(), window.picture().getScreenSettings().values());
    assertEquals(3, window.picture().getZoom());
  }

  @Test
  void thePokesItHadAppliedCanStillBeUndone() throws Exception {
    PokesTool pokes = new PokesTool();
    Window kept = kept();
    PokFile.PokeMod lives = new PokFile.PokeMod("Infinite lives", "M 8 35136 0 53", "game.pok", "Game");
    lives.getParsedInstruction().setPreviousValue(53);
    lives.getParsedInstruction().setPreviousBank(8);
    lives.getParsedInstruction().setPreviousAddress(35136);
    pokes.applied(kept, List.of(lives));
    byte[] file = new SzxSession(Set.of(pokes)).write(kept);

    Speccy opened = machine();
    new SzxFormat().read(file, opened, note -> { });
    Window window = new Window(opened);
    new SzxSession(Set.of(pokes)).bringBack(file, window);

    PokFile.PokeMod back = pokes.applied(window).get(0);
    assertEquals(List.of("Infinite lives", "M 8 35136 0 53", "game.pok", "Game"),
        List.of(back.getName(), back.getRawInstruction(), back.getPokFileName(), back.getGameName()));
    assertEquals(List.of(53, 8, 35136), List.of(back.getParsedInstruction().getPreviousValue(),
        back.getParsedInstruction().getPreviousBank(), back.getParsedInstruction().getPreviousAddress()));
  }

  /**
   * A clone no format names is written as the model it copies, and comes back as itself from the
   * block: the header of this file says 48K, and the block a TC2048, which is what it lands on.
   */
  @Test
  void theMachineTheBlockNamesIsTheOneTheFileLandsOn() throws Exception {
    Speccy kept = machine();
    String timex = kept.machine.model(Tc2048.class).shortName();
    byte[] file = new SzxFormat().write(kept, Map.of("OOZX", ("machine=" + timex).getBytes()), note -> { });

    Speccy opened = machine();
    new SzxFormat().read(file, opened, note -> { });

    assertEquals(Tc2048.class, opened.machine.current.getClass());
  }

  @Test
  void romsThatAreNotHereLeaveItOnItsOwnAndSaySo() throws Exception {
    Speccy kept = machine();
    kept.machine.select(kept.machine.model(SpecPlus3.class));
    byte[] file = new SzxFormat().write(kept, Map.of("OOZX", "roms=Version 4.1".getBytes()), note -> { });

    Speccy opened = machine();
    List<String> notes = new java.util.ArrayList<>();
    new SzxFormat().read(file, opened, notes::add);

    assertEquals(SpecPlus3.class, opened.machine.current.getClass());
    assertEquals("Version 4.0", opened.roms.chosenSet(opened.machine.current));
    assertTrue(notes.stream().anyMatch(note -> note.contains("Version 4.1")), String.valueOf(notes));
  }

  /**
   * What is plugged into it is in before it is read, whether its window plugged it or a setting did:
   * plugging a SpecDrum in resets the machine, which once it is read would undo the reading, and a
   * Melodik switched on afterwards has missed its registers in the AY block.
   */
  @Test
  void whatIsPluggedIntoItIsInBeforeItIsRead() throws Exception {
    Speccy kept = machine();
    kept.peripheralRegistry.allThatAre(SpecDrumPeripheral.class).get(0).plugIn(true);
    settings(kept, "melodik").values().set("fitted", true);
    if (kept.peripheralRegistry.update()) kept.machine.reset(true);
    byte[] page = new byte[16384];
    Arrays.fill(page, (byte) 0xA5);
    CoreFields.PAGE.set(kept.banks, 2, page);
    kept.peripheralRegistry.allThatAre(MelodikPeripheral.class).get(0).register(7, 0x38);
    byte[] file = new SzxSession().write(new Window(kept));

    Speccy opened = machine();
    new SzxFormat().read(file, opened, note -> { });

    assertTrue(opened.peripheralRegistry.isActive(SpecDrumPeripheral.class), "the SpecDrum is not in");
    assertTrue(opened.peripheralRegistry.isActive(MelodikPeripheral.class), "the Melodik is not in");
    assertEquals(0x38, opened.peripheralRegistry.allThatAre(MelodikPeripheral.class).get(0).register(7));
    assertEquals(MachineDescription.of(kept), MachineDescription.of(opened));
    assertFalse(opened.peripheralRegistry.update(), "clipping their windows on now would reset it");
  }

  /**
   * Each piece of equipment clipped onto it comes back to the same side, the same size, folded or
   * not, and unfolding to the same height; two sharing a side share it in the same order.
   */
  @Test
  void theEquipmentClippedOnItComesBackAsItWasLeft() throws Exception {
    try {
      Desktop desk = new Desktop();
      Window kept = kept();
      List<MachineFrame> clipped = onTheEventThread(() -> {
        Desk.isRunBy(desk);
        desk.pane().add(kept);
        MachineFrame drum = desk.equipment("SpecDrum");
        drum.attachTo(kept, AttachedFrame.Dock.BOTTOM);
        MachineFrame printer = desk.equipment("ZX Printer");
        printer.setCompact(false, 230);
        printer.attachTo(kept, AttachedFrame.Dock.BOTTOM);
        MachineFrame mouse = desk.equipment("Kempston Mouse");
        mouse.setSize(300, 200);
        mouse.attachTo(kept, AttachedFrame.Dock.RIGHT);
        return List.of(drum, printer, mouse);
      });
      List<String> left = onTheEventThread(() -> asLeft(clipped.stream()));
      byte[] file = onTheEventThread(() -> new SzxSession().write(kept));

      Desktop another = new Desktop();
      Speccy opened = machine();
      new SzxFormat().read(file, opened, note -> { });
      Window window = new Window(opened);
      onTheEventThread(() -> {
        Desk.isRunBy(another);
        another.pane().add(window);
        new SzxSession().bringBack(file, window);
        return null;
      });

      assertEquals(left, onTheEventThread(() -> asLeft(another.opened().keySet().stream())));
      assertTrue(another.opened().keySet().stream().allMatch(on -> on.getMachineWindow() == window), "clipped onto another window");
    } finally {
      Desk.isRunBy(null);
    }
  }

  /** What there is to see of each window clipped on: which, where, how big, and how it folds. */
  private static List<String> asLeft(Stream<MachineFrame> windows) {
    return windows.map(window -> window.getTitle() + " " + window.dockedTo() + " " + window.getBounds()
        + (window.isCompact() ? " compact" : " expanded") + " to " + window.expandedHeight()).sorted().toList();
  }

  @Test
  void anotherEmulatorReadsTheFilePassingOverWhatIsAroundTheMachine() throws Exception {
    assumeTrue(Reference.present(), "libspectrum is not installed here");
    assertFalse(Reference.read(new SzxSession().write(kept()), "szx").isRefused(), "libspectrum refused it");
  }
}
