/*	
 * Class 	TrackingGPRS
 * 
 * This software is developed for Choral devices with Java.
 * Copyright Choral srl. All Rights reserved. 
 */
package general;

import choral.io.Can;
import com.cinterion.io.*;

/**
 * Thread that controls the periodic sending of GPS positions (GPRMC strings)
 * through GPRS connection, using a timer with time set.
 *
 * @version	1.02 <BR> <i>Last update</i>: 25-10-2007
 * @author alessioza
 *
 */
public class TrackingGPRS extends Thread implements GlobCost {

    /* 
     * local variables
     */
    /**
     * Store received msg into local mailbox
     */
    private String msgRicevuto = "";
    /**
     * Sleep indicator of current tracking
     */
    private boolean sospendiTrack = false;
    /**
     * Tracking execution indicator
     */
    private boolean trackingAttivo = false;
    private boolean exit = false;
    protected BCListener BCL;
    private String infoGps;
    private boolean invia_to_socket = false;
    private boolean setStop = true;
    private double ctrlSpeed = 0.0;
    private int val_insensibgps;
    Can can;
    int counterCanMsg = 0;
    int timer2 = 0;

    /* 
     * constructors
     */
    public TrackingGPRS() {
        //System.out.println("Th*TrackingGPRS: CREATED");
        can = new Can();
    }

