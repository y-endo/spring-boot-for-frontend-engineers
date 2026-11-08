void main() {
    IO.println("java.version   = " + System.getProperty("java.version"));
    IO.println("java.vendor    = " + System.getProperty("java.vendor"));
    IO.println("os.name        = " + System.getProperty("os.name"));
    IO.println("user.timezone  = " + java.time.ZoneId.systemDefault());
    IO.println("file.encoding  = " + System.getProperty("file.encoding"));
    IO.println("stdout.encoding= " + System.getProperty("stdout.encoding"));
    IO.println("processors     = " + Runtime.getRuntime().availableProcessors());
    IO.println("max heap (MB)  = " + Runtime.getRuntime().maxMemory() / 1024 / 1024);
}
