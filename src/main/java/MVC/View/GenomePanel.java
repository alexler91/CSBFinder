package MVC.View;

import Utils.Gene;
import sun.plugin.javascript.JSClassLoader;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.List;
import java.util.Map;

public class GenomePanel extends JPanel {
    private JScrollPane scroll;
    private JPanel container;
    private GridBagConstraints gc;

    public GenomePanel() {
        setGCLayout();
        setLayout(new BorderLayout());
        container = new JPanel();
        container.setLayout(new GridBagLayout());

        scroll = new JScrollPane(container);
        add(scroll, BorderLayout.CENTER);
    }

    public void displayInstances(Map<String,List<List<Gene>>> instances) {
        container.removeAll();
        setData(instances);

    }

    public void setData(Map<String,List<List<Gene>>> instances) {
        container.removeAll();

        JLabel geneName;
        JScrollPane cogList;
        JPanel instancesListPanel;

        int colIndex = 0;

        // For GridBagLayout
        Insets insetName = new Insets(0, 0, 0, 5);
        Insets insetList = new Insets(0, 0, 0, 0);

        for (Map.Entry<String, List<List<Gene>>> entry: instances.entrySet()) {
            geneName = new JLabel(entry.getKey());
            geneName.setPreferredSize(new Dimension(100, 25));

            instancesListPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

            for (List<Gene> instance : entry.getValue()) {
                DefaultListModel model = new DefaultListModel();
                for (Gene gene : instance) {
                    model.addElement(gene);
                }
                JList panel = new JList(model);
                instancesListPanel.add(panel);
                panel.setVisibleRowCount(1);
                panel.setLayoutOrientation(JList.HORIZONTAL_WRAP);
            }

//            panel.setCellRenderer(new CustomListCellRenderer());
            cogList = new JScrollPane(instancesListPanel);
            cogList.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

            gc.gridx = 0; gc.gridy = colIndex; gc.weightx = 0; gc.anchor = GridBagConstraints.LINE_START; gc.insets = insetName;
            container.add(geneName, gc);
            gc.gridx = 1; gc.gridy = colIndex; gc.weightx = 2; gc.anchor = GridBagConstraints.LINE_START; gc.insets = insetList;
            container.add(cogList, gc);
            colIndex += 1;
        }
    }

    private void setGCLayout() {
        gc = new GridBagConstraints();
    }

}
