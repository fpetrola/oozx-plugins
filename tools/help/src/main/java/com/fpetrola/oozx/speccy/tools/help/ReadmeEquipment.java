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

package com.fpetrola.oozx.speccy.tools.help;

import dev.crystal.plugins.api.Offers;

import com.fpetrola.oozx.speccy.devices.Explains;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import javax.swing.JEditorPane;
import javax.swing.JInternalFrame;
import javax.swing.JScrollPane;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Offers("Read the help")
public class ReadmeEquipment implements Explains {
  private static final String STYLE = "body { font-family: Arial, sans-serif; margin: 20px; line-height: 1.6; color: #333; }"
      + "h1 { color: #1f77b4; border-bottom: 2px solid #1f77b4; padding-bottom: 10px; }"
      + "h2 { color: #ff7f0e; margin-top: 20px; }"
      + "h3 { color: #2ca02c; }"
      + "code { background-color: #f5f5f5; padding: 2px 6px; border-radius: 3px; font-family: 'Courier New'; }"
      + "pre { background-color: #f5f5f5; padding: 10px; border-radius: 5px; overflow-x: auto; }"
      + "pre code { background-color: transparent; padding: 0; }"
      + "a { color: #1f77b4; text-decoration: none; }"
      + "blockquote { border-left: 4px solid #ddd; padding-left: 15px; color: #666; margin-left: 0; }"
      + "table { border-collapse: collapse; width: 100%; }"
      + "th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }"
      + "th { background-color: #f5f5f5; }";

  public String name() {
    return "README";
  }

  public JInternalFrame open() {
    JEditorPane page = new JEditorPane("text/html", "<html><head><style>" + STYLE + "</style></head><body>"
        + toHtml(readme()) + "</body></html>");
    page.setEditable(false);
    page.setCaretPosition(0);
    JInternalFrame frame = new JInternalFrame("README - OOZX", true, true, true, true);
    frame.add(new JScrollPane(page, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED));
    frame.setBounds(50, 50, 800, 600);
    return frame;
  }

  private static String toHtml(String markdown) {
    List<org.commonmark.Extension> tables = List.of(TablesExtension.create());
    return HtmlRenderer.builder().extensions(tables).build()
        .render(Parser.builder().extensions(tables).build().parse(markdown));
  }

  private static String readme() {
    try (InputStream in = ReadmeEquipment.class.getResourceAsStream("README.md")) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
