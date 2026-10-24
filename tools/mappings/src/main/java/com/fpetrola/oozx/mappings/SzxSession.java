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

package com.fpetrola.oozx.mappings;

import com.fpetrola.emulation.helpers.snapshots.SnapshotException;
import com.fpetrola.oozx.EmulatorControl;
import com.fpetrola.oozx.Speccy;
import com.fpetrola.oozx.config.Configuration;
import com.fpetrola.oozx.config.Settings;
import com.fpetrola.oozx.formats.SnapshotFile;
import com.fpetrola.oozx.plugins.Plugins;
import com.fpetrola.oozx.snapshots.SzxFormat;
import com.fpetrola.oozx.speccy.devices.Desk;
import com.fpetrola.oozx.speccy.devices.EmulatorWindow;
import com.fpetrola.oozx.speccy.devices.MachineFrame;
import com.fpetrola.oozx.speccy.devices.MachineTool;
import com.fpetrola.oozx.speccy.devices.Session;
import com.fpetrola.oozx.speccy.machine.RomNotLoadedException;
import com.fpetrola.oozx.speccy.machine.Spectrum;
import com.fpetrola.oozx.speccy.peripherals.Pluggable;
import com.fpetrola.oozx.speccy.pokes.PokFile;
import com.fpetrola.oozx.speccy.pokes.PokInstruction;
import com.fpetrola.oozx.speccy.pokes.PokesTool;
import com.fpetrola.oozx.speccy.screen.SpeccyScreen;
import com.fpetrola.oozx.speccy.windows.AttachedFrame;
import com.google.inject.Inject;

