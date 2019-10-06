/*
MIT License

Copyright (c) 2019 Zhiyu Zhu/朱智语/ApolloZhu <zhuzhiyu@uw.edu>

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.*;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * A music player that utilizes Guitar37.
 * <p>
 * THIS IS NOT A GOOD EXAMPLE OF STYLE FOR YOUR OWN ASSIGNMENTS,
 * PLUS IT USES FORBIDDEN AND/OR DEPRECATED FEATURES.
 * <p>
 * For feature requests/bug reports/suggestions, contact:
 *
 * @author Apollo | Zhiyu Zhu | zhuzhiyu@uw.edu
 * <p>
 * or submit an issue at project home page on GitHub:
 * <p>
 * https://github.com/ApolloZhu/GuitarTunePlayer
 */
public class GuitarTunePlayer {
    public static final String VERSION = "0.0.1";

    public static void main(String[] args) {
        GuitarTunePlayerController player = new GuitarTunePlayerController(selectSongs());
        JFrame frame = new JFrame("Guitar Tune Player v" + VERSION);
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        frame.setSize(300, 500);
        frame.setLocation(screenSize.width - frame.getWidth() - 50, 50);
        frame.setContentPane(player.getPanel());
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setAlwaysOnTop(true);
        frame.setVisible(true);
    }

    private static File[] selectSongs() {
        File folder = getFolder();
        File[] songs = folder.listFiles();
        if (songs == null || songs.length <= 0) {
            UserDefaults.clear();
            return selectSongs();
        } else {
            UserDefaults.setSelectedFolder(folder.getPath());
            return songs;
        }
    }

    private static File getFolder() {
        String path = UserDefaults.getSelectedFolder();
        if (path.length() > 0) {
            return new File(path);
        } else {
            return chooseFolder();
        }
    }

    private static File chooseFolder() {
        JFileChooser chooser = new JFileChooser(new File("."));
        chooser.setDialogTitle("Choose the FOLDER containing all the tunes");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
            return chooseFolder();
        }
        return chooser.getSelectedFile();
    }
}

class GuitarTunePlayerController implements GuitarPlayerUpdateHandler, GuitarTunePlayerPanelDelegate {
    public enum LoopMode {
        SINGLE, NEXT, SHUFFLE
    }

    private static final Comparator<File> CASE_INSENSITIVE_ORDER = new Comparator<File>() {
        public int compare(File o1, File o2) {
            return String.CASE_INSENSITIVE_ORDER.compare(o1.getName(), o2.getName());
        }
    };

    // State
    private File[] songs;
    private int currentIndex;
    private boolean isPlaying;
    private boolean isPaused;
    private LoopMode loopMode;
    // Helpers
    private Thread playerThread;
    private GuitarPlayer guitarPlayer;
    private GuitarTunePlayerPanel panel;

    public GuitarTunePlayerController(File[] unorderedSongs) {
        setSongs(unorderedSongs);
        panel = new GuitarTunePlayerPanel(songs, this);
        guitarPlayer = new GuitarPlayer(this);
        setLoopMode(UserDefaults.getLoopMode());
        setIsPlaying(false);
        playLastPlayedSong();
    }

    public GuitarTunePlayerPanel getPanel() {
        return panel;
    }

    private void setSongs(File[] unorderedSongs) {
        List<File> names = Arrays.asList(unorderedSongs);
        Collections.sort(names, CASE_INSENSITIVE_ORDER);
        songs = names.toArray(new File[]{});
    }

    private void playLastPlayedSong() {
        String lastPlayed = UserDefaults.getLastPlayedFilePath();
        if (lastPlayed.length() > 0) {
            File file = new File(lastPlayed);
            int index = Arrays.binarySearch(songs, file, CASE_INSENSITIVE_ORDER);
            play((index == -1) ? 0 : index);
        } else {
            play(0);
        }
    }

