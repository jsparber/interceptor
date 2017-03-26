package com.juliansparber.vpnMITM;

/**
 * Created by jSparber on 3/18/17.
 */


public class TrafficBlocker {
    public boolean blockTraffic;

    public TrafficBlocker() {
        blockTraffic = false;
    }

    public void doWait() {
        synchronized (this) {
            try {
                this.wait();
            } catch (InterruptedException e) {

            }
        }
    }

    public void doNotify(boolean blocker) {
        synchronized (this) {
            this.blockTraffic = blocker;
            this.notify();
        }
    }
}

/*public class MyWaitNotify{

    TrafficBlocker blocker = new TrafficBlocker();

    public void doWait(){
        synchronized(blocker){
            try{
                blocker.wait();
            } catch(InterruptedException e){

            }
        }
    }

    public void doNotify(){
        synchronized(blocker){
            blocker.notify();
        }
    }
}
*/
