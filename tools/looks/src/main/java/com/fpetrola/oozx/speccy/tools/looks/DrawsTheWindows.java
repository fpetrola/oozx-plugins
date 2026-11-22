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
package com.fpetrola.oozx.speccy.tools.looks;

import dev.crystal.plugins.api.Offers;
import dev.plugdesk.desk.Look;
import dev.plugdesk.desk.Widgets;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * A picture of everything a plugin offers, drawn while its jar is built, in the look the desk wears first: one PNG per
 * class that says what it offers and has something to see, under META-INF/pictures and named after it, so that the jar
 * carries a picture of what it brings and the catalogue shows it before the plugin is here and without waiting when it
 * is. What opens a window is that window; a look, a few of the things a desk is made of wearing it. Anything else is
 * shown by the drawing its offer names, which the catalogue draws itself. A picture already there, drawn by an earlier
 * step of the build, stays.
 * It knows nothing of the emulator: only what each class answers.
 * <p>
 * Run by the build in a java of its own, on the plugin's classpath: {@code <classes folder>}.
 */
public final class DrawsTheWindows {
  private static final String FIRST = "Solarized Light";

  public static void main(String[] args) throws Exception {
    Path classes = Path.of(args[0]);
    // A module that compiles nothing, such as the one that gathers the others, has nothing to draw.
    if (!Files.isDirectory(classes)) System.exit(0);
    Path pictures = classes.resolve("META-INF/pictures");
    try (Stream<Path> all = Files.walk(classes)) {
      for (Path file : all.filter(one -> one.toString().endsWith(".class") && !one.toString().contains("$")).toList()) {
        String name = classes.relativize(file).toString().replace(java.io.File.separatorChar, '.').replaceAll("\\.class$", "");
        Path picture = pictures.resolve(name + ".png");
        java.util.concurrent.FutureTask<Void> drawing = new java.util.concurrent.FutureTask<>(() -> {
          draw(name, picture);
          return null;
        });
        // A window on the window's thread, as Swing asks: what it leaves for later there, such as wearing the look a
        // desk kept, comes after its picture. A look where it was: what it animates there asks for a screen.
        if (aLook(name)) drawing.run();
        else
          javax.swing.SwingUtilities.invokeLater(drawing);
        try {
          drawing.get();
        } catch (java.util.concurrent.ExecutionException cannotBeDrawnHere) {
          System.err.println("No picture of " + name + ": " + (cannotBeDrawnHere.getCause().getCause() != null
              ? cannotBeDrawnHere.getCause().getCause() : cannotBeDrawnHere.getCause()));
        }
      }
    }
    // What the windows started - timers, a gamepad being looked for - is not waited for: the pictures are written.
    System.exit(0);
  }

  /** Its picture, when it says what it offers and has something to see: the window it opens, or the look it is. */
  private static void draw(String name, Path picture) throws Exception {
    Class<?> kind = Class.forName(name, false, Thread.currentThread().getContextClassLoader());
    if (kind.getAnnotation(Offers.class) == null || Files.exists(picture)) return;
    if (!Look.class.isAssignableFrom(kind)) new DarklafLooks().wear(FIRST);
    JComponent shown = whatIsShown(kind);
    if (shown == null) return;
    // Smooth words: with no screen to ask how to draw them they come out in hard pixels. Grey, not by subpixels, as a
    // picture is shrunk and shown on any ground.
    UIManager.put(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    SwingUtilities.updateComponentTreeUI(shown);
    BufferedImage drawn = Widgets.drawn(shown);
    if (drawn == null || drawn.getWidth() < 8 || drawn.getHeight() < 8) return;
    Files.createDirectories(picture.getParent());
    ImageIO.write(drawn, "png", picture.toFile());
  }

  private static boolean aLook(String name) {
    try {
      return Look.class.isAssignableFrom(Class.forName(name, false, Thread.currentThread().getContextClassLoader()));
    } catch (ClassNotFoundException | LinkageError notHere) {
      return false;
    }
  }

  /** What there is to see of it: the window it opens or the look it is; null when it has neither. */
  private static JComponent whatIsShown(Class<?> kind) throws Exception {
    Object it = made(kind);
    if (it instanceof Look look) return wearing(look);
    if (it == null || !answers(kind, "open")) return null;
    try {
      return (JComponent) kind.getMethod("open").invoke(it);
    } catch (java.lang.reflect.InvocationTargetException opensOnlyOnADesk) {
      throw opensOnlyOnADesk;
    }
  }

  private static Object made(Class<?> kind) {
    try {
      return kind.getConstructor().newInstance();
    } catch (ReflectiveOperationException | LinkageError onlyItsMakerCanMakeIt) {
      return null;
    }
  }

  private static boolean answers(Class<?> kind, String method) {
    try {
      kind.getMethod(method);
      return true;
    } catch (NoSuchMethodException no) {
      return false;
    }
  }

  /** The things a desk is made of, wearing that look: its first theme, which is the one a person meets first. */
  private static JComponent wearing(Look look) throws Exception {
    look.wear(look.names().getFirst());
    JPanel shown = new JPanel(new GridLayout(0, 2, 10, 8));
    shown.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
    shown.add(new JLabel(look.family() + ": " + look.names().getFirst()));
    shown.add(new JTextField("Jet Set Willy"));
    JCheckBox sound = new JCheckBox("Sound", true);
    shown.add(sound);
    shown.add(new JComboBox<>(new String[]{"Spectrum 128K"}));
    JSlider speed = new JSlider(0, 100, 60);
    shown.add(speed);
    JProgressBar loading = new JProgressBar(0, 100);
    loading.setValue(70);
    shown.add(loading);
    shown.add(new JButton("Play"));
    shown.add(new JToggleButton("Pause", true));
    shown.setSize(420, 190);
    return shown;
  }
}