    /*
     * methods
     */
    /**
     * Thread execution code:
     * <BR> ---------- <BR>
     * Performed operations: <br>
     * <ul type="disc">
     * <li> Create task to send strings through GPRS connection and pass
     * parameters;
     * <li> Loop for receive message sent from tasks and AppMain (about trasking
     * start and suspen).
     * </ul>
     */
    public void run() {

        //System.out.println("Th*TrackingGPRS: STARTED");
        while (!InfoStato.getInstance().isCloseTrackingGPRS()) {
			//try {

            // Bearer Control
            BCL = new BCListener();
            BearerControl.addListener(BCL);

            /* 
             * WAIT FOR MESSAGES into MAILBOX for GPRS TRACKING START
             * 
             */
				//System.out.println("Th*TrackingGPRS: Ready to start GPRS TRACKING...");
            while (true) {

                if (msgRicevuto.equalsIgnoreCase(timeoutExpired)) {
                    //System.out.println("GPRS TIMEOUT ON MBOX3");
                    SemAT.getInstance().getCoin(5);
                    InfoStato.getInstance().writeATCommand("AT+CGATT=0\r");
                    SemAT.getInstance().putCoin();
                }

                if (msgRicevuto.equalsIgnoreCase(rebootTrack)) {
                    break;
                }

					// *** FORCED CLOSURE OF THREAD			
                if (msgRicevuto.equalsIgnoreCase(exitTrack)) {

                    // Case 1: active tracking  -> suspend current tracking and exit
                    if (trackingAttivo == true) {
                        sospendiTrack = true;
                        exit = true;
                    } // Case 2: not active tracking -> exit directly
                    else {
                        break;
                    }

                } //stopTrack

					// *** FINISHED to SEND strings through GPRS	
                if (msgRicevuto.equalsIgnoreCase(invioCompletato)) {

                    if (Settings.getInstance().getSetting("generalDebug", false)) {
                        System.out.println("Th*TrackingGPRS: Tracking finished!!");
                    }
                    // reset timer and task, and enable CSD
                    sospendiTrack = true;

                } //invioCompletato

					// *** Suspend current tracking, if active			
                if (sospendiTrack == true) {

                    InfoStato.getInstance().setEnableCSD(true);

                    if (Settings.getInstance().getSetting("generalDebug", false)) {
                        System.out.println("Th*TrackingGPRS: Reset socket task e timer completed");
                    }

                    if (exit == true) {
                        break;
                    }

                } //sospendiTrack

                if (msgRicevuto.equalsIgnoreCase(trackNormale) || msgRicevuto.equalsIgnoreCase(trackMovimento)
                        || msgRicevuto.equalsIgnoreCase(trackAttivChiave) || msgRicevuto.equalsIgnoreCase(trackDisattivChiave)
                        || msgRicevuto.equalsIgnoreCase(trackAlarmIn1) || msgRicevuto.equalsIgnoreCase(trackAlarmIn2)
                        || msgRicevuto.equalsIgnoreCase(trackBatteria) || msgRicevuto.equalsIgnoreCase(trackSMS)
                        || msgRicevuto.equalsIgnoreCase(trackAlive) || msgRicevuto.equalsIgnoreCase(trackCodice)
                        || msgRicevuto.equalsIgnoreCase(trackUrcSim)) {

                    String datoToSend;
                    //if (GPRSposFormat == CHORAL) {
                    if (Settings.getInstance().getSetting("format", "USR").equals("USR")) {
                        infoGps = null;
                        Posusr msg = new Posusr();
                        String tempRMC = (String) DataStores.getInstance(DataStores.dsDRMC).getLastValid();
                        String tempGGA = (String) DataStores.getInstance(DataStores.dsDGGA).getLastValid();
                        if (tempRMC == null) {
                            tempRMC = "";
                        }
                        if (tempGGA == null) {
                            tempGGA = "";
                        }
                        if (tempRMC.equals(tempGGA)) {
                            tempRMC = "";
                        }
                        if ((tempRMC != null) && (!(tempRMC.equals("")))) {
                            infoGps = msg.set_posusr(tempRMC, tempGGA);
                        } else {
                            infoGps = Settings.getInstance().getSetting("header", "$")
                                    + "," + Settings.getInstance().getSetting("clientID", InfoStato.getInstance().getIMEI())
                                    + defaultGPS;
                        }
                        datoToSend = infoGps;
                    } else {
                        datoToSend = (String) DataStores.getInstance(DataStores.dsDRMC).getLastValid();
                    }

                    if ((datoToSend == null) || (datoToSend.indexOf("null") >= 0)) {
                        if (Settings.getInstance().getSetting("format", "USR").equals("USR")) {
                            datoToSend = Settings.getInstance().getSetting("header", "$")
                                    + "," + Settings.getInstance().getSetting("clientID", InfoStato.getInstance().getIMEI())
                                    + defaultGPS + ",<ERROR>*00";
                        }
                        /*else{
                         datoToSend = defaultGpsNMEA;
                         }*/

                    }
                    if (Settings.getInstance().getSetting("format", "USR").equals("USR")) {
                        if (msgRicevuto.equalsIgnoreCase(trackMovimento)) {
                            datoToSend = datoToSend + ",ALR<" + alarmMovimento + ">";
                        } else if (msgRicevuto.equalsIgnoreCase(trackAttivChiave)) {
                            datoToSend = datoToSend + ",ALR<" + alarmChiaveAttivata + ">";
                        } else if (msgRicevuto.equalsIgnoreCase(trackDisattivChiave)) {
                            datoToSend = datoToSend + ",ALR<" + alarmChiaveDisattivata + ">";
                        } else if (msgRicevuto.equalsIgnoreCase(trackBatteria)) {
                            datoToSend = datoToSend + ",ALR<" + alarmBatteria + ">";
                        } else if (msgRicevuto.equalsIgnoreCase(trackAlarmIn1)) {
                            datoToSend = datoToSend + ",ALR<" + alarmIn1 + ">";
                        } else if (msgRicevuto.equalsIgnoreCase(trackAlarmIn2)) {
                            datoToSend = datoToSend + ",ALR<" + alarmIn2 + ">";
                        } else if (msgRicevuto.equalsIgnoreCase(trackAlive)) {
                            datoToSend = datoToSend + ",ALR<" + alive + ">";
                        } else if (msgRicevuto.equalsIgnoreCase(trackCodice)) {
                            datoToSend = datoToSend + ",COD<" + InfoStato.getInstance().getCode() + ">";
                        } else if (msgRicevuto.equalsIgnoreCase(trackUrcSim)) {
                            datoToSend = datoToSend + ",ALR<URC SIM>";
                        } else;
                    }

                    if (Settings.getInstance().getSetting("format", "USR").equals("USR")) {
                        datoToSend = datoToSend + "*" + this.getChecksum(datoToSend).toUpperCase();
                    }

                    if (Settings.getInstance().getSetting("generalDebug", false)) {
                        System.out.println(datoToSend);
                    }
                    new LogError("Trk " + datoToSend);

						//System.out.println("TIME DATA: " + datoToSend);
                    //System.out.println("RAM DATA: " + InfoStato.getInstance().getDataRAM());
                    // Aggiunto il 29/12/2011 [MB]
                    ctrlSpeed = InfoStato.getInstance().getSpeedDFS();
                    if (Settings.getInstance().getSetting("speedDebug", false)) {
                        ctrlSpeed = InfoStato.getInstance().getSpeedGree();
                        //System.out.println("SPEED " + ctrlSpeed);
                    }

                    InfoStato.getInstance().setSpeedForTrk(ctrlSpeed);
                        val_insensibgps = Settings.getInstance().getSetting("minSpeed", 0);

                    if (msgRicevuto.equalsIgnoreCase(trackNormale)) {

                        if ((ctrlSpeed >= val_insensibgps)) {
                            invia_to_socket = true;
                            setStop = false;
                        } else {
                            if ((!InfoStato.getInstance().getPreAlive()) && (ctrlSpeed <= val_insensibgps) && (InfoStato.getInstance().getPreSpeedDFS() > val_insensibgps)) {

                                invia_to_socket = true;
                                setStop = false;
                            } else {
                                if (!setStop) {
                                    invia_to_socket = true;
                                    setStop = true;
                                } else {
                                    invia_to_socket = false;
                                }
                            }
                        }
                    } else {
                        invia_to_socket = true;
                    }
                    if (invia_to_socket) {
                        try {
                            // semophore for queue
                            while (!InfoStato.getCoda()) {
                                Thread.sleep(1);
                            }
                        } catch (InterruptedException e) {
                        }

                        if ((InfoStato.getInstance().getDataRAM()).equals("")) {
                            InfoStato.getInstance().setDataRAM(datoToSend);
                            if (Settings.getInstance().getSetting("generalDebug", false)) {
                                System.out.println(datoToSend);
                            }
                        } else {

                            String datoToMem = InfoStato.getInstance().getDataRAM();
                            InfoStato.getInstance().setDataRAM(datoToSend);

                            /*
                             * Save data to send
                             */
                            int temp = InfoStato.getInstance().getTrkIN();
                            //System.out.println("Th*TrackingGPRS: pointer in - " + temp);
                            if ((temp >= codaSize) || (temp < 0)) {
                                temp = 0;
                            }
                            new LogError("Th*TrackingGPRS: pointer in - " + temp + " " + datoToMem);
                            InfoStato.getInstance().saveRecord(temp, datoToMem);
                            temp++;
                            if ((temp >= codaSize) || (temp < 0)) {
                                temp = 0;
                            }
                            InfoStato.getInstance().setTrkIN(temp);

                            invia_to_socket = false;
                        }
                        InfoStato.freeCoda();
                    }
                }

                //variable to verify task operation
                timer2++;
                InfoStato.getInstance().setTask2Timer(timer2);
                /*
                 * Read message from Mailbox, if present
                 * 
                 * read() method is BLOCKING, until a message is received
                 * (of some sort) while loop is stopped
                 */
                msgRicevuto = (String) Mailboxes.getInstance(3).read();
                if (Settings.getInstance().getSetting("generalDebug", false)) {
                    System.out.println("Th*TrackingGPRS: Received message: " + msgRicevuto);
                }

                // *** Check if tracking is active				
                if (trackingAttivo == true) {
                    sospendiTrack = true;
                }

            } //while(true)		

            if (Settings.getInstance().getSetting("generalDebug", false)) {
                System.out.println("Th*TrackingGPRS: END");
            }
            InfoStato.getInstance().setTrackingAttivo(false);

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }

            if (InfoStato.getInstance().closeGPRS()) {
                InfoStato.getInstance().setCloseGPRS(false);
                // Sending communication to AppMain
                Mailboxes.getInstance(0).write(msgCloseGPRS);
                break;
            }

        } //while

    } //run

    public String getHexaString(int num) {

        String dato = Integer.toHexString(num);
        if (dato.length() > 2) {
            dato = dato.substring(dato.length() - 2);
        }
        if (dato.length() < 2) {
            dato = "0" + dato;
        }
        return dato;

    }
    
    public String getChecksum(String sentence) {
        String checksum;

        try {
            int[] intSentence = new int[sentence.length()];
            intSentence[0] = sentence.charAt(0);

            for (int i = 1; i < sentence.length(); i++) {
                intSentence[i] = intSentence[i - 1] ^ sentence.charAt(i);
            }

            checksum = Integer.toHexString(intSentence[sentence.length() - 1]);
            if (checksum.length() < 2) {
                return "0" + checksum;
            } else {
                return checksum;
            }
        } catch (IndexOutOfBoundsException e) {
            return "00";
        }
    }

} //TrackingGPRS

