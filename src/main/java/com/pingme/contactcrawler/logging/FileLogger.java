package com.pingme.contactcrawler.logging;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.concurrent.BlockingQueue;

public class FileLogger implements Runnable {

    private final BlockingQueue<String> queue;
    private final String filePath;
    private final Object lock = new Object(); // объект для synchronized

    public FileLogger(BlockingQueue<String> queue, String filePath) {
        this.queue = queue;
        this.filePath = filePath;
    }

    @Override
    public void run() {
        // Создаём папку logs, если её нет
        try {
            if (Paths.get(filePath).getParent() != null) {
                Files.createDirectories(Paths.get(filePath).getParent());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        while (true) {
            try {
                String message = queue.take();
                writeLine(message);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    // synchronized примитив синхронизации, защищает запись в файл
    private void writeLine(String message) {
        String line = LocalDateTime.now() + " - " + message;
        synchronized (lock) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath, true))) {
                writer.write(line);
                writer.newLine();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
