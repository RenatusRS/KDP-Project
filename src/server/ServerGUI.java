package server;

import javax.swing.*;
import java.awt.*;

public class ServerGUI extends JFrame {
	
	public final JTextArea textArea = new JTextArea();
	public final JScrollPane scrollPane = new JScrollPane(textArea,
			JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
	
	public ServerGUI(String title, String icon) {
		setTitle(title);
		setLocation(250, 250);
		setMinimumSize(new Dimension(500, 400));
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		
		setIconImage(Toolkit.getDefaultToolkit().getImage(getClass().getResource("/assets/" + icon + ".png")));
		
		textArea.setWrapStyleWord(true);
		textArea.setLineWrap(true);
		textArea.setOpaque(true);
		textArea.setEditable(false);
		textArea.setFocusable(true);
		
		add(scrollPane, BorderLayout.CENTER);
		
		pack();
		setVisible(true);
	}
	
	public void print(String text) {
		textArea.append(text);
		
		if (scrollPane.getVerticalScrollBar().getValue() + scrollPane.getVerticalScrollBar().getModel().getExtent() >= scrollPane.getVerticalScrollBar().getModel().getMaximum() - 30)
			textArea.setCaretPosition(textArea.getDocument().getLength());
	}
}
