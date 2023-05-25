import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.domain.WritePrecision;

import java.io.IOException;
import java.time.Instant;
import com.influxdb.client.write.Point;
import org.iot.raspberry.grovepi.GrovePi;
import org.iot.raspberry.grovepi.pi4j.GrovePi4J;
import org.iot.raspberry.grovepi.sensors.analog.GroveRotarySensor;
import org.iot.raspberry.grovepi.sensors.digital.GroveButton;
import org.iot.raspberry.grovepi.sensors.i2c.GroveRgbLcd;
import org.iot.raspberry.grovepi.sensors.listener.GroveButtonListener;
import org.iot.raspberry.grovepi.sensors.synch.SensorMonitor;

import java.util.logging.Level;
import java.util.logging.Logger;

public class NanoFactory {

    /* -------------------------- INFLUX DB -------------------------- */
    public static final String INFLUX_TOKEN = "2_sE2UaVrCiJTp0AN4ys_D_sayS6SI_nRodYsQRltqag_ZiBoAoHGQq92dHzHyNZpZC1-IZf7_O6SdzJcWPYIA==";
    public static final String INFLUX_CONNECTION_URL = "http://169.254.179.226:8086";
    public static final String INFLUX_BUCKET = "BucketProgetto";
    public static final String INFLUX_ORG = "supsi";

    /* -------------------------- CONSTANTS -------------------------- */
    public static final String SORTER_NAME = "Sorter";
    public static final String TRAPDOOR_NAME = "Trapdoor";
    public static final String HUMAN_INTERVENTION = "HumanIntervention";
    public static final int RS_THRESHOLD = 10;
    public static final int TRAPDOOR_BRIGHT_OPEN = 47;    // Based on blue opening, degrees
    public static final int TRAPDOOR_DARK_OPEN = 157;     // Based on blue opening, degrees
    public static final int SORTER_IDLE = 197;
    public static final int SORTER_DARK_SIDE = 72;
    public static final int SORTER_BRIGHT_SIDE = 300;

    /* -------------------------- STATIC VARIABLES -------------------------- */

    private static SorterStatus registerSorterEvent;
    private static TrapdoorStatus lastTrapdoorStatus;
    private static int buttonClickCounter = 0;
    public static InfluxDBClient client;

    /* -------------------------- ENUMS -------------------------- */

    enum SorterStatus{
        BRIGHT_SIDE("BrightSide"),
        DARK_SIDE("DarkSide"),
        IDLE("Idle");

        public final String description;
        private SorterStatus(String description){
            this.description = description;
        }
    }

    enum TrapdoorStatus{
        BRIGHT_OPEN("BrightOpen"),
        DARK_OPEN("DarkOpen");

        public final String description;
        private TrapdoorStatus(String description){
            this.description = description;
        }
    }

    /* -------------------------- METHODS -------------------------- */

    /*
        This method is used to tell if a sensor is within a given range utilizing a certain treshold
     */
    private static boolean isInPosition(double degrees, double desiredSpot, double threshhold){
        return degrees + threshhold > desiredSpot && degrees - threshhold < desiredSpot;
    }

    /*
        This method saves a point on the Influx Database
     */
    private static void saveMeasurementOnInflux(String sourceSensorName, String sourceSensorStatus, double sourceSensorValue){
        if(client != null){
            Point point = Point
                    .measurement(sourceSensorName)                       // Nome sensore
                    .addTag("SensorStatus", sourceSensorStatus)          // Stato sensore
                    .addField("SensorValue", sourceSensorValue)     // Valore sensore
                    .time(Instant.now(), WritePrecision.NS);

            // Write on influx DB
            client.getWriteApiBlocking().writePoint(INFLUX_BUCKET, INFLUX_ORG, point);
        }
    }

    private static void updateLCDScreen(GroveRgbLcd lcdScreen){
        try{
            lcdScreen.setRGB(255,255,255);
            lcdScreen.setText(String.format("Errori: %d", buttonClickCounter));
        }catch(IOException ioe){
            ioe.printStackTrace();
        }
    }

