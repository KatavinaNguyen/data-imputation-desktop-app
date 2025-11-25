package com.data.imputation.ui;

import com.data.imputation.service.TimeSeriesInterpolationService;
import org.springframework.stereotype.Component;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

@Component
public class DesktopUi {

    private final TimeSeriesInterpolationService interpolationService;

    private JTextField suffixField;
    private JLabel exampleLabel;

    // status / progress area
    private JPanel statusPanel;
    private JLabel statusLabel;
    private JLabel statusFileLabel;
    private JProgressBar statusProgressBar;
    private JLabel cancelLabel;
    private SwingWorker<Path, Void> currentWorker;

    // Neutral dark grey theme
    private static final Color BG_MAIN      = new Color(12, 12, 14);   // window background
    private static final Color BG_PANEL     = new Color(22, 22, 24);   // top panel
    private static final Color BG_INPUT     = new Color(32, 32, 36);   // text field / surfaces

    private static final Color FG_PRIMARY   = new Color(232, 232, 235); // main text
    private static final Color FG_MUTED     = new Color(140, 140, 146); // secondary text

    // buttons: white, light grey hover, black text
    private static final Color BTN_BG       = new Color(250, 250, 250);
    private static final Color BTN_BG_HOVER = new Color(230, 230, 230);
    private static final Color BTN_BORDER   = new Color(180, 180, 185);
    private static final Color BTN_TEXT     = Color.BLACK;

    private static final Color BORDER_SOFT  = new Color(54, 54, 60);   // subtle outlines
    private static final Color DROP_BORDER  = new Color(70, 70, 78);   // dashed box border

    private static final String BASE_FONT_FAMILY = "SansSerif";

    // simple limit hint for UI
    private static final int MAX_FILE_MB = 10;

    public DesktopUi(TimeSeriesInterpolationService interpolationService) {
        this.interpolationService = interpolationService;
    }

