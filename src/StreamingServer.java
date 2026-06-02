// StreamingServer.java
import ws.schild.jave.AudioAttributes;
import ws.schild.jave.EncodingAttributes;
import ws.schild.jave.Encoder;
import ws.schild.jave.EncoderException;
import ws.schild.jave.InputFormatException;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.VideoAttributes;
import ws.schild.jave.VideoSize;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class StreamingServer {
    static final String[] SUPPORTED_FORMATS = {"avi", "mp4", "mkv"};
    static final String[] SUPPORTED_RESOLUTIONS = {"240p", "360p", "480p", "720p", "1080p"};
    static final int CONTROL_PORT = 8080;
    static final int STREAM_PORT = 8081;
    static List<String> availableVideos = new ArrayList<>();
    private static final Logger logger = Logger.getLogger("StreamingServer");
    
    static class VideoFile {
        String movieName;
        String resolution;
        String format;
        String fullPath;
        VideoFile(String movieName, String resolution, String format, String fullPath) {
            this.movieName = movieName;
            this.resolution = resolution;
            this.format = format;
            this.fullPath = fullPath;
        }
        public String toString() {
            return movieName + "-" + resolution + "." + format;
        }
    }
    
    public static void main(String[] args) {
        try {
            FileHandler fh = new FileHandler("server.log");
            fh.setFormatter(new SimpleFormatter());
            logger.addHandler(fh);
        } catch (Exception e) {
            System.err.println("Could not create log file: " + e.getMessage());
        }
        logger.info("Streaming Server Starting");
        System.out.println("Streaming Server Starting");
        String videosFolderPath = "videos";
        File videosFolder = new File(videosFolderPath);
        if (!videosFolder.exists() || !videosFolder.isDirectory()) {
            System.err.println("ERROR: Videos folder not found: " + videosFolderPath);
            logger.severe("Videos folder not found: " + videosFolderPath);
            return;
        }
        List<VideoFile> existingFiles = new ArrayList<>();
        File[] files = videosFolder.listFiles();
        if (files == null) {
            System.err.println("ERROR: Cannot read videos folder");
            logger.severe("Cannot read videos folder");
            return;
        }
        System.out.println("Existing files in videos folder:");
        for (File file : files) {
            if (file.isFile()) {
                String fileName = file.getName();
                String format = "";
                int lastDot = fileName.lastIndexOf('.');
                if (lastDot > 0) {
                    format = fileName.substring(lastDot + 1).toLowerCase();
                }
                String nameWithoutExt = fileName.substring(0, lastDot);
                int lastDash = nameWithoutExt.lastIndexOf('-');
                if (lastDash > 0) {
                    String movieName = nameWithoutExt.substring(0, lastDash);
                    String resolution = nameWithoutExt.substring(lastDash + 1);
                    VideoFile vf = new VideoFile(movieName, resolution, format, file.getAbsolutePath());
                    existingFiles.add(vf);
                    System.out.println("  " + vf);
                }
            }
        }
        Map<String, Set<String>> existingFormatsPerMovie = new HashMap<>();
        Map<String, Integer> maxResolutionPerMovie = new HashMap<>();
        for (VideoFile vf : existingFiles) {
            existingFormatsPerMovie.putIfAbsent(vf.movieName, new HashSet<>());
            existingFormatsPerMovie.get(vf.movieName).add(vf.format);
            int currentRes = getResolutionIndex(vf.resolution);
            int maxRes = maxResolutionPerMovie.getOrDefault(vf.movieName, 0);
            if (currentRes > maxRes) {
                maxResolutionPerMovie.put(vf.movieName, currentRes);
            }
        }
        System.out.println("Movie Analysis:");
        for (String movieName : existingFormatsPerMovie.keySet()) {
            int maxRes = maxResolutionPerMovie.get(movieName);
            System.out.println("Movie: " + movieName);
            System.out.println("  Existing formats: " + existingFormatsPerMovie.get(movieName));
            System.out.println("  Max resolution: " + resolutionIndexToString(maxRes));
        }
        System.out.println("Generating missing files with JAVE2:");
        for (String movieName : existingFormatsPerMovie.keySet()) {
            int maxResValue = maxResolutionPerMovie.get(movieName);
            String maxResString = resolutionIndexToString(maxResValue);
            Map<String, String> sourceFilePerFormat = new HashMap<>();
            for (VideoFile vf : existingFiles) {
                if (vf.movieName.equals(movieName)) {
                    String existing = sourceFilePerFormat.get(vf.format);
                    if (existing == null || getResolutionIndex(vf.resolution) > getResolutionIndex(extractResolutionFromFilename(existing))) {
                        sourceFilePerFormat.put(vf.format, vf.toString());
                    }
                }
            }
            for (String targetFormat : SUPPORTED_FORMATS) {
                String sourceFormat = targetFormat;
                if (!sourceFilePerFormat.containsKey(targetFormat)) {
                    if (sourceFilePerFormat.isEmpty()) {
                        System.out.println("  " + movieName + ": No source files at all - skipping");
                        continue;
                    }
                    sourceFormat = sourceFilePerFormat.keySet().iterator().next();
                    System.out.println("  " + movieName + ": Format " + targetFormat + " missing - will create from " + sourceFormat);
                }
                String sourceFile = sourceFilePerFormat.get(sourceFormat);
                if (sourceFile == null) {
                    for (VideoFile vf : existingFiles) {
                        if (vf.movieName.equals(movieName) && vf.format.equals(sourceFormat)) {
                            sourceFile = vf.toString();
                            break;
                        }
                    }
                }
                if (sourceFile == null) continue;
                for (String targetRes : SUPPORTED_RESOLUTIONS) {
                    int targetResValue = getResolutionIndex(targetRes);
                    if (targetResValue <= maxResValue) {
                        String targetFile = movieName + "-" + targetRes + "." + targetFormat;
                        if (!fileExists(videosFolderPath, targetFile)) {
                            System.out.println("  Creating: " + targetFile + " (from " + sourceFile + ")");
                            convertWithJAVE2(videosFolderPath, sourceFile, targetFile, targetRes);
                        } else {
                            System.out.println("  Already exists: " + targetFile);
                        }
                    } else {
                        System.out.println("  Cannot create: " + movieName + "-" + targetRes + "." + targetFormat + " (exceeds max resolution " + maxResString + ")");
                    }
                }
                System.out.println();
            }
        }
        File[] finalFiles = videosFolder.listFiles();
        for (File file : finalFiles) {
            if (file.isFile() && !file.getName().startsWith(".")) {
                availableVideos.add(file.getName());
            }
        }
        System.out.println("Total available videos: " + availableVideos.size());
        for (int i = 0; i < availableVideos.size(); i++) {
            System.out.println("  " + (i+1) + ". " + availableVideos.get(i));
        }
        try (ServerSocket controlSocket = new ServerSocket(CONTROL_PORT)) {
            System.out.println("Server listening on control port " + CONTROL_PORT);
            logger.info("Server listening on port " + CONTROL_PORT);
            while (true) {
                System.out.println("Waiting for a client to connect...");
                Socket clientSocket = controlSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress());
                logger.info("Client connected: " + clientSocket.getInetAddress());
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                boolean connected = true;
                while (connected) {
                    out.println(availableVideos.size());
                    for (String video : availableVideos) {
                        out.println(video);
                    }
                    String choiceStr = in.readLine();
                    if (choiceStr == null) {
                        System.out.println("Client disconnected unexpectedly");
                        connected = false;
                        break;
                    }
                    int choice = Integer.parseInt(choiceStr);
                    if (choice == 0) {
                        System.out.println("Client chose to exit");
                        out.println("Goodbye!");
                        connected = false;
                    } else if (choice >= 1 && choice <= availableVideos.size()) {
                        String selectedVideo = availableVideos.get(choice - 1);
                        System.out.println("Client selected: " + selectedVideo);
                        logger.info("Client selected: " + selectedVideo);
                        out.println("SELECTED:" + selectedVideo);
                        startStreaming(videosFolderPath, selectedVideo, out);
                        System.out.println("Returning to menu for client...");
                    } else {
                        System.out.println("Invalid choice: " + choice);
                        out.println("ERROR: Invalid choice");
                    }
                }
                clientSocket.close();
                System.out.println("Client disconnected");
            }
        } catch (IOException e) {
            System.err.println("Server socket error: " + e.getMessage());
            logger.severe("Server socket error: " + e.getMessage());
        }
    }
    
    static void startStreaming(String videosFolderPath, String videoFile, PrintWriter out) {
        String ffmpegPath = "ffmpeg/bin/ffmpeg.exe";
        String fullVideoPath = videosFolderPath + "/" + videoFile;
        try {
            System.out.println("  Streaming server ready on port " + STREAM_PORT);
            out.println("STREAM_READY:" + STREAM_PORT);
            List<String> command = new ArrayList<>();
            command.add(ffmpegPath);
            command.add("-i");
            command.add(fullVideoPath);
            command.add("-c");
            command.add("copy");
            command.add("-f");
            command.add("mpegts");
            command.add("tcp://127.0.0.1:" + STREAM_PORT + "?listen");
            System.out.println("  Starting FFmpeg streaming: " + String.join(" ", command));
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process ffmpegProcess = pb.start();
            BufferedReader ffmpegOutput = new BufferedReader(new InputStreamReader(ffmpegProcess.getInputStream()));
            Thread outputReader = new Thread(() -> {
                try {
                    String line;
                    while ((line = ffmpegOutput.readLine()) != null) {
                        System.out.println("  [FFmpeg] " + line);
                    }
                } catch (IOException e) {
                }
            });
            outputReader.start();
            int exitCode = ffmpegProcess.waitFor();
            System.out.println("  FFmpeg streaming finished with exit code: " + exitCode);
        } catch (Exception e) {
            System.err.println("  Error during streaming: " + e.getMessage());
            out.println("STREAM_ERROR:" + e.getMessage());
        }
    }
    
    static void convertWithJAVE2(String folderPath, String sourceFile, String targetFile, String targetResolution) {
        String sourcePath = folderPath + File.separator + sourceFile;
        String targetPath = folderPath + File.separator + targetFile;
        int height = getResolutionIndex(targetResolution);
        int width = (height * 16) / 9;
        try {
            AudioAttributes audio = new AudioAttributes();
            audio.setCodec("libmp3lame");
            VideoAttributes video = new VideoAttributes();
            video.setCodec("libx264");
            video.setSize(new VideoSize(width, height));
            EncodingAttributes attrs = new EncodingAttributes();
            if (targetFile.endsWith(".mp4")) {
                attrs.setFormat("mp4");
            } else if (targetFile.endsWith(".avi")) {
                attrs.setFormat("avi");
            } else if (targetFile.endsWith(".mkv")) {
                attrs.setFormat("matroska");
            }
            attrs.setAudioAttributes(audio);
            attrs.setVideoAttributes(video);
            Encoder encoder = new Encoder();
            encoder.encode(new File(sourcePath), new File(targetPath), attrs);
            System.out.println("    Successfully created: " + targetFile);
            logger.info("Created: " + targetFile);
        } catch (IllegalArgumentException e) {
            System.err.println("    Error: " + e.getMessage());
            logger.warning("Conversion error: " + e.getMessage());
        } catch (InputFormatException e) {
            System.err.println("    Input format error: " + e.getMessage());
            logger.warning("Input format error: " + e.getMessage());
        } catch (EncoderException e) {
            System.err.println("    Encoder error: " + e.getMessage());
            logger.warning("Encoder error: " + e.getMessage());
        }
    }
    
    static int getResolutionIndex(String resolution) {
        switch (resolution) {
            case "240p": return 240;
            case "360p": return 360;
            case "480p": return 480;
            case "720p": return 720;
            case "1080p": return 1080;
            default: return 0;
        }
    }
    
    static String resolutionIndexToString(int resolution) {
        switch (resolution) {
            case 240: return "240p";
            case 360: return "360p";
            case 480: return "480p";
            case 720: return "720p";
            case 1080: return "1080p";
            default: return "unknown";
        }
    }
    
    static String extractResolutionFromFilename(String filename) {
        int lastDash = filename.lastIndexOf('-');
        int lastDot = filename.lastIndexOf('.');
        if (lastDash > 0 && lastDot > lastDash) {
            return filename.substring(lastDash + 1, lastDot);
        }
        return "240p";
    }
    
    static boolean fileExists(String folderPath, String fileName) {
        File f = new File(folderPath + File.separator + fileName);
        return f.exists();
    }
}