    private static void giveGreenFedback(GroveRgbLcd lcdScreen){
        try{
            lcdScreen.setRGB(0,255,0);
            Thread.sleep(50);
            lcdScreen.setRGB(255,255,255);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {
        // GrovePi setup
        Logger.getLogger("GrovePi").setLevel(Level.WARNING);
        Logger.getLogger("RaspberryPi").setLevel(Level.WARNING);
        GrovePi grovePi = new GrovePi4J();

        // Influx setup
        client = InfluxDBClientFactory.create(INFLUX_CONNECTION_URL, INFLUX_TOKEN.toCharArray());

        // Components setup
        GroveRgbLcd lcdScreen = grovePi.getLCD();

        GroveRotarySensor trapdoorRotatorySensor = new GroveRotarySensor(grovePi, 1);   // Analogic
        GroveRotarySensor sorterRotatorySensor = new GroveRotarySensor(grovePi, 0);     // Analogic

        GroveButton buttonIncrement = new GroveButton(grovePi, 7);
        GroveButton buttonDecrement = new GroveButton(grovePi, 8);
        GroveButton buttonSubmit = new GroveButton(grovePi, 6);

        SensorMonitor<Boolean> buttonIncrementSensorMonitor = new SensorMonitor<>(buttonIncrement, 100);
        SensorMonitor<Boolean> buttonDecrementSensorMonitor = new SensorMonitor<>(buttonDecrement, 100);
        SensorMonitor<Boolean> buttonSubmitSensorMonitor = new SensorMonitor<>(buttonSubmit, 100);

        buttonIncrement.setButtonListener(new GroveButtonListener() {
            @Override
            public void onRelease() {}
            @Override
            public void onPress() {}
            @Override
            public void onClick() {
                buttonClickCounter++;
                updateLCDScreen(lcdScreen);
            }
        });

        buttonDecrement.setButtonListener(new GroveButtonListener() {
            @Override
            public void onRelease() {}
            @Override
            public void onPress() {}

            @Override
            public void onClick() {
                if(buttonClickCounter > 0){
                    buttonClickCounter--;
                    updateLCDScreen(lcdScreen);
                }
            }
        });

        buttonSubmit.setButtonListener(new GroveButtonListener() {
            @Override
            public void onRelease() {}
            @Override
            public void onPress() {}

            @Override
            public void onClick() {
                saveMeasurementOnInflux(
                        HUMAN_INTERVENTION,
                        HUMAN_INTERVENTION,
                        buttonClickCounter
                );

                buttonClickCounter = 0;
                updateLCDScreen(lcdScreen);

                // Give green feedback
                giveGreenFedback(lcdScreen);
            }
        });

        buttonIncrementSensorMonitor.start();
        buttonDecrementSensorMonitor.start();
        buttonSubmitSensorMonitor.start();

        updateLCDScreen(lcdScreen);

        while(true){
            // Sorter
            if(isInPosition(sorterRotatorySensor.get().getDegrees(), SORTER_IDLE, RS_THRESHOLD)){
                // IDLE
                if(registerSorterEvent != SorterStatus.IDLE) {
                    saveMeasurementOnInflux(
                            SORTER_NAME,
                            SorterStatus.IDLE.description,
                            sorterRotatorySensor.get().getDegrees()
                    );

                    registerSorterEvent = SorterStatus.IDLE;
                }
            }else if(isInPosition(sorterRotatorySensor.get().getDegrees(), SORTER_BRIGHT_SIDE, RS_THRESHOLD)){
                // BRIGHT SIDE
                if(registerSorterEvent != SorterStatus.BRIGHT_SIDE){
                    saveMeasurementOnInflux(
                            SORTER_NAME,
                            SorterStatus.BRIGHT_SIDE.description,
                            sorterRotatorySensor.get().getDegrees()
                    );

                    registerSorterEvent = SorterStatus.BRIGHT_SIDE;
                }
            }else if(isInPosition(sorterRotatorySensor.get().getDegrees(), SORTER_DARK_SIDE, RS_THRESHOLD)){
                // DARK SIDE
                if(registerSorterEvent != SorterStatus.DARK_SIDE){
                    saveMeasurementOnInflux(
                            SORTER_NAME,
                            SorterStatus.DARK_SIDE.description,
                            sorterRotatorySensor.get().getDegrees()
                    );

                    registerSorterEvent = SorterStatus.DARK_SIDE;
                }
            }

            // Trapdoor
            if(isInPosition(trapdoorRotatorySensor.get().getDegrees(), TRAPDOOR_BRIGHT_OPEN, RS_THRESHOLD)){
                // Bright open, Dark closed
                if(lastTrapdoorStatus != TrapdoorStatus.BRIGHT_OPEN){
                    saveMeasurementOnInflux(
                            TRAPDOOR_NAME,
                            TrapdoorStatus.BRIGHT_OPEN.description,
                            trapdoorRotatorySensor.get().getDegrees()
                    );

                    lastTrapdoorStatus = TrapdoorStatus.BRIGHT_OPEN;
                }
            }else if(isInPosition(trapdoorRotatorySensor.get().getDegrees(), TRAPDOOR_DARK_OPEN, RS_THRESHOLD)){
                // Bright open, Dark closed
                if(lastTrapdoorStatus != TrapdoorStatus.DARK_OPEN){
                    saveMeasurementOnInflux(
                            TRAPDOOR_NAME,
                            TrapdoorStatus.DARK_OPEN.description,
                            trapdoorRotatorySensor.get().getDegrees()
                    );

                    lastTrapdoorStatus = TrapdoorStatus.DARK_OPEN;
                }
            }
            Thread.sleep(250);
        }
    }
}