    public void cycleLoopMode() {
        switch (loopMode) {
            case SINGLE:
                setLoopMode(LoopMode.NEXT);
                break;
            case NEXT:
                setLoopMode(LoopMode.SHUFFLE);
                break;
            case SHUFFLE:
                setLoopMode(LoopMode.SINGLE);
                break;
        }
    }

    private void setLoopMode(LoopMode loopMode) {
        this.loopMode = loopMode;
        UserDefaults.setLoopMode(loopMode);
        switch (loopMode) {
            case SINGLE:
                panel.setLoopMode("Repeat");
                break;
            case NEXT:
                panel.setLoopMode("Loop");
                break;
            case SHUFFLE:
                panel.setLoopMode("Shuffle");
                break;
        }
    }

    public void togglePlayingMode() {
        if (isPlaying) {
            pause();
        } else {
            start();
        }
    }

    private void start() {
        if (isPlaying) {
            return;
        }
        if (isPaused) {
            resume();
        } else {
            play(currentIndex);
        }
    }

    public void next() {
        switch (loopMode) {
            case SINGLE:
                play(currentIndex);
                break;
            case NEXT:
                play((currentIndex + 1) % songs.length);
                break;
            case SHUFFLE:
                play((int) (Math.random() * songs.length));
                break;
        }
    }

    private void pause() {
        isPaused = true;
        setIsPlaying(false);
        playerThread.suspend();
    }

    private void resume() {
        setIsPlaying(true);
        playerThread.resume();
    }

