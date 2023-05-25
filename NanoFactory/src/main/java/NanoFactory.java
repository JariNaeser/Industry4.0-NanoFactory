import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.domain.WritePrecision;
import java.time.Instant;
import com.influxdb.client.write.Point;
import org.iot.raspberry.grovepi.GrovePi;
import org.iot.raspberry.grovepi.pi4j.GrovePi4J;
import org.iot.raspberry.grovepi.sensors.analog.GroveRotarySensor;
import org.iot.raspberry.grovepi.sensors.i2c.GroveRgbLcd;

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
    public static final int RS_THRESHOLD = 10;
    public static final int TRAPDOOR_BRIGHT_OPEN = 47;    // Based on blue opening, degrees
    public static final int TRAPDOOR_DARK_OPEN = 157;     // Based on blue opening, degrees
    public static final int SORTER_IDLE = 197;
    public static final int SORTER_DARK_SIDE = 72;
    public static final int SORTER_BRIGHT_SIDE = 300;

    /* -------------------------- STATIC VARIABLES -------------------------- */

    private static SorterStatus registerSorterEvent;
    private static TrapdoorStatus lastTrapdoorStatus;

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
    private static void saveMeasurementOnInflux(String sourceSensorName, String sourceSensorStatus, double sourceSensorValue, InfluxDBClient client){
        Point point = Point
                .measurement(sourceSensorName)                       // Nome sensore
                .addTag("SensorStatus", sourceSensorStatus)          // Stato sensore
                .addField("SensorValue", sourceSensorValue)     // Valore sensore
                .time(Instant.now(), WritePrecision.NS);

        // Write on influx DB
        client.getWriteApiBlocking().writePoint(INFLUX_BUCKET, INFLUX_ORG, point);
    }

    public static void main(String[] args) throws Exception {
        // GrovePi setup
        Logger.getLogger("GrovePi").setLevel(Level.WARNING);
        Logger.getLogger("RaspberryPi").setLevel(Level.WARNING);
        GrovePi grovePi = new GrovePi4J();
        // Influx setup
        InfluxDBClient client = InfluxDBClientFactory.create(INFLUX_CONNECTION_URL, INFLUX_TOKEN.toCharArray());

        // Components setup
        GroveRotarySensor trapdoorRotatorySensor = new GroveRotarySensor(grovePi, 1);   // Analogic
        GroveRotarySensor sorterRotatorySensor = new GroveRotarySensor(grovePi, 0);     // Analogic
        GroveRotarySensor operatorRotatorySensor = new GroveRotarySensor(grovePi, 2);   // Analogic

        GroveRgbLcd lcdScreen = grovePi.getLCD();

        lcdScreen.setRGB(255,255,255);
        lcdScreen.setText("Hello");

        while(true){
            // Sorter
            if(isInPosition(sorterRotatorySensor.get().getDegrees(), SORTER_IDLE, RS_THRESHOLD)){
                // IDLE
                if(registerSorterEvent != SorterStatus.IDLE) {
                    saveMeasurementOnInflux(
                            SORTER_NAME,
                            SorterStatus.IDLE.description,
                            sorterRotatorySensor.get().getDegrees(),
                            client
                    );

                    registerSorterEvent = SorterStatus.IDLE;
                }
            }else if(isInPosition(sorterRotatorySensor.get().getDegrees(), SORTER_BRIGHT_SIDE, RS_THRESHOLD)){
                // BRIGHT SIDE
                if(registerSorterEvent != SorterStatus.BRIGHT_SIDE){
                    saveMeasurementOnInflux(
                            SORTER_NAME,
                            SorterStatus.BRIGHT_SIDE.description,
                            sorterRotatorySensor.get().getDegrees(),
                            client
                    );

                    registerSorterEvent = SorterStatus.BRIGHT_SIDE;
                }
            }else if(isInPosition(sorterRotatorySensor.get().getDegrees(), SORTER_DARK_SIDE, RS_THRESHOLD)){
                // DARK SIDE
                if(registerSorterEvent != SorterStatus.DARK_SIDE){
                    saveMeasurementOnInflux(
                            SORTER_NAME,
                            SorterStatus.DARK_SIDE.description,
                            sorterRotatorySensor.get().getDegrees(),
                            client
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
                            trapdoorRotatorySensor.get().getDegrees(),
                            client
                    );

                    lastTrapdoorStatus = TrapdoorStatus.BRIGHT_OPEN;
                }
            }else if(isInPosition(trapdoorRotatorySensor.get().getDegrees(), TRAPDOOR_DARK_OPEN, RS_THRESHOLD)){
                // Bright open, Dark closed
                if(lastTrapdoorStatus != TrapdoorStatus.DARK_OPEN){
                    saveMeasurementOnInflux(
                            TRAPDOOR_NAME,
                            TrapdoorStatus.DARK_OPEN.description,
                            trapdoorRotatorySensor.get().getDegrees(),
                            client
                    );

                    lastTrapdoorStatus = TrapdoorStatus.DARK_OPEN;
                }
            }
            Thread.sleep(250);
        }
    }
}
