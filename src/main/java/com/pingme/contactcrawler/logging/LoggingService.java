package com.pingme.contactcrawler.logging;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Service
public class LoggingService {

    private final BlockingQueue<String> logQueue = new LinkedBlockingQueue<>();

    @PostConstruct
    public void startLoggerThread() {
        FileLogger fileLogger = new FileLogger(logQueue, "logs/crawler.log");

        Thread loggerThread = new Thread(fileLogger, "file-logger-thread");
        loggerThread.setDaemon(true); // демон-поток для логирования
        loggerThread.start();
    }

    public void log(String message) {
        logQueue.offer(message);
    }
}