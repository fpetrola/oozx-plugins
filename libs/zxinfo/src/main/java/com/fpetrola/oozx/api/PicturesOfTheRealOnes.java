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

package com.fpetrola.oozx.api;

import dev.crystal.plugins.api.Offers;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * The pictures ZXDB has of what a plugin offers, copied into the plugin while it is built: each class whose offer says
 * which entry it is ({@code known = "zxdb:N"}) gets them under META-INF/pictures, named after it, so the catalogue
 * shows them without waiting for anybody. A build with no network, or with both services down, goes on without them;
 * what was fetched once is kept in the person's cache and not asked for again.
 * <p>
 * Run by the build in a java of its own, on the plugin's classpath: {@code <classes folder>}.
 */
public final class PicturesOfTheRealOnes {
  private static final String KNOWN = "zxdb:";
  private static final int MOST = 3, WIDEST = 800;
  private static final Path KEPT = Path.of(System.getProperty("user.home"), ".cache", "oozx-build", "zxdb-pictures");

  public static void main(String[] args) throws IOException {
    Path classes = Path.of(args[0]);
    // A module that compiles nothing, such as the one that gathers the others, has nothing to draw.
    if (!Files.isDirectory(classes)) System.exit(0);
    try (Stream<Path> all = Files.walk(classes)) {
      for (Path file : all.filter(one -> one.toString().endsWith(".class") && !one.toString().contains("$")).toList()) {
        String name = classes.relativize(file).toString().replace(java.io.File.separatorChar, '.').replaceAll("\\.class$", "");
        try {
          Offers offers = Class.forName(name, false, Thread.currentThread().getContextClassLoader()).getAnnotation(Offers.class);
          if (offers == null || !offers.known().startsWith(KNOWN)) continue;
          List<Path> kept = kept(offers.known().substring(KNOWN.length()));
          for (int i = 0; i < kept.size(); i++)
            copy(kept.get(i), classes.resolve("META-INF/pictures").resolve(name + ".real-" + (i + 1) + ".jpg"));
        } catch (Throwable notThisOne) {
          System.err.println("No pictures of " + name + ": " + notThisOne);
        }
      }
    }
    System.exit(0);
  }

  /** The pictures of that entry in the cache, fetched first if they are not there yet. */
  private static List<Path> kept(String id) throws IOException {
    Path folder = KEPT.resolve(id);
    if (!Files.isDirectory(folder)) {
      GameEntry entry = entry(String.format("%07d", Integer.parseInt(id)));
      if (entry == null) return List.of();
      Path fetching = Files.createDirectories(KEPT).resolve(id + ".fetching");
      Files.createDirectories(fetching);
      int n = 0;
      for (String address : entry.pictures()) {
        if (n == MOST) break;
        BufferedImage picture = ImageIO.read(java.net.URI.create(address).toURL());
        if (picture != null) ImageIO.write(small(picture), "jpg", fetching.resolve(++n + ".jpg").toFile());
      }
      Files.move(fetching, folder);
    }
    try (Stream<Path> pictures = Files.list(folder)) {
      return pictures.sorted().toList();
    }
  }

  /** The entry, from this project's service first and ZXInfo's after: either may be down for days. */
  private static GameEntry entry(String id) {
    for (String address : List.of(ZxInfoClient.OURS, ZxInfoClient.ZXINFO)) {
      try {
        return ZxInfoClient.at(address, client -> client.getGameDetails(id, ZxInfoClient.MODE_FULL)).getGameEntry();
      } catch (RuntimeException notAnswering) {
        System.err.println(address + " did not answer for " + id + ": " + notAnswering.getMessage());
      }
    }
    return null;
  }

  /** No wider than a catalogue shows it, and with no transparency, which is what a JPEG holds: scans are megabytes. */
  private static BufferedImage small(BufferedImage picture) {
    double scale = Math.min(1, (double) WIDEST / picture.getWidth());
    int width = (int) Math.round(picture.getWidth() * scale), height = (int) Math.round(picture.getHeight() * scale);
    BufferedImage small = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    java.awt.Graphics2D drawing = small.createGraphics();
    drawing.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    drawing.drawImage(picture, 0, 0, width, height, java.awt.Color.WHITE, null);
    drawing.dispose();
    return small;
  }

  private static void copy(Path from, Path to) throws IOException {
    Files.createDirectories(to.getParent());
    Files.copy(from, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
  }
}
