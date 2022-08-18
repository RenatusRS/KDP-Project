package subserver;

import javax.swing.*;
import java.awt.*;

public class SubserverGUI extends JFrame {
	
	public final JTextArea textArea = new JTextArea();
	
	public SubserverGUI(long id) {
		setTitle("Subserver [" + id + "]");
		setLocation(250, 250);
		setMinimumSize(new Dimension(500, 400));
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		
		setIconImage(Toolkit.getDefaultToolkit().getImage(getClass().getResource("/assets/subserver.png")));
		
		textArea.setEditable(false);
		
		add(new JScrollPane(textArea,
						JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED),
				BorderLayout.CENTER);
		
		pack();
		setVisible(true);
	}
	
	public void print(String text) {
		textArea.append(text);
		textArea.setCaretPosition(textArea.getDocument().getLength());
	}
}
