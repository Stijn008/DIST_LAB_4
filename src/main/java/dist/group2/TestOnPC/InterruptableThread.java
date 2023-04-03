package dist.group2.TestOnPC;

public class InterruptableThread extends Thread {
    public InterruptableThread(Runnable target) {
        super(target);
    }
    public void startInterruptable() {
        // Start the thread
        this.start();

        // Continue thread if no interrupt occurs
        while(!this.isInterrupted()) {}

        // Thread interrupted
        System.out.println("Client thread stopped due to interruption");
    }
}
