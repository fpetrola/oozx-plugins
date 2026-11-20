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

package com.fpetrola.oozx.speccy.tools.games;

import javax.swing.*;
import javax.swing.AbstractAction;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

/**
 * A file the catalogue has of a game, seen on the desk: a window like any other there, which can be made as large as
 * the desk or put away, so that a map can be studied beside the game it is of.
 */
public class DownloadViewer extends JInternalFrame {
    private static final int DEFAULT_WIDTH = 800;
    private static final int DEFAULT_HEIGHT = 600;

    public DownloadViewer(String downloadUrl, String fileName) {
        super(fileName, true, true, true, true);
        
        setSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        setDefaultCloseOperation(JInternalFrame.DISPOSE_ON_CLOSE);
        // Setup Escape key to close
        setupEscapeToClose();
        
        // Create content panel
        JPanel contentPanel = new JPanel(new BorderLayout(10, 10));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Show loading message
        JLabel loadingLabel = new JLabel("Loading " + fileName + "...");
        loadingLabel.setHorizontalAlignment(JLabel.CENTER);
        contentPanel.add(loadingLabel, BorderLayout.CENTER);
        
        add(contentPanel);
        
        // Load file asynchronously
        SwingWorker<Component, Void> worker = new SwingWorker<Component, Void>() {
            @Override
            protected Component doInBackground() throws Exception {
                return createViewerComponent(downloadUrl, fileName);
            }
            
            @Override
            protected void done() {
                try {
                    Component viewer = get();
                    contentPanel.removeAll();
                    contentPanel.add(viewer, BorderLayout.CENTER);
                    // As large as what it shows, and no larger than the screen leaves room for.
                    pack();
                    Dimension room = getParent() == null ? Toolkit.getDefaultToolkit().getScreenSize() : getParent().getSize();
                    setSize(Math.min(getWidth() + 24, room.width - 20), Math.min(getHeight() + 24, room.height - 20));
                    setLocation(Math.max(0, (room.width - getWidth()) / 2), Math.max(0, (room.height - getHeight()) / 2));
                } catch (Exception e) {
                    contentPanel.removeAll();
                    JTextArea errorArea = new JTextArea();
                    errorArea.setText("Error loading file:\n" + e.getMessage());
                    errorArea.setEditable(false);
                    contentPanel.add(new JScrollPane(errorArea), BorderLayout.CENTER);
                    contentPanel.revalidate();
                    contentPanel.repaint();
                }
            }
        };
        worker.execute();
    }
    
    /**
     * Create appropriate viewer component based on file type
     */
    private Component createViewerComponent(String downloadUrl, String fileName) throws IOException {
        String lowerFileName = fileName.toLowerCase();
        
        // Determine file type and create appropriate viewer
        if (isImageFile(lowerFileName)) {
            return createImageViewer(downloadUrl);
        } else if (isTextFile(lowerFileName)) {
            return createTextViewer(downloadUrl);
        } else {
            return createScreenViewer(downloadUrl);
        }
    }

    /** Whether this window shows that file; what it does not, the person's own programs open: a PDF, a sound. */
    static boolean shows(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".scr") || lower.matches(".*\\.(jpg|jpeg|png|gif|bmp|txt|md|html|htm|xml|json|csv|log)");
    }

    private Component createScreenViewer(String downloadUrl) throws IOException {
        try (InputStream in = new URL(downloadUrl).openStream()) {
            return dev.plugdesk.desk.Widgets.zooming(screen(in.readAllBytes()));
        }
    }

    /** A Spectrum screen as it is kept: 6144 bytes of pixels in the display's own order, then 768 of colours, a cell each. */
    static java.awt.image.BufferedImage screen(byte[] screen) throws IOException {
        if (screen.length < 6912) throw new IOException("not a Spectrum screen: " + screen.length + " bytes");
        java.awt.image.BufferedImage picture = new java.awt.image.BufferedImage(256, 192, java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 192; y++) {
            int line = ((y & 0xC0) << 5) | ((y & 0x07) << 8) | ((y & 0x38) << 2);
            for (int x = 0; x < 256; x++) {
                int colours = screen[6144 + (y / 8) * 32 + x / 8] & 0xFF, level = (colours & 0x40) != 0 ? 0xFF : 0xD8;
                boolean ink = (screen[line + x / 8] & (0x80 >> (x & 7))) != 0;
                int colour = ink ? colours & 7 : (colours >> 3) & 7;
                picture.setRGB(x, y, ((colour & 2) != 0 ? level << 16 : 0) | ((colour & 4) != 0 ? level << 8 : 0) | ((colour & 1) != 0 ? level : 0));
            }
        }
        return picture;
    }

    private Component createImageViewer(String downloadUrl) throws IOException {
        java.awt.image.BufferedImage picture = javax.imageio.ImageIO.read(new URL(downloadUrl));
        if (picture == null) throw new IOException("not a picture this can read");
        return dev.plugdesk.desk.Widgets.zooming(picture);
    }

    private Component createTextViewer(String downloadUrl) throws IOException {
        URL url = new URL(downloadUrl);
        URLConnection conn = url.openConnection();
        InputStream inputStream = conn.getInputStream();
        
        StringBuilder content = new StringBuilder();
        byte[] buffer = new byte[1024];
        int bytesRead;
        
        try {
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                content.append(new String(buffer, 0, bytesRead));
            }
        } finally {
            inputStream.close();
        }
        
        JTextArea textArea = new JTextArea();
        textArea.setText(content.toString());
        textArea.setEditable(false);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        
        JScrollPane scrolling = new JScrollPane(textArea);
        scrolling.setPreferredSize(new Dimension(780, 560));
        return scrolling;
    }
    
    private boolean isImageFile(String fileName) {
        return fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || 
               fileName.endsWith(".png") || fileName.endsWith(".gif") ||
               fileName.endsWith(".bmp") || fileName.endsWith(".webp");
    }
    
    private boolean isTextFile(String fileName) {
        return fileName.endsWith(".txt") || fileName.endsWith(".md") ||
               fileName.endsWith(".html") || fileName.endsWith(".htm") ||
               fileName.endsWith(".xml") || fileName.endsWith(".json") ||
               fileName.endsWith(".csv") || fileName.endsWith(".log");
    }
    
    private void setupEscapeToClose() {
        KeyStroke escapeKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(escapeKeyStroke, "closeDialog");
        getRootPane().getActionMap().put("closeDialog", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
    }
}