    public void show() {
        SwingUtilities.invokeLater(() -> {
            installBaseLookAndFeel();

            Font baseFont = new Font(BASE_FONT_FAMILY, Font.PLAIN, 12);

            JFrame frame = new JFrame("Missing Data Imputation");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(580, 420);
            frame.setLocationRelativeTo(null);
            frame.getContentPane().setBackground(BG_MAIN);
            frame.setLayout(new BorderLayout(8, 8));
            frame.setFont(baseFont);

            // ---------- TOP: "Add File Tag" + textbox + Timestamp (one line) ----------
            JPanel topPanel = new JPanel();
            topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
            topPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 8, 12));
            topPanel.setBackground(BG_PANEL);

            JPanel rowPanel = new JPanel(new GridBagLayout());
            rowPanel.setOpaque(false);

            GridBagConstraints topGbc = new GridBagConstraints();
            topGbc.gridy = 0;
            topGbc.insets = new Insets(0, 0, 0, 8);
            topGbc.anchor = GridBagConstraints.WEST;

            JLabel titleLabel = new JLabel("Add File Tag");
            titleLabel.setForeground(FG_PRIMARY);
            titleLabel.setFont(baseFont.deriveFont(Font.BOLD, 13f));

            topGbc.gridx = 0;
            topGbc.weightx = 0;
            topGbc.fill = GridBagConstraints.NONE;
            rowPanel.add(titleLabel, topGbc);

            suffixField = new JTextField();
            suffixField.setToolTipText("Suffix to append to filename (e.g. 2025-01-01, final, imp)");
            suffixField.setBackground(BG_INPUT);
            suffixField.setForeground(FG_PRIMARY);
            suffixField.setCaretColor(FG_PRIMARY);
            suffixField.setFont(baseFont.deriveFont(13f));
            suffixField.setBorder(new RoundedBorder(20, BORDER_SOFT));
            suffixField.setOpaque(true);

            topGbc.gridx = 1;
            topGbc.weightx = 1.0;
            topGbc.fill = GridBagConstraints.HORIZONTAL;
            rowPanel.add(suffixField, topGbc);

            JButton timestampButton = new JButton("Timestamp");
            timestampButton.setToolTipText("Fill with today's date (yyyy-MM-dd)");
            timestampButton.setFont(baseFont.deriveFont(Font.BOLD, 12f));
            styleWhiteButton(timestampButton);
            timestampButton.addActionListener(e -> {
                String today = LocalDate.now().toString();
                suffixField.setText(today);
            });

            topGbc.gridx = 2;
            topGbc.weightx = 0;
            topGbc.fill = GridBagConstraints.NONE;
            rowPanel.add(timestampButton, topGbc);

            exampleLabel = new JLabel();
            exampleLabel.setFont(baseFont.deriveFont(11f));
            exampleLabel.setForeground(FG_MUTED);

            suffixField.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) { updateExampleLabel(); }
                @Override public void removeUpdate(DocumentEvent e) { updateExampleLabel(); }
                @Override public void changedUpdate(DocumentEvent e) { updateExampleLabel(); }
            });
            updateExampleLabel();

            topPanel.add(rowPanel);
            topPanel.add(Box.createVerticalStrut(6));
            topPanel.add(exampleLabel);

            // Divider line
            JSeparator separator = new JSeparator();
            separator.setForeground(BORDER_SOFT);
            separator.setBackground(BG_MAIN);

            // ---------- BOTTOM: drag-and-drop area ----------
            DropAreaPanel dropArea = new DropAreaPanel();
            dropArea.setLayout(new GridBagLayout());
            dropArea.setBackground(BG_MAIN);

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.weightx = 1.0;
            gbc.anchor = GridBagConstraints.CENTER;
            gbc.fill   = GridBagConstraints.NONE;
            gbc.insets = new Insets(2, 4, 2, 4);

            JLabel iconLabel = new JLabel();
            loadCloudIcon(iconLabel);

            JLabel dragLabel = new JLabel("Drag & drop to upload");
            dragLabel.setFont(baseFont.deriveFont(Font.BOLD, 15f));
            dragLabel.setForeground(FG_PRIMARY);

            JButton chooseFileButton = new JButton("Choose File");
            chooseFileButton.setFont(baseFont.deriveFont(Font.BOLD, 12f));
            styleWhiteButton(chooseFileButton);

            JLabel limitLabel = new JLabel("(up to " + MAX_FILE_MB + "MB)");
            limitLabel.setFont(baseFont.deriveFont(11f));
            limitLabel.setForeground(FG_MUTED);

            // message to user about actions
            JLabel infoLabel = new JLabel(
                    "<html><br><br><i>File cleaning will begin immediately upon selection.</i></html>"
            );
            infoLabel.setFont(limitLabel.getFont());
            infoLabel.setForeground(limitLabel.getForeground());

            // top spacer
            gbc.gridy = 0;
            gbc.weighty = 1.0;
            dropArea.add(Box.createVerticalStrut(4), gbc);

            // icon
            gbc.gridy = 1;
            gbc.weighty = 0;
            dropArea.add(iconLabel, gbc);

            // "Drag & drop to upload"
            gbc.gridy = 2;
            dropArea.add(dragLabel, gbc);

            // Choose File button
            gbc.gridy = 3;
            dropArea.add(chooseFileButton, gbc);

            // "(up to X MB)"
            gbc.gridy = 4;
            dropArea.add(limitLabel, gbc);

            // italic message under it
            gbc.gridy = 5;
            dropArea.add(infoLabel, gbc);

            // bottom spacer
            gbc.gridy = 6;
            gbc.weighty = 1.0;
            dropArea.add(Box.createVerticalStrut(4), gbc);

            // Drag & drop support
            new DropTarget(dropArea, new FileDropTargetListener());

            // Choose File click -> open file chooser
            chooseFileButton.addActionListener(e -> {
                JFileChooser chooser = new JFileChooser();
                chooser.setDialogTitle("Choose CSV file");
                int result = chooser.showOpenDialog(frame);
                if (result == JFileChooser.APPROVE_OPTION) {
                    File selected = chooser.getSelectedFile();
                    if (selected != null) {
                        handleFile(selected.toPath());
                    }
                }
            });

            // ---------- STATUS PANEL (progress + cancel + filename) ----------
            statusPanel = new JPanel(new BorderLayout(8, 0));
            statusPanel.setBackground(BG_MAIN);
            statusPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 4));

            statusLabel = new JLabel("");
            statusLabel.setForeground(FG_PRIMARY);
            statusLabel.setFont(baseFont.deriveFont(11f));

            statusFileLabel = new JLabel("");
            statusFileLabel.setForeground(FG_MUTED);
            statusFileLabel.setFont(baseFont.deriveFont(11f));

            JPanel labelStack = new JPanel();
            labelStack.setLayout(new BoxLayout(labelStack, BoxLayout.Y_AXIS));
            labelStack.setOpaque(false);
            labelStack.add(statusLabel);
            labelStack.add(statusFileLabel);

            statusProgressBar = new JProgressBar();
            statusProgressBar.setIndeterminate(false);
            statusProgressBar.setVisible(false);
            statusProgressBar.setBorder(BorderFactory.createEmptyBorder());
            statusProgressBar.setPreferredSize(new Dimension(180, 10));

            cancelLabel = new JLabel("CANCEL");
            cancelLabel.setForeground(new Color(90, 150, 255));
            cancelLabel.setFont(baseFont.deriveFont(Font.BOLD, 11f));
            cancelLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            cancelLabel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    // only react if a job is actually running
                    if (currentWorker != null && !currentWorker.isDone()) {
                        cancelCurrentWorker();
                    }
                }
            });

            statusPanel.add(labelStack, BorderLayout.WEST);
            statusPanel.add(statusProgressBar, BorderLayout.CENTER);
            statusPanel.add(cancelLabel, BorderLayout.EAST);
            statusPanel.setVisible(false);
            cancelLabel.setVisible(false);   // start hidden


            // wrap bottom area: drop area + status panel
            JPanel dropWrapper = new JPanel(new BorderLayout());
            dropWrapper.setBorder(BorderFactory.createEmptyBorder(8, 12, 12, 12));
            dropWrapper.setBackground(BG_MAIN);
            dropWrapper.add(dropArea, BorderLayout.CENTER);
            dropWrapper.add(statusPanel, BorderLayout.SOUTH);

            frame.getContentPane().add(topPanel, BorderLayout.NORTH);
            frame.getContentPane().add(separator, BorderLayout.CENTER);
            frame.getContentPane().add(dropWrapper, BorderLayout.SOUTH);

            frame.setVisible(true);
        });
    }

    // ---------- styling helpers ----------

    private void installBaseLookAndFeel() {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignored) { }

        UIManager.put("control", BG_MAIN);
        UIManager.put("info", BG_PANEL);
        UIManager.put("nimbusBase", new Color(30, 30, 34));
        UIManager.put("nimbusBlueGrey", new Color(50, 50, 58));
        UIManager.put("nimbusLightBackground", BG_PANEL);
        UIManager.put("text", FG_PRIMARY);
    }

    private void styleWhiteButton(JButton button) {
        button.setBackground(BTN_BG);
        button.setForeground(BTN_TEXT);
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(true);
        button.setBorder(new RoundedBorder(1, BTN_BORDER));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        Color normalBg = BTN_BG;
        Color hoverBg  = BTN_BG_HOVER;

        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(hoverBg);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(normalBg);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                button.setBackground(hoverBg.darker());
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (button.getBounds().contains(e.getPoint())) {
                    button.setBackground(hoverBg);
                } else {
                    button.setBackground(normalBg);
                }
            }
        });
    }

    private void updateExampleLabel() {
        if (exampleLabel == null) return;

        String suffix = (suffixField != null) ? suffixField.getText().trim() : "";
        String htmlSuffix = escapeHtml(suffix);

        String text;
        if (suffix.isEmpty()) {
            text = "<html><i>filename</i>.csv</html>";
        } else {
            text = "<html><i>filename</i>_" + htmlSuffix + ".csv</html>";
        }
        exampleLabel.setText(text);
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private void loadCloudIcon(JLabel iconLabel) {
        try {
            URL iconUrl = getClass().getResource("/ui/upload-icon.png");
            if (iconUrl != null) {
                ImageIcon rawIcon = new ImageIcon(iconUrl);
                java.awt.Image img = rawIcon.getImage();
                int w = rawIcon.getIconWidth();
                int h = rawIcon.getIconHeight();

                if (w > 60) {
                    int newW = 60;
                    int newH = (int) ((double) h * newW / w);
                    java.awt.Image scaled =
                            img.getScaledInstance(newW, newH, java.awt.Image.SCALE_SMOOTH);
                    iconLabel.setIcon(new ImageIcon(scaled));
                } else {
                    iconLabel.setIcon(rawIcon);
                }
            }
        } catch (Exception ignored) {
        }
    }

    // ---------- processing + status ----------

    private void handleFile(Path inputPath) {
        if (currentWorker != null && !currentWorker.isDone()) {
            // already processing; ignore additional requests
            return;
        }

        String suffix = (suffixField != null) ? suffixField.getText() : "";

        statusPanel.setVisible(true);
        statusLabel.setText("Processing file...");
        statusFileLabel.setText(inputPath.getFileName().toString());
        statusProgressBar.setIndeterminate(true);
        statusProgressBar.setVisible(true);
        cancelLabel.setEnabled(true);
        cancelLabel.setForeground(new Color(90, 150, 255));

        SwingWorker<Path, Void> worker = new SwingWorker<>() {
            @Override
            protected Path doInBackground() throws Exception {
                return interpolationService.processFile(inputPath, suffix);
            }

            @Override
            protected void done() {
                try {
                    if (isCancelled()) {
                        statusLabel.setText("Cancelled.");
                        statusProgressBar.setVisible(false);
                        cancelLabel.setEnabled(false);
                        cancelLabel.setForeground(FG_MUTED);
                        cancelLabel.setVisible(false);   // hide after cancel
                        return;
                    }

                    Path output = get(); 
                    statusLabel.setText("File complete.");
                    statusFileLabel.setText(output.getFileName().toString());
                    statusProgressBar.setIndeterminate(false);
                    statusProgressBar.setVisible(false);
                    cancelLabel.setEnabled(false);
                    cancelLabel.setForeground(FG_MUTED);
                    cancelLabel.setVisible(false);       // hide after success

                } catch (Exception e) {
                    // handles InterruptedException / ExecutionException
                    statusLabel.setText("Error: " + e.getMessage());
                    statusFileLabel.setText("");
                    statusProgressBar.setVisible(false);
                    cancelLabel.setEnabled(false);
                    cancelLabel.setForeground(FG_MUTED);
                    cancelLabel.setVisible(false);
                    e.printStackTrace();
                } finally {
                    currentWorker = null;
                }
            }
        };

        currentWorker = worker;
        worker.execute();
    }

    private void cancelCurrentWorker() {
        if (currentWorker != null && !currentWorker.isDone()) {
            currentWorker.cancel(true);
            statusLabel.setText("Cancelling...");
            cancelLabel.setEnabled(false);
        }
    }

    private class FileDropTargetListener extends DropTargetAdapter {
        @Override
        public void drop(DropTargetDropEvent dtde) {
            try {
                dtde.acceptDrop(DnDConstants.ACTION_COPY);
                @SuppressWarnings("unchecked")
                List<File> droppedFiles = (List<File>) dtde.getTransferable()
                        .getTransferData(DataFlavor.javaFileListFlavor);

                if (droppedFiles.isEmpty()) return;
                File file = droppedFiles.get(0);
                handleFile(file.toPath());
            } catch (Exception e) {
                e.printStackTrace();
                statusPanel.setVisible(true);
                statusLabel.setText("Error: " + e.getMessage());
                statusFileLabel.setText("");
                statusProgressBar.setVisible(false);
            }
        }
    }

    // Rounded rectangle with dashed border; preferred size for drop box
    private static class DropAreaPanel extends JPanel {
        @Override
        public Dimension getPreferredSize() {
            return new Dimension(520, 250);
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int inset = 8;
            int x = inset;
            int y = inset;
            int w = getWidth() - 2 * inset;
            int h = getHeight() - 2 * inset;
            int arc = 16;

            float[] dash = {6f, 6f};
            g2.setColor(DROP_BORDER);
            g2.setStroke(new BasicStroke(
                    1.8f,
                    BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND,
                    0f,
                    dash,
                    0f
            ));

            g2.draw(new RoundRectangle2D.Float(x, y, w, h, arc, arc));
            g2.dispose();
        }
    }

    // Rounded border for text field and buttons
    private static class RoundedBorder implements Border {
        private final int radius;
        private final Color color;

        RoundedBorder(int radius, Color color) {
            this.radius = radius;
            this.color = color;
        }

        @Override
        public Insets getBorderInsets(java.awt.Component c) {
            return new Insets(6, 14, 6, 14);
        }

        @Override
        public boolean isBorderOpaque() {
            return false;
        }

        @Override
        public void paintBorder(java.awt.Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
            g2.dispose();
        }
    }
}
