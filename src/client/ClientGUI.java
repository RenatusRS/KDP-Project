package client;

import shared.Room;
import shared.Video;

import javax.security.auth.login.LoginException;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class ClientGUI extends JFrame {
	final Client owner;
	
	final JTabbedPane tabbedPane = new JTabbedPane();
	
	private final JTextField textUsername = new JTextField();
	private final JTextField textPassword = new JPasswordField();
	private final JTextField textPasswordConfirm = new JPasswordField();
	private final JButton buttonLogin = new JButton("Login");
	
	private final JPanel notificationsPanel = new JPanel(new GridLayout(0, 1));
	private final JPanel videosPanel = new JPanel(new GridLayout(0, 1));
	
	final JLabel labelConnection = new JLabel("STATUS: Disconnected", JLabel.LEFT);
	
	private final Player player = new Player();
	
	final JComboBox<Room> rooms = new JComboBox<>();
	
	final JPanel usersPanel = new JPanel();
	
	final JButton uploadButton = new JButton("Upload");
	
	private String localVideo = null;
	private long localTime = 0;
	
	private Thread thread;
	
	final JPanel loginPanel = loginPanel();
	final JPanel browsePanel = browsePanel();
	final JPanel watchPanel = watchPanel();
	
	public ClientGUI(Client owner) {
		this.owner = owner;
		
		setTitle("KDP Project");
		setBounds(250, 250, 1080, 720);
		
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		
		setIconImage(Toolkit.getDefaultToolkit().getImage(getClass().getResource("/assets/client.png")));
		
		tabbedPane.add(loginPanel, "Login");
		add(tabbedPane, BorderLayout.CENTER);
		add(labelConnection, BorderLayout.PAGE_END);
		
		setVisible(true);
	}
	
	private JPanel loginPanel() {
		JPanel loginPanel = new JPanel(new GridLayout(0, 1, 5, 15));
		
		JLabel labelError = new JLabel("", JLabel.CENTER);
		
		labelError.setForeground(Color.red);
		
		textPasswordConfirm.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				updateFieldState();
			}
			
			@Override
			public void removeUpdate(DocumentEvent e) {
				updateFieldState();
			}
			
			@Override
			public void changedUpdate(DocumentEvent e) {
				updateFieldState();
			}
			
			private void updateFieldState() {
				buttonLogin.setText(textPasswordConfirm.getText().isEmpty() ? "Login" : "Registration");
			}
		});
		
		buttonLogin.addActionListener((ae) -> {
			try {
				if (textUsername.getText().isBlank()) throw new LoginException("Missing username!");
				if (textPassword.getText().isEmpty()) throw new LoginException("Missing password!");
				
				owner.username = textUsername.getText();
				
				if (textPasswordConfirm.getText().isEmpty()) owner.signIn(textUsername.getText(), textPassword.getText(), false);
				else {
					if (!textPassword.getText().equals(textPasswordConfirm.getText())) throw new LoginException("Password confirmation incorrect!");
					
					owner.signIn(textUsername.getText(), textPassword.getText(), true);
				}
				
				owner.loggedIn(textUsername.getText());
			} catch (LoginException e) {
				labelError.setText(e.getMessage());
			} catch (RemoteException e) {
				labelError.setText("Couldn't connect to the central server.");
			} catch (NotBoundException e) {
				e.printStackTrace();
			}
		});
		
		loginPanel.add(new JLabel("Username"));
		loginPanel.add(textUsername);
		loginPanel.add(new JLabel("Password"));
		loginPanel.add(textPassword);
		loginPanel.add(new JLabel("Confirm Password"));
		loginPanel.add(textPasswordConfirm);
		loginPanel.add(labelError);
		loginPanel.add(buttonLogin);
		
		return loginPanel;
	}
	
	private JPanel browsePanel() {
		notificationsPanel.setLayout(new BoxLayout(notificationsPanel, BoxLayout.PAGE_AXIS));
		notificationsPanel.setBorder(BorderFactory.createTitledBorder("Notifications"));
		
		JScrollPane videosScroll = new JScrollPane(videosPanel,
				JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		
		JScrollPane notificationsScroll = new JScrollPane(notificationsPanel,
				JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		
		videosPanel.setLayout(new BoxLayout(videosPanel, BoxLayout.PAGE_AXIS));
		videosPanel.setBorder(BorderFactory.createTitledBorder("Videos"));
		
		JPanel uploadPanel = new JPanel(new BorderLayout());
		uploadPanel.setBorder(BorderFactory.createTitledBorder("Upload"));
		
		JFileChooser fileChooser = new JFileChooser();
		JLabel fileLabel = new JLabel();
		JButton fileButton = new JButton("Choose file");
		
		fileButton.addActionListener((ae) -> {
			if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) fileLabel.setText(fileChooser.getSelectedFile().getAbsolutePath());
		});
		
		uploadButton.addActionListener((ae) -> (new Thread(() -> {
			File file = fileChooser.getSelectedFile();
			if (file == null) return;
			
			try {
				if (!owner.subserver.validateVideo(file.getName())) return;
				
				try (InputStream is = new FileInputStream(file)) {
					int readBytes;
					byte[] b = new byte[1024 * 1024 * 32];
					
					while ((readBytes = is.read(b)) != -1) owner.subserver.addVideo(new Video(file.getName(), b, readBytes));
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
				
				owner.subserver.finalizeVideo(file.getName());
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		})).start());
		
		uploadPanel.add(fileLabel, BorderLayout.LINE_START);
		uploadPanel.add(fileButton, BorderLayout.CENTER);
		uploadPanel.add(uploadButton, BorderLayout.LINE_END);
		
		JPanel mainPanel = new JPanel(new BorderLayout());
		
		mainPanel.add(uploadPanel, BorderLayout.PAGE_START);
		mainPanel.add(videosScroll, BorderLayout.CENTER);
		
		JPanel browsePanel = new JPanel(new BorderLayout());
		
		browsePanel.add(mainPanel, BorderLayout.CENTER);
		browsePanel.add(notificationsScroll, BorderLayout.LINE_END);
		
		return browsePanel;
	}
	
	private JPanel watchPanel() {
		JButton createRoom = new JButton("Create Room");
		
		rooms.addItemListener(e -> {
			if (e.getStateChange() != ItemEvent.SELECTED || tabbedPane.getSelectedIndex() != 1) return;
			
			Room room = (Room) e.getItem();
			
			player.setControls(owner.username.equals(room.owner));
			
			if (thread != null) thread.interrupt();
			
			if (room.toString().equals("0 | " + owner.username + " | Local Room")) {
				if (localVideo != null) {
					play(localVideo);
					player.pause(true);
					player.seek(localTime);
				}
				
				createRoom.setEnabled(true);
			} else {
				try {
					room = owner.subserver.getRoomData(room.getID());
				} catch (RemoteException ex) {
					ex.printStackTrace();
				}
				
				play(room.video);
				player.pause(room.getPaused());
				player.seek(room.getTime());
				
				createRoom.setEnabled(false);
				
				Room finalRoom = room;
				
				thread = new Thread(() -> {
					Room temp;
					
					try {
						while (true) {
							try {
								if (finalRoom.owner.equals(owner.username)) owner.subserver.setRoomData(finalRoom.getID(), player.progress.getValue(), player.getPaused());
								else {
									temp = owner.subserver.getRoomData(finalRoom.getID());
									
									System.out.println(temp.getTime() + " - " + player.getTime() + " = " + Math.abs(temp.getTime() - player.getTime()));
									if (temp.getPaused()) {
										player.pause(true);
										if (temp.getTime() != player.getTime()) player.seek(temp.getTime());
									} else {
										if (player.getPaused() || Math.abs(temp.getTime() - player.getTime()) > 1000) player.seek(temp.getTime());
										player.pause(false);
									}
								}
							} catch (RemoteException e1) {
								if (!finalRoom.owner.equals(owner.username) && !player.getPaused()) player.pause(true);
								addNotification("Lost connection to the server, stopping room updates");
								this.wait();
							}
							
							Thread.sleep(1000);
						}
					} catch (InterruptedException ignored) {
					}
				});
				
				thread.start();
			}
		});
		
		createRoom.addActionListener((ae) -> {
			if (player.getVideo() == null) return;
			
			ArrayList<String> viewers = new ArrayList<>();
			
			for (Component user : usersPanel.getComponents())
				if (user instanceof JCheckBox checkBox && checkBox.isSelected()) {
					viewers.add(checkBox.getText());
					checkBox.setSelected(false);
				}
			
			if (viewers.isEmpty()) return;
			
			viewers.add(owner.username);
			
			try {
				owner.subserver.createRoom(new Room(player.getVideo(), owner.username, viewers));
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		});
		
		JPanel roomPanel = new JPanel(new BorderLayout());
		usersPanel.setLayout(new BoxLayout(usersPanel, BoxLayout.PAGE_AXIS));
		
		roomPanel.add(rooms, BorderLayout.PAGE_START);
		roomPanel.add(usersPanel, BorderLayout.CENTER);
		roomPanel.add(createRoom, BorderLayout.PAGE_END);
		
		JPanel watchPanel = new JPanel(new BorderLayout());
		
		watchPanel.add(player, BorderLayout.CENTER);
		watchPanel.add(roomPanel, BorderLayout.LINE_END);
		
		return watchPanel;
	}
	
	public void addRoom(Room room) {
		if (rooms.getItemCount() == 0) {
			Room localRoom = new Room("Local Room", owner.username, null);
			localRoom.setID(0);
			rooms.addItem(localRoom);
		}
		
		rooms.addItem(room);
		
		if (!owner.username.equals(room.owner)) addNotification("User '" + room.owner + "' has added you to their room playing '" + room.video + "'");
	}
	
	public void addNotification(String text) {
		JPanel notification = new JPanel(new BorderLayout());
		
		notification.add(new JLabel(text), BorderLayout.LINE_START);
		notification.add(new JLabel(new SimpleDateFormat("   dd/MM/yyyy HH:mm:ss").format(new Date())), BorderLayout.LINE_END);
		
		notification.setMaximumSize(new Dimension(Integer.MAX_VALUE, notification.getMinimumSize().height));
		
		notificationsPanel.add(notification);
		revalidate();
	}
	
	public void addVideo(String title) {
		JPanel video = new JPanel();
		
		video.setMaximumSize(new Dimension(100, 100));
		
		video.setBorder(BorderFactory.createLineBorder(Color.black));
		video.setBackground(Color.GRAY);
		
		video.add(new JLabel(title));
		
		video.setMaximumSize(new Dimension(Integer.MAX_VALUE, video.getMinimumSize().height));
		
		video.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				if (rooms.getItemCount() == 0) {
					Room localRoom = new Room("Local Room", owner.username, null);
					localRoom.setID(0);
					addRoom(localRoom);
				}
				
				rooms.setSelectedIndex(0);
				tabbedPane.setSelectedIndex(1);
				
				play(title);
			}
		});
		
		videosPanel.add(video);
		revalidate();
	}
	
	public void addUser(String username) {
		JCheckBox user = new JCheckBox(username);
		user.setMaximumSize(new Dimension(Integer.MAX_VALUE, user.getMinimumSize().height));
		
		usersPanel.add(user);
		revalidate();
	}
	
	public void play(String title) {
		Room room = (Room) rooms.getSelectedItem();
		
		if (room == null || room.toString().equals("0 | " + owner.username + " | Local Room")) localVideo = title;
		
		player.play("uploads/client/" + owner.username + "/" + title);
	}
}