import javax.swing.JInternalFrame;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * A machine's window as an SZX: the machine in the format's blocks, and what is around it in one of
 * its own, OOZX, which another emulator passes over. What is around it is text, a key a line: which
 * machine it is exactly and the ROMs it and its boards run on, every setting of the machine and of
 * its devices by the name it is declared under, which devices are plugged into it, how the window
 * shows the picture, whether it is paused, the file the machine came from, its processor and its
 * turbo, the pokes applied to it, and the equipment clipped onto the window. Where the window is,
 * the desk keeps.
 * <p>
 * The first four are put back as the file is read, before the machine is walked ({@link #plug},
 * asked by {@link SzxMapping}): choosing a machine starts it from cold, and plugging some devices
 * in resets it. The rest needs the window, and comes back once there is one.
 */
public final class SzxSession implements Session {

  static final String BLOCK = "OOZX";
  private static final String LOOK = "screen.";

  private final SzxFormat szx = new SzxFormat();
  /** The tools, asked what they left done on a window: the very ones its toolbar has. */
  @Inject
  private Set<MachineTool> tools = Set.of();

  public SzxSession() {
  }

  /** With the tools it asks, where nothing injects them. */
  public SzxSession(Set<MachineTool> tools) {
    this.tools = tools;
  }

  @Override
  public String extension() {
    return "szx";
  }

  @Override
  public byte[] write(EmulatorWindow window) throws SnapshotException {
    Map<String, String> around = new TreeMap<>();
    Speccy speccy = window.machine();
    if (window.picture() instanceof SpeccyScreen screen) {
      around.put("window.zoom", String.valueOf(screen.getZoom()));
      screen.getScreenSettings().values().forEach((knob, value) -> around.put(LOOK + knob, value));
    }
    Spectrum machine = speccy.machine.current;
    around.put("machine", machine.shortName());
    around.put("roms", Configuration.asText(speccy.roms.files));
    EmulatorControl control = speccy.control;
    if (control != null) {
      around.put("paused", String.valueOf(control.isPaused()));
      around.put("turbo", String.valueOf(control.isTurboMode()));
      if (control.getProcessor() != null) around.put("processor", control.getProcessor());
      if (control.getFilename() != null && !control.getFilename().isEmpty()) around.put("file", control.getFilename());
    }
    for (Settings.Configurable settings : speccy.settings().devices()) {
      for (String property : settings.device().properties()) {
        Object value = settings.values().get(property);
        if (value != null) around.put(settings.device().name() + "." + property, Configuration.asText(value));
      }
    }
    List<Pluggable> plugged = speccy.peripheralRegistry.allThatAre(Pluggable.class).stream().filter(Pluggable::isPluggedIn).toList();
    for (int at = 0; at < plugged.size(); at++) around.put("plugged." + at, plugged.get(at).getClass().getName());
    PokesTool pokes = pokes();
    if (pokes != null) keep(pokes.applied(window), around);
    keep(Desk.theOne().equipmentOn(window), around);
    String text = around.entrySet().stream().map(line -> line.getKey() + "=" + line.getValue()).collect(Collectors.joining("\n"));
    return szx.write(window.machine(), Map.of(BLOCK, text.getBytes(StandardCharsets.UTF_8)), note -> { });
  }

  /**
   * What has to be in place before the machine is walked, from the block a session wrote: the
   * machine and its ROMs, its settings, and what is plugged into it. The format switches it on, with
   * the reset that plugging some devices in asks for, as it did when they were plugged in first.
   */
  static void plug(byte[] block, Speccy speccy, SnapshotFile file) {
    Map<String, String> around = around(block);
    becomes(around, speccy, file);
    for (Settings.Configurable settings : speccy.settings().devices()) {
      for (String property : settings.device().properties()) {
        String value = around.get(settings.device().name() + "." + property);
        if (value == null) continue;
        try {
          settings.values().set(property, Configuration.fromText(value, settings.device().typeOf(property)));
        } catch (RuntimeException unreadable) {
          // A value a version that meant something else by it wrote: this one stays as the machine starts.
        }
      }
    }
    List<Pluggable> here = speccy.peripheralRegistry.allThatAre(Pluggable.class);
    for (int at = 0; around.containsKey("plugged." + at); at++) {
      String named = around.get("plugged." + at);
      here.stream().filter(device -> device.getClass().getName().equals(named)).findFirst()
          .ifPresentOrElse(device -> device.plugIn(true), () -> file.note("this build has no " + named + " to plug in"));
    }
  }

  private static void becomes(Map<String, String> around, Speccy speccy, SnapshotFile file) {
    Spectrum was = speccy.machine.current;
    String named = around.get("machine");
    Spectrum machine = named == null ? was : speccy.machine.forShortName(named).orElse(null);
    if (machine == null) {
      file.note("this build has no " + named + ": loaded into the " + was.getName());
      machine = was;
    }
    boolean otherRoms = runsOn(roms(around.get("roms")), machine, speccy, file);
    if (machine == was && !otherRoms) return;
    try {
      speccy.machine.select(machine);
    } catch (RomNotLoadedException noRoms) {
      file.note("the " + machine.getName() + " cannot be switched on here (" + noRoms.getMessage() + "): loaded into the " + was.getName());
      speccy.machine.select(was);
    }
  }

  /** The ROMs the machine and its boards ran on, by what each is; nothing from a session that did not say them. */
  @SuppressWarnings("unchecked")
  private static Map<String, List<String>> roms(String kept) {
    try {
      return kept == null ? null : (Map<String, List<String>>) Configuration.fromText(kept, Map.class);
    } catch (RuntimeException saidOtherwise) {
      return null;
    }
  }

  /**
   * Whether the machine is to run on other ROMs than it does: its own and its boards', as they were, in place of
   * what it started with, where the machine's are here. They are this machine's, and no other's.
   */
  private static boolean runsOn(Map<String, List<String>> roms, Spectrum machine, Speccy speccy, SnapshotFile file) {
    if (roms == null) return false;
    List<String> before = speccy.roms.running(machine);
    speccy.roms.files.clear();
    roms.forEach((key, files) -> speccy.roms.files.put(key, List.copyOf(files)));
    if (speccy.roms.running(machine).equals(before)) return false;
    if (speccy.roms.missingFor(machine).isEmpty()) return true;
    file.note("the ROMs " + speccy.roms.running(machine) + " are not here: the " + machine.getName() + " runs on its own");
    speccy.roms.runOn(machine, before);
    return false;
  }

  @Override
  public void bringBack(byte[] file, EmulatorWindow window) {
    byte[] block = szx.carried(file).get(BLOCK);
    if (block == null) return;
    Map<String, String> around = around(block);
    EmulatorControl control = window.machine().control;
    if (control != null) {
      if (around.containsKey("processor")) control.setProcessor(around.get("processor"));
      if (around.containsKey("turbo")) control.setGeneralOption("turbo", Boolean.parseBoolean(around.get("turbo")));
      if (around.containsKey("paused")) control.setGeneralOption("pause", Boolean.parseBoolean(around.get("paused")));
      control.setFilename(around.get("file"));
    }
    if (window.picture() instanceof SpeccyScreen screen) {
      Map<String, String> look = new TreeMap<>();
      around.forEach((key, value) -> { if (key.startsWith(LOOK)) look.put(key.substring(LOOK.length()), value); });
      screen.getScreenSettings().apply(look);
      if (around.containsKey("window.zoom")) screen.setZoom(Double.parseDouble(around.get("window.zoom")));
    }
    PokesTool pokes = pokes();
    if (pokes != null) pokes.applied(window, appliedIn(around));
    if (window instanceof JInternalFrame onto) clip(around, onto);
  }

  private PokesTool pokes() {
    return tools.stream().filter(PokesTool.class::isInstance).map(PokesTool.class::cast).findFirst().orElse(null);
  }

  /** Each poke applied, by its line of the .pok and what it overwrote, so that it can still be undone. */
  private static void keep(List<PokFile.PokeMod> applied, Map<String, String> around) {
    for (int at = 0; at < applied.size(); at++) {
      PokFile.PokeMod mod = applied.get(at);
      PokInstruction line = mod.getParsedInstruction();
      String key = "poke." + at + ".";
      around.put(key + "name", mod.getName());
      around.put(key + "line", mod.getRawInstruction());
      if (mod.getPokFileName() != null) around.put(key + "file", mod.getPokFileName());
      if (mod.getGameName() != null) around.put(key + "game", mod.getGameName());
      if (line.isApplied()) around.put(key + "before", line.getPreviousValue() + "," + line.getPreviousBank() + "," + line.getPreviousAddress());
    }
  }

  /** The same back: the memory already has them, from the file, so they are only written down. */
  private static List<PokFile.PokeMod> appliedIn(Map<String, String> around) {
    List<PokFile.PokeMod> applied = new ArrayList<>();
    for (int at = 0; around.containsKey("poke." + at + ".line"); at++) {
      String key = "poke." + at + ".";
      PokFile.PokeMod mod = new PokFile.PokeMod(around.get(key + "name"), around.get(key + "line"),
          around.get(key + "file"), around.get(key + "game"));
      if (around.containsKey(key + "before")) {
        String[] before = around.get(key + "before").split(",");
        mod.getParsedInstruction().setPreviousValue(number(before[0]));
        mod.getParsedInstruction().setPreviousBank(number(before[1]));
        mod.getParsedInstruction().setPreviousAddress(number(before[2]));
      }
      applied.add(mod);
    }
    return applied;
  }

  /** The equipment clipped onto the window, in the order each sits along its side: which, where, how big, and folded or not. */
  private static void keep(Map<MachineFrame, String> clipped, Map<String, String> around) {
    List<MachineFrame> windows = clipped.keySet().stream().sorted(Comparator.comparingInt(SzxSession::along)).toList();
    for (int at = 0; at < windows.size(); at++) {
      MachineFrame window = windows.get(at);
      String key = "equipment." + at + ".";
      around.put(key + "kind", clipped.get(window));
      around.put(key + "side", window.dockedTo().name());
      around.put(key + "width", String.valueOf(window.getWidth()));
      around.put(key + "height", String.valueOf(window.getHeight()));
      keepFolding(window, key, around);
    }
  }

  /** The same back, each kind this build still offers, in the same order so that a shared side is shared the same way. */
  private static void clip(Map<String, String> around, JInternalFrame onto) {
    for (int at = 0; around.containsKey("equipment." + at + ".kind"); at++) {
      String key = "equipment." + at + ".";
      MachineFrame window = Desk.theOne().equipment(around.get(key + "kind"));
      if (window == null) continue;
      fold(window, key, around);
      window.setSize(Integer.parseInt(around.get(key + "width")), Integer.parseInt(around.get(key + "height")));
      window.attachTo(onto, AttachedFrame.Dock.valueOf(around.get(key + "side")));
    }
  }

  /**
   * Any other window of the desk: the name the desk opened it under, how it folds, and what the
   * mapping of its kind says it has inside. One clipped onto a machine goes with the machine.
   */
  @Override
  public Map<String, String> keep(JInternalFrame window) {
    String kind = Desk.theOne().nameOf(window);
    if (kind == null || window instanceof AttachedFrame clipped && clipped.isAttached()) return Map.of();
    Map<String, String> kept = new TreeMap<>(Map.of("kind", kind));
    if (window instanceof AttachedFrame folding) keepFolding(folding, "", kept);
    Plugins.found(WindowInside.class).forEach(inside -> inside.keep(window, kept));
    return kept;
  }

  @Override
  public JInternalFrame bringBack(Map<String, String> kept) {
    JInternalFrame window = kept.containsKey("kind") ? Desk.theOne().window(kept.get("kind")) : null;
    if (window instanceof AttachedFrame folding) fold(folding, "", kept);
    if (window != null) Plugins.found(WindowInside.class).forEach(inside -> inside.bringBack(window, kept));
    return window;
  }

  private static void keepFolding(AttachedFrame window, String key, Map<String, String> around) {
    around.put(key + "compact", String.valueOf(window.isCompact()));
    around.put(key + "expanded", String.valueOf(window.expandedHeight()));
  }

  private static void fold(AttachedFrame window, String key, Map<String, String> around) {
    if (around.containsKey(key + "compact"))
      window.setCompact(Boolean.parseBoolean(around.get(key + "compact")), Integer.parseInt(around.get(key + "expanded")));
  }

  /** How far along its side a window sits, which is the order of the windows sharing it. */
  private static int along(AttachedFrame window) {
    AttachedFrame.Dock side = window.dockedTo();
    return side == AttachedFrame.Dock.TOP || side == AttachedFrame.Dock.BOTTOM ? window.getX() : window.getY();
  }

  private static Integer number(String text) {
    return text.equals("null") ? null : Integer.valueOf(text);
  }

  /** What is around a machine, from its block: a key a line. */
  private static Map<String, String> around(byte[] block) {
    Map<String, String> around = new TreeMap<>();
    for (String line : new String(block, StandardCharsets.UTF_8).split("\n")) {
      int equals = line.indexOf('=');
      if (equals > 0) around.put(line.substring(0, equals), line.substring(equals + 1));
    }
    return around;
  }
}
