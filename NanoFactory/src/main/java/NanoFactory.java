import org.iot.raspberry.grovepi.GrovePi;
import org.iot.raspberry.grovepi.pi4j.GrovePi4J;
import org.iot.raspberry.grovepi.sensors.analog.GroveRotarySensor;
import org.iot.raspberry.grovepi.sensors.i2c.GroveRgbLcd;

import java.util.logging.Level;
import java.util.logging.Logger;

public class NanoFactory {

    public static final int RS_THRESHOLD = 10;

    // Trapdoor:    47 - 157                47: Black open, Blue closed - 157: Black closed, Blue open
    public static final int TRAPDOOR_BRIGHT_OPEN = 47;     // Based on blue opening, degrees
    public static final int TRAPDOOR_DARK_OPEN = 157;     // Based on blue opening, degrees


    // Sorter:      72 - 197 - 300          72: Dark colored balls - 197: Idle - 300: light colored balls
    public static final int SORTER_IDLE = 197;
    public static final int SORTER_DARK_SIDE = 72;
    public static final int SORTER_BRIGHT_SIDE = 300;

    private static boolean registerSorterEvent = true;
    private static TrapdoorStatus lastTrapdoorStatus;

    enum SorterStatus{
        BRIGHT_SIDE,
        DARK_SIDE,
        IDLE
    }

    enum TrapdoorStatus{
        BRIGHT_OPEN,
        DARK_OPEN
    }

    private static boolean isInPosition(double degrees, double desiredSpot, double threshhold){
        return degrees + threshhold > desiredSpot && degrees - threshhold < desiredSpot;
    }

    private static void saveOnInflux(){

        // TODO - To Be Implemented

        /*Point point = Point
                .measurement("")                                                    // Nome sensore
                .tag("randomNew2", "randomValue")                   // Stato sensore
                .addField("random_value", (int)(Math.random() * 100))       // Valore sensore
                .time(Instant.now(), WritePrecision.NS);
*/

    }

    public static void main(String[] args) throws Exception {
        Logger.getLogger("GrovePi").setLevel(Level.WARNING);
        Logger.getLogger("RaspberryPi").setLevel(Level.WARNING);

        GrovePi grovePi = new GrovePi4J();

        // Setup components
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

                // Write on Influx Database

                registerSorterEvent = true;
            }else if(isInPosition(sorterRotatorySensor.get().getDegrees(), SORTER_BRIGHT_SIDE, RS_THRESHOLD)){
                // BRIGHT
                if(registerSorterEvent){
                    // Save on Database

                    // Write on Influx Database

                    registerSorterEvent = false;
                }
            }else if(isInPosition(sorterRotatorySensor.get().getDegrees(), SORTER_DARK_SIDE, RS_THRESHOLD)){
                // DARK
                if(registerSorterEvent){
                    // Save on Database

                    // Write on Influx Database

                    registerSorterEvent = false;
                }
            }

            // Trapdoor
            if(isInPosition(trapdoorRotatorySensor.get().getDegrees(), TRAPDOOR_BRIGHT_OPEN, RS_THRESHOLD)){
                // Bright open, Dark closed
                if(lastTrapdoorStatus != TrapdoorStatus.DARK_OPEN){

                    // Write on Influx Database
                    // TODO - To Test

                    lastTrapdoorStatus = TrapdoorStatus.BRIGHT_OPEN;
                }
            }else if(isInPosition(trapdoorRotatorySensor.get().getDegrees(), TRAPDOOR_DARK_OPEN, RS_THRESHOLD)){
                // Bright open, Dark closed
                if(lastTrapdoorStatus != TrapdoorStatus.BRIGHT_OPEN){

                    // Write on Influx Database
                    // TODO - To Test

                    lastTrapdoorStatus = TrapdoorStatus.DARK_OPEN;
                }
            }
            Thread.sleep(500);
        }
    }
}