    private void stopPlayer() {
        if (playerThread == null) {
            return;
        }
        guitarPlayer.stop();
        if (isPaused) {
            playerThread.resume();
        }
        try {
            playerThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void listDidSelectIndex(JList<File> list, int index) {
        play(index);
    }

    private void play(int index) {
        if (panel.listSelectIndex(index)) {
            return; // recurse by selection listener
        }
        stopPlayer();
        currentIndex = index;
        _play(songs[currentIndex]);
    }

    private void _play(final File song) {
        panel.setCurrentTitle(Formatter.songNameFrom(song));
        UserDefaults.setLastPlayedFilePath(song.getPath());

        playerThread = new Thread(new Runnable() {
            public void run() {
                try {
                    guitarPlayer.play(new Scanner(song));
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }
        });

        setCurrentTime(0);
        setIsPlaying(true);
        playerThread.start();
    }

    private void setIsPlaying(boolean isPlaying) {
        this.isPlaying = isPlaying;
        if (isPlaying) {
            isPaused = false;
        }
        panel.setPlayPauseButtonTitle(isPlaying ? "Pause" : "Play");
    }

    public void setCurrentTime(double seconds) {
        panel.setCurrentTime(seconds);
    }

    public void setTotalTime(double seconds) {
        panel.setTotalTime(seconds);
    }
}

// MARK: - User Interface

interface GuitarTunePlayerPanelDelegate {
    void listDidSelectIndex(JList<File> list, int index);

    void cycleLoopMode();

    void togglePlayingMode();

    void next();
}

class GuitarTunePlayerPanel extends JPanel {
    private JLabel currentSongTitle;
    private JButton loopModeButton;
    private JButton playPauseButton;
    private JLabel currentTime, totalTime;
    private JProgressBar progress;
    private JList<File> list;
    private double current, total;
    private GuitarTunePlayerPanelDelegate delegate;

    GuitarTunePlayerPanel(File[] songs, GuitarTunePlayerPanelDelegate delegate) {
        this.delegate = delegate;
        setLayout(new BorderLayout());
        makeControls();
        makePlaylistUI(songs);
    }

    private void makePlaylistUI(File[] songs) {
        list = new JList<>(songs);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    delegate.listDidSelectIndex(list, list.getSelectedIndex());
                }
            }
        });
        list.setCellRenderer(new ListCellRenderer<File>() {
            public Component getListCellRendererComponent(
                    JList<? extends File> list, File value,
                    int index, boolean isSelected, boolean cellHasFocus
            ) {
                JLabel label = new JLabel(Formatter.songNameFrom(value));
                label.setFont(label.getFont().deriveFont(18f));
                label.setBorder(new EmptyBorder(0, 10, 0, 10));
                label.setOpaque(true);
                if (isSelected) {
                    label.setBackground(list.getSelectionBackground());
                    label.setForeground(list.getSelectionForeground());
                } else {
                    label.setBackground(list.getBackground());
                    label.setForeground(list.getForeground());
                }
                return label;
            }
        });
        JScrollPane listScroller = new JScrollPane(list);
        add(listScroller, BorderLayout.CENTER);
    }

    private void makeControls() {
        JPanel controls = new JPanel();
        add(controls, BorderLayout.NORTH);
        controls.setLayout(new GridLayout(3, 1));

        makeTitle(controls);
        makeButtons(controls);
        makeProgressBar(controls);
    }

    private void makeTitle(JPanel controls) {
        currentSongTitle = new JLabel();
        controls.add(currentSongTitle, BorderLayout.NORTH);
        currentSongTitle.setBorder(new EmptyBorder(0, 10, 0, 10));
        currentSongTitle.setFont(currentSongTitle.getFont().deriveFont(24f));
    }

    private void makeButtons(JPanel controls) {
        JPanel buttons = new JPanel();
        controls.add(buttons);
        buttons.setLayout(new GridLayout(1, 3));

        loopModeButton = new JButton();
        buttons.add(loopModeButton);
        loopModeButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                delegate.cycleLoopMode();
            }
        });
        playPauseButton = new JButton();
        buttons.add(playPauseButton);
        playPauseButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                delegate.togglePlayingMode();
            }
        });
        JButton nextButton = new JButton("Next");
        buttons.add(nextButton);
        nextButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                delegate.next();
            }
        });
    }

    private void makeProgressBar(JPanel controls) {
        JPanel progressBar = new JPanel();
        controls.add(progressBar);
        progressBar.setLayout(new BorderLayout());
        currentTime = new JLabel(Formatter.timeIntervalFrom(0));
        progressBar.add(currentTime, BorderLayout.WEST);
        currentTime.setBorder(new EmptyBorder(0, 10, 0, 10));
        progress = new JProgressBar();
        progressBar.add(progress, BorderLayout.CENTER);
        totalTime = new JLabel();
        progressBar.add(totalTime, BorderLayout.EAST);
        totalTime.setBorder(new EmptyBorder(0, 10, 0, 10));
    }

    public void setLoopMode(String text) {
        loopModeButton.setText(text);
    }

    /**
     * @param index to select.
     * @return if list did change to select the index.
     */
    public boolean listSelectIndex(int index) {
        if (list.getSelectedIndex() == index) {
            return false;
        }
        list.setSelectedIndex(index);
        list.ensureIndexIsVisible(index);
        return true;
    }

    public void setPlayPauseButtonTitle(String title) {
        playPauseButton.setText(title);
    }

    public void setCurrentTitle(String title) {
        currentSongTitle.setText(title);
    }

    public void setCurrentTime(double seconds) {
        current = seconds;
        updatePercentage();
        currentTime.setText(Formatter.timeIntervalFrom(seconds));
    }

    public void setTotalTime(double seconds) {
        total = seconds;
        progress.setMaximum((int) Math.round(total * 60));
        updatePercentage();
        totalTime.setText(Formatter.timeIntervalFrom(seconds));
    }

    private void updatePercentage() {
        int newProgress = (int) (current / total * progress.getMaximum());
        progress.setValue(newProgress);
    }
}

// MARK: - Utilities

interface GuitarPlayerUpdateHandler {
    void next();

    void setTotalTime(double seconds);

    void setCurrentTime(double seconds);
}

/**
 * This class is adapted from "PlayThatTune.java"
 */
class GuitarPlayer {
    private final Guitar37 guitar = new Guitar37();

