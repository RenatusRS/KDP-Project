package client;

import com.sun.jna.NativeLibrary;
import uk.co.caprica.vlcj.binding.RuntimeUtil;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

public class Player extends JPanel {
	static {
		NativeLibrary.addSearchPath(RuntimeUtil.getLibVlcLibraryName(), "./lib");
	}
	
	private final EmbeddedMediaPlayerComponent emp = new EmbeddedMediaPlayerComponent();
	final JSlider progress = new JSlider(JSlider.HORIZONTAL);
	
	private boolean oldPause = false;
	private boolean moving = false;
	private boolean controls = true;
	
	private final JLabel currentTime = new JLabel("  00:00   ");
	private final JLabel totalTime = new JLabel("   00:00  ");
	
	private long time = 0;
	
	private String video = null;
	
	private void moveSlider(MouseEvent e) {
		progress.setValue((int) Math.round(((double) progress.getMaximum()) * (long) e.getPoint().x / ((long) progress.getWidth())));
		
		alignTime(progress.getValue());
	}
	
	private void alignVideo() {
		emp.mediaPlayer().controls().setTime(progress.getValue());
	}
	
	private void alignSlider() {
		progress.setValue((int) (emp.mediaPlayer().status().time()));
		alignTime(emp.mediaPlayer().status().time());
	}
	
	private void alignTime(long millis) {
		currentTime.setText(String.format("  %02d:%02d   ", TimeUnit.MILLISECONDS.toMinutes(millis),
				TimeUnit.MILLISECONDS.toSeconds(millis)
						- TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis))));
	}
	
	public Player() {
		progress.setValue(0);
		
		emp.mediaPlayer().controls().setRepeat(true);
		
		emp.mediaPlayer().input().enableMouseInputHandling(false);
		emp.videoSurfaceComponent().addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (!controls) return;
				
				oldPause = emp.mediaPlayer().status().isPlaying();
				pause();
			}
		});
		
		emp.mediaPlayer().events().addMediaPlayerEventListener(new MediaPlayerEventAdapter() {
			@Override
			public void timeChanged(MediaPlayer mediaPlayer, long newTime) {
				alignSlider();
			}
			
			@Override
			public void mediaPlayerReady(MediaPlayer mediaPlayer) {
				time = emp.mediaPlayer().status().length();
				
				progress.setMaximum((int) emp.mediaPlayer().status().length());
				
				totalTime.setText(String.format("   %02d:%02d  ", TimeUnit.MILLISECONDS.toMinutes(time),
						TimeUnit.MILLISECONDS.toSeconds(time)
								- TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(time))));
			}
		});
		
		progress.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (!controls) return;
				
				moving = true;
				pause(true);
				moveSlider(e);
			}
			
			@Override
			public void mouseReleased(MouseEvent e) {
				if (!controls) return;
				
				pause(oldPause);
				moving = false;
				alignVideo();
			}
		});
		
		progress.addMouseMotionListener(new MouseAdapter() {
			@Override
			public void mouseDragged(MouseEvent e) {
				if (!controls) return;
				
				moveSlider(e);
			}
		});
		
		JPanel progressPanel = new JPanel(new BorderLayout());
		progressPanel.add(currentTime, BorderLayout.LINE_START);
		progressPanel.add(progress, BorderLayout.CENTER);
		progressPanel.add(totalTime, BorderLayout.LINE_END);
		
		setLayout(new BorderLayout());
		add(emp, BorderLayout.CENTER);
		add(progressPanel, BorderLayout.PAGE_END);
	}
	
	public void play(String path) {
		emp.mediaPlayer().media().play(path);
		video = Paths.get(path).getFileName().toString();
	}
	
	public void pause() {
		if (!moving) alignSlider();
		
		emp.mediaPlayer().controls().pause();
	}
	
	public void pause(boolean state) {
		if (!moving) alignSlider();
		
		emp.mediaPlayer().controls().setPause(state);
	}
	
	public void setControls(boolean state) {
		progress.setEnabled(state);
		this.controls = state;
	}
	
	public void seek(long millis) {
		emp.mediaPlayer().controls().setTime(millis);
		alignSlider();
	}
	
	public String getVideo() {
		return video;
	}
	
	public long getTime() {
		return emp.mediaPlayer().status().time();
	}
	
	public boolean getPaused() {
		return !emp.mediaPlayer().status().isPlaying();
	}
}