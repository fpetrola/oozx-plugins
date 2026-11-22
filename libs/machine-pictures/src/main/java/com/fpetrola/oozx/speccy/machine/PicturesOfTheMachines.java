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

package com.fpetrola.oozx.speccy.machine;

import com.fpetrola.oozx.Speccy;
import com.fpetrola.oozx.speccy.modules.display.Picture;
import dev.crystal.plugins.api.Offers;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * How each machine a module brings looks when it is switched on, drawn while the module is built: the machine started
 * where nobody sees it, left running a moment, and its screen written under META-INF/pictures named after its class,
 * so the catalogue shows a machine as a person meets it. A ROM this build does not carry is brought from where it is
 * published, as the desk does when a person starts that machine, and kept between builds; one that cannot be had
 * leaves its machine without a picture. Each thing that is clipped onto a machine is drawn the same way, in use: its
 * window clipped onto a machine switched on, as the desk shows it.
 * <p>
 * Run by the build in a java of its own, on the module's classpath: {@code <classes folder>}.
 */
public final class PicturesOfTheMachines {
  private static final long RUNNING_MILLIS = 2500;
  private static final Path KEPT = Path.of(System.getProperty("oozx.build.cache", "."), "roms");

  public static void main(String[] args) throws Exception {
    Path classes = Path.of(args[0]);
    // A module that compiles nothing, such as the one that gathers the others, has nothing to draw.
    if (!Files.isDirectory(classes)) System.exit(0);
    try (Stream<Path> all = Files.walk(classes)) {
      for (Path file : all.filter(one -> one.toString().endsWith(".class") && !one.toString().contains("$")).toList()) {
        String name = classes.relativize(file).toString().replace(java.io.File.separatorChar, '.').replaceAll("\\.class$", "");
        try {
          Class<?> kind = Class.forName(name, false, Thread.currentThread().getContextClassLoader());
          if (kind.getAnnotation(Offers.class) == null) continue;
          Path picture = Files.createDirectories(classes.resolve("META-INF/pictures")).resolve(name + ".png");
          if (Spectrum.class.isAssignableFrom(kind)) ImageIO.write(started(kind), "png", picture.toFile());
          else if (com.fpetrola.oozx.speccy.devices.Equipment.class.isAssignableFrom(kind)) inUse(kind, picture);
        } catch (Throwable cannotStartHere) {
          System.err.println("No picture of " + name + ": " + cannotStartHere);
        }
      }
    }
    System.exit(0);
  }

  /**
   * That equipment as the desk shows it in use: its window opened, unfolded and clipped onto a machine just switched
   * on, which plugs its device in, and the machine left running a moment.
   */
  private static void inUse(Class<?> kind, Path picture) throws Exception {
    Speccy speccy = switchedOn(null);
    // On a machine its boards go on: the one a desk starts with, or the first that takes them, as a Covox needs a
    // Pentagon. Its boards are those its own plugin brings, which come from where it does.
    java.util.List<com.fpetrola.oozx.speccy.peripherals.Peripheral> its = speccy.peripheralRegistry
        .allThatAre(com.fpetrola.oozx.speccy.peripherals.Peripheral.class).stream()
        .filter(one -> one.getClass().getProtectionDomain().getCodeSource().getLocation()
            .equals(kind.getProtectionDomain().getCodeSource().getLocation())).toList();
    if (!its.stream().allMatch(one -> one.fitsOn(speccy.machine.current))) {
      Spectrum fitting = speccy.machine.getMachineTypes().stream()
          .filter(model -> its.stream().allMatch(one -> one.fitsOn(model))).findFirst().orElse(null);
      if (fitting != null) {
        bringItsRoms(speccy, fitting);
        speccy.machine.select(fitting);
      }
    }
    // Its boards' ROMs too, as the desk asks for them before plugging one in: without them it only says it has none.
    for (Object device : its) bringItsRoms(speccy, device);
    com.fpetrola.oozx.speccy.devices.Equipment equipment =
        (com.fpetrola.oozx.speccy.devices.Equipment) kind.getConstructor().newInstance();
    com.fpetrola.oozx.speccy.tools.looks.DrawsTheWindows.window(picture, () -> {
      com.fpetrola.oozx.speccy.devices.MachineFrame window = equipment.open();
      window.attachTo(machineWindow(speccy));
      // Unfolded, as somebody who wants to see what it does opens it: folded it is only its bar.
      if (window.isCompact()) window.setCompact(false);
      return window;
    }, () -> running(speccy));
  }

  /** A machine's window, which is how a window clipped onto it finds the machine. */
  private static javax.swing.JInternalFrame machineWindow(Speccy speccy) {
    class Shown extends javax.swing.JInternalFrame implements com.fpetrola.oozx.speccy.devices.EmulatorWindow {
      public Speccy machine() {
        return speccy;
      }

      private final javax.swing.JComponent picture = new javax.swing.JPanel();

      Shown() {
        // As large as a machine's window opens: what is clipped along it takes its width.
        setSize(420, 360);
      }

      /** Where its screen would be, for what listens to it there, such as a mouse. */
      public javax.swing.JComponent picture() {
        return picture;
      }
    }
    return new Shown();
  }

  /** A machine switched on, as fast as it goes: the one of that kind, or the one a desk starts with when none. */
  private static Speccy switchedOn(Class<?> kind) throws java.io.IOException {
    Speccy speccy = Speccy.create();
    speccy.speed.emulation = 20000;
    speccy.init();
    Spectrum model = kind == null ? null : speccy.machine.getMachineTypes().stream().filter(kind::isInstance).findFirst().orElseThrow();
    if (model != null) bringItsRoms(speccy, model);
    speccy.machine.selectDefault();
    if (model != null) speccy.machine.select(model);
    return speccy;
  }

  private static void running(Speccy speccy) {
    for (long until = System.currentTimeMillis() + RUNNING_MILLIS; System.currentTimeMillis() < until; ) {
      speccy.loop.doOpcodes();
      speccy.scheduler.runDue();
    }
  }

  private static void bringItsRoms(Speccy speccy, Object model) throws java.io.IOException {
    com.fpetrola.oozx.config.RomFiles.askingFirst((rom, from) -> true);
    for (String rom : speccy.roms.missingFor(model)) {
      Path kept = KEPT.resolve(rom), here = com.fpetrola.oozx.config.RomFiles.kept(rom).toPath();
      Files.createDirectories(here.getParent());
      if (Files.exists(kept)) Files.copy(kept, here, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      else if (speccy.roms.bring(rom)) Files.copy(here, Files.createDirectories(KEPT).resolve(rom));
    }
  }

  /** That machine switched on and left running a moment, as fast as it goes, and what its screen shows. */
  private static BufferedImage started(Class<?> kind) throws java.io.IOException {
    Speccy speccy = switchedOn(kind);
    running(speccy);
    BufferedImage screen = new BufferedImage(Picture.WIDTH, Picture.HEIGHT, BufferedImage.TYPE_INT_RGB);
    for (int y = 0; y < Picture.HEIGHT; y++)
      for (int x = 0; x < Picture.WIDTH; x++) screen.setRGB(x, y, speccy.picture.pixels[y * Picture.STRIDE + x]);
    return screen;
  }
}