    private List<Segment> segments;
    private double currentTime;
    private boolean shouldTerminate;
    private GuitarPlayerUpdateHandler delegate;

    GuitarPlayer(GuitarPlayerUpdateHandler delegate) {
        this.delegate = delegate;
    }

    private static class Segment {
        int pitch;
        double duration;

        Segment(int pitch, double duration) {
            this.pitch = pitch;
            this.duration = duration;
        }
    }

    public void stop() {
        shouldTerminate = true;
    }

    public void play(Scanner input) {
        shouldTerminate = false;
        loadSegments(input);
        playSegments();
        if (shouldTerminate) {
            return;
        }
        // Invoke later to terminate current thread
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                delegate.next();
            }
        });
    }

    private void loadSegments(Scanner input) {
        currentTime = 0;
        segments = new ArrayList<>();

        double totalDuration = 0;
        while (input.hasNextInt()) {
            if (shouldTerminate) {
                return;
            }
            int pitch = input.nextInt();
            double duration = input.nextDouble();
            totalDuration += duration;
            segments.add(new Segment(pitch, duration));
        }
        delegate.setTotalTime(totalDuration);
    }

    private void playSegments() {
        for (Segment segment : segments) {
            if (shouldTerminate) {
                return;
            }
            guitar.playNote(segment.pitch);
            advance(segment.duration);
            currentTime += segment.duration;
            delegate.setCurrentTime(currentTime);
        }
    }

    private void advance(double duration) {
        int tics = (int) Math.round(duration * StdAudio.SAMPLE_RATE);
        for (int i = 0; i < tics; i++) {
            if (shouldTerminate) {
                return;
            }
            StdAudio.play(guitar.sample());
            guitar.tic();
            delegate.setCurrentTime(currentTime + (double) i / tics * duration);
        }
    }
}

enum Formatter {
    ;

    static String timeIntervalFrom(double second) {
        int rounded = (int) (Math.round(second));
        int minutes = rounded / 60;
        int seconds = rounded % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    static String songNameFrom(File file) {
        String name = file.getName();
        name = name.replace(".txt", "");
        name = name.replace("_", " ");
        name = name.replace("-", " ");
        name = splitCamelCase(name);
        name = name.replaceAll("\\s+", " ");
        return name;
    }

    // Slightly modified from this stack overflow answer
    // https://stackoverflow.com/a/2560017
    // to handle "Kiki's Delivery Service Theme Song".
    private static String splitCamelCase(String s) {
        return s.replaceAll(
                String.format("%s|%s|%s",
                        "(?<=[A-Z])(?=[A-Z][a-z'])",
                        "(?<=[^A-Z])(?=[A-Z])",
                        "(?<=[A-Za-z'])(?=[^A-Za-z'])"
                ),
                " "
        );
    }
}

enum UserDefaults {
    ;
    private static final Preferences PREFERENCES
            = Preferences.userNodeForPackage(GuitarTunePlayer.class);

    public static void clear() {
        try {
            PREFERENCES.clear();
        } catch (BackingStoreException e) {
            e.printStackTrace();
        }
    }

    public static String getSelectedFolder() {
        return PREFERENCES.get("FOLDER", "");
    }

    public static void setSelectedFolder(String folder) {
        PREFERENCES.put("FOLDER", folder);
    }

    public static String getLastPlayedFilePath() {
        return PREFERENCES.get("FILE", "");
    }

    public static void setLastPlayedFilePath(String filePath) {
        PREFERENCES.put("FILE", filePath);
    }

    public static GuitarTunePlayerController.LoopMode getLoopMode() {
        return GuitarTunePlayerController.LoopMode
                .valueOf(PREFERENCES.get("LOOP_MODE", "NEXT"));
    }

    public static void setLoopMode(GuitarTunePlayerController.LoopMode loopMode) {
        PREFERENCES.put("LOOP_MODE", loopMode.name());
    }
}
