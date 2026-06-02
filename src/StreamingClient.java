import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class StreamingClient {
    
    private static final String SERVER_HOST = "localhost";
    private static final int CONTROL_PORT = 8080;
    private static Process currentFfplayProcess = null;
    
    public static void main(String[] args) {
        System.out.println("Streaming Client Starting ");
        
        try (Socket socket = new Socket(SERVER_HOST, CONTROL_PORT)) {
            System.out.println("Connected to server at " + SERVER_HOST + ":" + CONTROL_PORT);
            
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            Scanner scanner = new Scanner(System.in);
            
            boolean running = true;
            while (running) {
                // Διάβασε τον αριθμό των βίντεο
                int videoCount = Integer.parseInt(in.readLine());
                System.out.println("\n Available Videos (" + videoCount + " total) ");
                
                // Διάβασε και αποθήκευσε τη λίστα
                List<String> videoList = new ArrayList<>();
                for (int i = 0; i < videoCount; i++) {
                    String video = in.readLine();
                    videoList.add(video);
                    System.out.println("  " + (i + 1) + ". " + video);
                }
                
                // Επιλογή από τον χρήστη
                System.out.println("\nEnter the number of the video to stream (or 0 to exit):");
                System.out.print("> ");
                int choice = scanner.nextInt();
                
                // Στείλε την επιλογή στον server
                out.println(choice);
                
                if (choice == 0) {
                    String response = in.readLine();
                    System.out.println("Server: " + response);
                    running = false;
                    break;
                }
                
                if (choice >= 1 && choice <= videoList.size()) {
                    // Διάβασε την απάντηση SELECTED:
                    String response = in.readLine();
                    System.out.println("Server: " + response);
                    
                    // Περίμενε το μήνυμα για streaming
                    String streamMsg = in.readLine();
                    System.out.println("Stream message: " + streamMsg);
                    
                    if (streamMsg.startsWith("STREAM_READY:")) {
                        int streamPort = Integer.parseInt(streamMsg.substring(13));
                        System.out.println("\nStarting video playback on port " + streamPort + "...");
                        System.out.println("The video will play. Press Enter to stop and return to menu...");
                        
                        // Ξεκίνα το FFplay για να παίξει το βίντεο
                        playStream(streamPort);

                        // Περίμενε Enter
                        scanner.nextLine();
                        scanner.nextLine();

                        // Τερμάτισε το ffplay
                        System.out.println("Stopping video playback...");
                        if (currentFfplayProcess != null) {
                            currentFfplayProcess.destroyForcibly();
                            currentFfplayProcess = null;
                        }

                        System.out.println("Returning to menu...\n");
                        try {
                            Runtime.getRuntime().exec("taskkill /F /IM ffplay.exe");
                        } catch (Exception e) {
                            // στο WSL μπορεί να μη δουλέψει
                            System.out.println("  (Press Ctrl+C in ffplay window to close)");
                        }
                        
                        System.out.println("\nStopping video playback. Returning to menu...");
                    } else if (streamMsg.startsWith("STREAM_ERROR:")) {
                        System.err.println("Streaming error: " + streamMsg);
                    }
                } else {
                    System.out.println("Invalid choice. Please try again.");
                    // Διάβασε το μήνυμα λάθους από τον server
                    String errorMsg = in.readLine();
                    System.out.println("Server: " + errorMsg);
                }
            }
            
        } catch (IOException e) {
            System.err.println("Client error: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("\n Client finished ");
    }
    
    static void playStream(int streamPort) {
        String ffplayPath = "/mnt/c/Users/itsdr/OneDrive/Desktop/Multimedia-Lab-Project-2025-2026/ffmpeg/bin/ffplay.exe";
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
            currentFfplayProcess = pb.start();  // Αποθήκευση στη static μεταβλητή
        } catch (Exception e) {
            System.err.println("Error playing stream: " + e.getMessage());
        }
    }
}