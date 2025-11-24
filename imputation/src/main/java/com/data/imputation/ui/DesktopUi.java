package com.data.imputation.ui;

import com.data.imputation.service.TimeSeriesInterpolationService;
import org.springframework.stereotype.Component;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

@Component
public class DesktopUi {

    private final TimeSeriesInterpolationService interpolationService;

    public DesktopUi(TimeSeriesInterpolationService interpolationService) {
        this.interpolationService = interpolationService;
    }

    public void show() {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Time Series Imputation");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(450, 220);
            frame.setLocationRelativeTo(null);

            JLabel label = new JLabel("Drag and drop a CSV file here", SwingConstants.CENTER);
            label.setFont(label.getFont().deriveFont(Font.PLAIN, 16f));
            label.setBorder(BorderFactory.createDashedBorder(Color.GRAY));
            label.setPreferredSize(new Dimension(400, 160));

            new DropTarget(label, new FileDropTargetListener());

            frame.getContentPane().add(label);
            frame.setVisible(true);
        });
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
                Path inputPath = file.toPath();

                Path outputPath = interpolationService.processFile(inputPath);

                JOptionPane.showMessageDialog(
                        null,
                        "Interpolation complete.\nOutput file:\n" + outputPath,
                        "Done",
                        JOptionPane.INFORMATION_MESSAGE
                );
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(
                        null,
                        "Error: " + e.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }
}
