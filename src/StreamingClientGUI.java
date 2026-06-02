// StreamingClientGUI.java
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class StreamingClientGUI extends JFrame {
    private static final String SERVER_HOST = "localhost";
    private static final int CONTROL_PORT = 8080;
    private static final Logger logger = Logger.getLogger("StreamingClientGUI");
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private List<String> videoList;
    private JList<String> videoJList;
    private DefaultListModel<String> listModel;
    private JButton streamButton;
    private JButton exitButton;
    private JLabel statusLabel;
    private Process currentFfplayProcess;
    
    public StreamingClientGUI() {
        try {
            FileHandler fh = new FileHandler("client.log");
            fh.setFormatter(new SimpleFormatter());
            logger.addHandler(fh);
        } catch (Exception e) {
            System.err.println("Could not create log file: " + e.getMessage());
        }
        setTitle("Streaming Client");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(500, 400);
        setLocationRelativeTo(null);
        listModel = new DefaultListModel<>();
        videoJList = new JList<>(listModel);
        videoJList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scrollPane = new JScrollPane(videoJList);
        streamButton = new JButton("Stream");
        streamButton.setEnabled(false);
        exitButton = new JButton("Exit");
        statusLabel = new JLabel("Not connected", SwingConstants.CENTER);
        statusLabel.setOpaque(true);
        statusLabel.setBackground(Color.LIGHT_GRAY);
        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(streamButton);
        buttonPanel.add(exitButton);
        add(scrollPane, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
        add(statusLabel, BorderLayout.NORTH);
        streamButton.addActionListener(e -> streamSelectedVideo());
        exitButton.addActionListener(e -> exitApplication());
        connectToServer();
        setVisible(true);
    }
    
    private void connectToServer() {
        try {
            statusLabel.setText("Connecting to server...");
            statusLabel.setBackground(Color.YELLOW);
            socket = new Socket(SERVER_HOST, CONTROL_PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            int videoCount = Integer.parseInt(in.readLine());
            videoList = new ArrayList<>();
            listModel.clear();
            for (int i = 0; i < videoCount; i++) {
                String video = in.readLine();
                videoList.add(video);
                listModel.addElement(video);
            }
            statusLabel.setText("Connected. Select a video and click Stream.");
            statusLabel.setBackground(Color.GREEN);
            streamButton.setEnabled(true);
            logger.info("Connected to server, received " + videoCount + " videos");
        } catch (IOException e) {
            statusLabel.setText("Connection failed: " + e.getMessage());
            statusLabel.setBackground(Color.RED);
            logger.severe("Connection failed: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Cannot connect to server: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void streamSelectedVideo() {
        String selected = videoJList.getSelectedValue();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "Please select a video first", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int index = videoJList.getSelectedIndex();
        int choice = index + 1;
        out.println(choice);
        try {
            String response = in.readLine();
            if (response == null || !response.startsWith("SELECTED:")) {
                statusLabel.setText("Error: " + response);
                return;
            }
            String streamMsg = in.readLine();
            if (streamMsg.startsWith("STREAM_READY:")) {
                int streamPort = Integer.parseInt(streamMsg.substring(13));
                statusLabel.setText("Playing: " + selected);
                statusLabel.setBackground(Color.CYAN);
                logger.info("Playing: " + selected);
                playStream(streamPort);
                statusLabel.setText("Finished. Select another video.");
                statusLabel.setBackground(Color.GREEN);
            } else if (streamMsg.startsWith("STREAM_ERROR:")) {
                statusLabel.setText("Stream error: " + streamMsg);
                statusLabel.setBackground(Color.RED);
                logger.warning("Stream error: " + streamMsg);
            }
        } catch (IOException e) {
            statusLabel.setText("Error: " + e.getMessage());
            statusLabel.setBackground(Color.RED);
            logger.severe("Stream error: " + e.getMessage());
        }
    }
    
    private void playStream(int streamPort) {
        String ffplayPath = "ffmpeg/bin/ffplay.exe";
        String streamUrl = "tcp://127.0.0.1:" + streamPort;
        List<String> command = new ArrayList<>();
        command.add(ffplayPath);
        command.add("-i");
        command.add(streamUrl);
        command.add("-x");
        command.add("640");
        command.add("-y");
        command.add("360");
        command.add("-noborder");
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            currentFfplayProcess = pb.start();
            currentFfplayProcess.waitFor();
        } catch (Exception e) {
            System.err.println("Error playing stream: " + e.getMessage());
            logger.warning("Error playing stream: " + e.getMessage());
        } finally {
            currentFfplayProcess = null;
        }
    }
    
    private void exitApplication() {
        try {
            if (out != null) {
                out.println(0);
            }
            if (socket != null) {
                socket.close();
            }
            if (currentFfplayProcess != null) {
                currentFfplayProcess.destroyForcibly();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        logger.info("Client exiting");
        System.exit(0);
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new StreamingClientGUI());
    }
}