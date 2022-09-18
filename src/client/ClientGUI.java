package client;

import shared.Data;
import shared.Room;

import javax.security.auth.login.LoginException;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
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
	private final Client owner;
	
	private final JTabbedPane tabbedPane = new JTabbedPane();
	
	private final JTextField textUsername = new JTextField(18);
	private final JTextField textPassword = new JPasswordField();
	private final JTextField textPasswordConfirm = new JPasswordField();
	private final JButton buttonLogin = new JButton("Login");
	
	private final JPanel notificationsPanel = new JPanel();
	private final JPanel videosPanel = new JPanel();
	
	final JLabel labelConnection = new JLabel();
	
	final Player player = new Player();
	
	final JComboBox<Room> rooms = new JComboBox<>();
	
	final JPanel usersPanel = new JPanel();
	
	private final JButton uploadButton = new JButton("Upload");
	private final JButton createRoom = new JButton("Create Room");
	
	private final JLabel labelError = new JLabel("", JLabel.CENTER);
	
	private JFileChooser fileChooser;
	
	Thread syncThread;
	Thread uploadThread;
	
	private final JPanel loginPanel = loginPanel();
	private final JPanel browsePanel = browsePanel();
	private final JPanel watchPanel = watchPanel();
	
	public ClientGUI(Client owner) {
		this.owner = owner;
		
		setTitle("KDP Project");
		setBounds(250, 250, 852, 480);
		setMinimumSize(new Dimension(852, 480));
		
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		
		setIconImage(Toolkit.getDefaultToolkit().getImage(getClass().getResource("/assets/client.png")));
		
		tabbedPane.add(loginPanel, "Login");
		add(tabbedPane, BorderLayout.CENTER);
		add(labelConnection, BorderLayout.PAGE_END);
		
		setVisible(true);
	}
	
	public void mainView() {
		tabbedPane.removeAll();
		
		tabbedPane.add(browsePanel, "Browse");
		tabbedPane.add(watchPanel, "Watch");
		
		Room localRoom = new Room("Local Room", owner.username, null);
		localRoom.setID(0);
		rooms.addItem(localRoom);
	}
	
	public void loginView() {
		setTitle("KDP Project");
		
		labelConnection.setText(" STATUS: Disconnected");
		labelConnection.setForeground(new Color(153, 0, 0));
		labelError.setText("Disconnected from network, register or sign in again");
		
		tabbedPane.removeAll();
		
		notificationsPanel.removeAll();
		rooms.removeAllItems();
		videosPanel.removeAll();
		usersPanel.removeAll();
		
		tabbedPane.add(loginPanel, "Login");
	}
	
	private JPanel loginPanel() {
		JPanel loginPanel = new JPanel(new GridBagLayout());
		loginPanel.setBorder(new EmptyBorder(4, 4, 4, 4));
		
		JPanel fieldsPanel = new JPanel(new GridLayout(0, 1));
		fieldsPanel.setBorder(new EmptyBorder(50, 90, 50, 90));
		
		labelError.setForeground(new Color(153, 0, 0));
		
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
				
				if (textPasswordConfirm.getText().isEmpty()) owner.loggedIn(textUsername.getText(), textPassword.getText(), false);
				else {
					if (!textPassword.getText().equals(textPasswordConfirm.getText())) throw new LoginException("Password confirmation incorrect!");
					owner.loggedIn(textUsername.getText(), textPassword.getText(), true);
				}
			} catch (LoginException e) {
				labelError.setText(e.getMessage());
			} catch (RemoteException e) {
				labelError.setText("Couldn't connect to the central server.");
			} catch (NotBoundException e) {
				e.printStackTrace();
			}
		});
		
		fieldsPanel.add(new JLabel("Username"));
		fieldsPanel.add(textUsername);
		fieldsPanel.add(new JLabel("Password"));
		fieldsPanel.add(textPassword);
		fieldsPanel.add(new JLabel("Confirm Password"));
		fieldsPanel.add(textPasswordConfirm);
		fieldsPanel.add(labelError);
		fieldsPanel.add(buttonLogin);
		
		loginPanel.add(fieldsPanel);
		
		return loginPanel;
	}
	
	private JPanel browsePanel() {
		notificationsPanel.setLayout(new BoxLayout(notificationsPanel, BoxLayout.PAGE_AXIS));
		
		JScrollPane videosScroll = new JScrollPane(videosPanel,
				JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		videosScroll.getVerticalScrollBar().setUnitIncrement(16);
		videosScroll.getHorizontalScrollBar().setUnitIncrement(16);
		
		JScrollPane notificationsScroll = new JScrollPane(notificationsPanel,
				JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		notificationsScroll.setBorder(BorderFactory.createTitledBorder("Notifications"));
		notificationsScroll.getVerticalScrollBar().setUnitIncrement(16);
		
		videosPanel.setLayout(new BoxLayout(videosPanel, BoxLayout.PAGE_AXIS));
		videosScroll.setBorder(BorderFactory.createTitledBorder("Videos"));
		
		JPanel uploadPanel = new JPanel(new BorderLayout());
		uploadPanel.setBorder(BorderFactory.createTitledBorder("Upload"));
		
		LookAndFeel previousLF = UIManager.getLookAndFeel();
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			fileChooser = new JFileChooser();
			fileChooser.setFileFilter(new FileNameExtensionFilter("Video Files (.mp4, .mov, .avi, ...)", "mp4", "mov", "avi", "mkv", "wmv", "webm"));
			UIManager.setLookAndFeel(previousLF);
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
			e.printStackTrace();
		}
		
		JLabel fileLabel = new JLabel();
		JButton fileButton = new JButton("Choose File");
		
		fileButton.addActionListener((ae) -> {
			if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) fileLabel.setText(fileChooser.getSelectedFile().getAbsolutePath());
		});
		
		uploadButton.addActionListener((ae) -> {
			uploadThread = new Thread(() -> {
				try {
					File file = fileChooser.getSelectedFile();
					
					if (file == null) throw new LoginException("No video selected for upload");
					if (!owner.subserver.reserveVideo(file.getName(), owner.username)) throw new LoginException("Video '" + file.getName() + "' already exists");
					
					JTextArea notification = addNotification("Uploading video '" + file.getName() + "' 0%");
					
					try (InputStream is = new FileInputStream(file)) {
						int readBytes;
						byte[] b = new byte[1024 * 1024 * 16];
						
						long total = file.length();
						long uploaded = 0;
						while (!Thread.currentThread().isInterrupted() && (readBytes = is.read(b)) != -1) {
							owner.subserver.uploadVideoDataToCentral(file.getName(), new Data(b, readBytes), owner.username);
							uploaded += readBytes;
							
							notification.setText("Uploading video '" + file.getName() + "' " + (int) ((double) uploaded / total * 100) + "%");
							revalidate();
						}
						
						if (!Thread.currentThread().isInterrupted()) {
							owner.subserver.finalizeVideo(file.getName(), owner.username);
						}
					}
				} catch (LoginException e) {
					addNotification(e.getMessage());
				} catch (IOException | NullPointerException e) {
					addNotification("No connection to the server, upload cancelled");
				}
				
				uploadButton.setEnabled(true);
			});
			
			uploadButton.setEnabled(false);
			uploadThread.start();
		});
		
		JPanel buttonsPanel = new JPanel(new BorderLayout());
		
		buttonsPanel.add(fileButton, BorderLayout.LINE_START);
		buttonsPanel.add(uploadButton, BorderLayout.LINE_END);
		
		uploadPanel.add(fileLabel, BorderLayout.CENTER);
		uploadPanel.add(buttonsPanel, BorderLayout.LINE_END);
		
		JPanel mainPanel = new JPanel(new BorderLayout());
		
		mainPanel.add(uploadPanel, BorderLayout.PAGE_START);
		mainPanel.add(videosScroll, BorderLayout.CENTER);
		
		JPanel browsePanel = new JPanel(new BorderLayout());
		
		browsePanel.add(mainPanel, BorderLayout.CENTER);
		browsePanel.add(notificationsScroll, BorderLayout.LINE_END);
		
		return browsePanel;
	}
	
	private JPanel watchPanel() {
		rooms.addItemListener(e -> {
			if (e.getStateChange() != ItemEvent.SELECTED || tabbedPane.getSelectedIndex() != 1) return;
			
			Room room = (Room) e.getItem();
			
			player.setControls(false);
			
			if (syncThread != null) {
				syncThread.interrupt();
				try {
					syncThread.join();
				} catch (InterruptedException ex) {
					ex.printStackTrace();
				}
			}
			
			if (room.toString().equals("0 | " + owner.username + " | Local Room")) {
				createRoom.setEnabled(true);
				player.setControls(true);
				return;
			}
			
			createRoom.setEnabled(false);
			
			player.play("uploads/client/" + owner.username + "/" + room.video);
			player.pause(true);
			
			if (room.owner.equals(owner.username)) syncThread = new Thread(() -> {
				try {
					while (!Thread.currentThread().isInterrupted()) {
						try {
							Room finalRoom = owner.subserver.getRoomData(room.getID());
							player.pause(finalRoom.getPaused());
							player.seek(finalRoom.getTime());
							
							break;
						} catch (RemoteException | LoginException e1) {
							addNotification("Couldn't get room, no connection to the server, retrying");
							Thread.sleep(1500);
						}
					}
					
					player.setControls(true);
					
					while (!Thread.currentThread().isInterrupted()) {
						try {
							owner.subserver.setRoomData(room.getID(), player.progress.getValue(), player.getPaused());
						} catch (RemoteException | LoginException e1) {
							addNotification("Couldn't update room, no connection to the server, retrying");
							Thread.sleep(1000);
						}
						
						Thread.sleep(250);
					}
				} catch (InterruptedException | ArrayIndexOutOfBoundsException ignored) {
				}
			});
			else syncThread = new Thread(() -> {
				try {
					while (!Thread.currentThread().isInterrupted()) {
						try {
							Room finalRoom = owner.subserver.getRoomData(room.getID());
							player.pause(finalRoom.getPaused());
							player.seek(finalRoom.getTime());
							
							break;
						} catch (RemoteException | LoginException e1) {
							addNotification("Couldn't get room, no connection to the server, retrying");
							Thread.sleep(1000);
						}
					}
					
					while (!Thread.currentThread().isInterrupted()) {
						try {
							Room temp = owner.subserver.getRoomData(room.getID());
							
							System.out.println(temp.getTime() + " - " + player.getTime() + " = " + Math.abs(temp.getTime() - player.getTime()));
							if (temp.getPaused()) {
								player.pause(true);
								if (temp.getTime() != player.getTime()) player.seek(temp.getTime());
							} else {
								if (player.getPaused() || Math.abs(temp.getTime() - player.getTime()) > 1000) player.seek(temp.getTime());
								player.pause(false);
							}
							
							Thread.sleep(250);
						} catch (RemoteException | LoginException e1) {
							player.pause(true);
							addNotification("Couldn't refresh room, no connection to the server, retrying");
							Thread.sleep(1000);
						}
					}
				} catch (InterruptedException | ArrayIndexOutOfBoundsException ignored) {
				}
			});
			
			syncThread.start();
		});
		
		createRoom.addActionListener((ae) -> {
			if (player.getVideo() == null) return;
			
			ArrayList<String> viewers = new ArrayList<>();
			
			for (Component user : usersPanel.getComponents())
				if (user instanceof JCheckBox) {
					JCheckBox checkBox = (JCheckBox) user;
					
					if (checkBox.isSelected()) {
						viewers.add(checkBox.getText());
						checkBox.setSelected(false);
					}
				}
			
			if (viewers.isEmpty()) return;
			
			viewers.add(owner.username);
			
			try {
				owner.subserver.createRoom(new Room(player.getVideo(), owner.username, viewers));
			} catch (RemoteException e) {
				addNotification("Couldn't create room, no connection to the server");
			} catch (LoginException e) {
				addNotification(e.getMessage());
			}
		});
		
		JPanel roomPanel = new JPanel(new BorderLayout());
		usersPanel.setLayout(new BoxLayout(usersPanel, BoxLayout.PAGE_AXIS));
		
		JScrollPane usersScroll = new JScrollPane(usersPanel,
				JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		usersScroll.getVerticalScrollBar().setUnitIncrement(16);
		
		roomPanel.add(rooms, BorderLayout.PAGE_START);
		roomPanel.add(usersScroll, BorderLayout.CENTER);
		roomPanel.add(createRoom, BorderLayout.PAGE_END);
		
		JPanel watchPanel = new JPanel(new BorderLayout());
		
		watchPanel.add(player, BorderLayout.CENTER);
		watchPanel.add(roomPanel, BorderLayout.LINE_END);
		
		return watchPanel;
	}
	
	public void addRoom(Room room) {
		rooms.addItem(room);
		
		if (!owner.username.equals(room.owner)) addNotification("User '" + room.owner + "' has added you to their room playing '" + room.video + "'");
		else rooms.setSelectedItem(room);
	}
	
	public JTextArea addNotification(String text) {
		JPanel notification = new JPanel(new BorderLayout());
		
		JTextArea notificationText = new JTextArea(0, 23);
		
		notificationText.setText(text);
		notificationText.setWrapStyleWord(true);
		notificationText.setLineWrap(true);
		notificationText.setOpaque(true);
		notificationText.setEditable(false);
		notificationText.setFocusable(false);
		notificationText.setBackground(new Color(214, 217, 223));
		notificationText.setFont(UIManager.getFont("Label.font"));
		notificationText.setBorder(UIManager.getBorder("Label.border"));
		
		JTextArea dateText = new JTextArea(new SimpleDateFormat("   dd/MM/yyyy HH:mm:ss   ").format(new Date()));
		dateText.setOpaque(true);
		dateText.setEditable(false);
		dateText.setFocusable(false);
		dateText.setBackground(new Color(214, 217, 223));
		dateText.setFont(UIManager.getFont("Label.font"));
		dateText.setBorder(UIManager.getBorder("Label.border"));
		
		notification.add(notificationText, BorderLayout.CENTER);
		notification.add(dateText, BorderLayout.LINE_END);
		
		dateText.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
		notificationText.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
		notification.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
		
		notificationsPanel.add(notification, 0);
		revalidate();
		
		return notificationText;
	}
	
	public void addVideo(String title) {
		JPanel video = new JPanel();
		
		video.setMaximumSize(new Dimension(100, 100));
		
		video.setBorder(BorderFactory.createLineBorder(new Color(214, 217, 223)));
		video.setBackground(new Color(172, 0, 0));
		
		JLabel titleLabel = new JLabel(title);
		titleLabel.setForeground(Color.white);
		titleLabel.setFont(new Font("Arial", Font.BOLD, 10));
		
		video.add(titleLabel);
		
		video.setMaximumSize(new Dimension(Integer.MAX_VALUE, video.getMinimumSize().height));
		
		video.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				if (syncThread != null) syncThread.interrupt();
				createRoom.setEnabled(true);
				player.setControls(true);
				rooms.setSelectedIndex(0);
				tabbedPane.setSelectedIndex(1);
				
				player.play("uploads/client/" + owner.username + "/" + title);
			}
		});
		
		videosPanel.add(video, 0);
		revalidate();
	}
	
	public void addUser(String username) {
		JCheckBox user = new JCheckBox(username);
		user.setMaximumSize(new Dimension(Integer.MAX_VALUE, user.getMinimumSize().height));
		
		usersPanel.add(user);
		revalidate();
	}
}
