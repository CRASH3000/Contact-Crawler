package com.pingme.contactcrawler.thread;

public class SimpleStatusLogger implements Runnable {

    private final long intervalMillis;

    public SimpleStatusLogger() {
        this(10_000L);
    }

    public SimpleStatusLogger(long intervalMillis) {
        this.intervalMillis = intervalMillis;
    }

    @Override
    public void run() {
        while (true) {
            try {
                System.out.println("[SimpleStatusLogger] Приложение работает, краулер готов к работе...");
                Thread.sleep(intervalMillis);
            } catch (InterruptedException e) {
                // Если поток прервали выходим из цикла и завершаем поток
                System.out.println("[SimpleStatusLogger] Поток логгера остановлен.");
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
