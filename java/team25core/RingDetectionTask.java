package team25core;

import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.util.ElapsedTime;
import com.vuforia.PIXEL_FORMAT;
import com.vuforia.Vuforia;

import org.firstinspires.ftc.robotcore.external.ClassFactory;
import org.firstinspires.ftc.robotcore.external.Telemetry;
import org.firstinspires.ftc.robotcore.external.hardware.camera.WebcamName;
import org.firstinspires.ftc.robotcore.external.navigation.VuforiaLocalizer;
import org.firstinspires.ftc.robotcore.external.tfod.Recognition;
import org.firstinspires.ftc.robotcore.external.tfod.TFObjectDetector;

import java.util.ArrayList;
import java.util.List;


public class RingDetectionTask extends RobotTask {

    public enum EventKind {
        OBJECTS_DETECTED,
    }

    protected ElapsedTime timer;

    public class RingDetectionEvent extends RobotEvent {

        public EventKind kind;
        public List<Recognition> rings;

        //this is constructor for ring detection event
        public RingDetectionEvent(RobotTask task, EventKind kind, List<Recognition> m)
        {
            super(task);
            this.kind = kind;
            this.rings = new ArrayList<>(m.size());
            this.rings.addAll(m);
        }

        public String toString()
        {
            return kind.toString();
        }
    }

    private VuforiaLocalizer vuforia;
    private Telemetry telemetry;
    private TFObjectDetector tfod;

    public static final String LABEL_QUAD_RINGS = "Quad";
    private static final String LABEL_SINGLE_RING = "Single";
    private static final String TFOD_MODEL_ASSET = "UltimateGoal.tflite";
    private int rateLimitMs;
    private DetectionKind detectionKind;
    private String cameraName;

    public enum DetectionKind {
        EVERYTHING, //this may go away
        QUAD_RING_DETECTED,
        SINGLE_RING_DETECTED,
        LARGEST_SKY_STONE_DETECTED, //this may go away
        UNKNOWN_DETECTED,
    }
     public enum RingKind {
            SINGLE_KIND,
            QUAD_KIND,
            UNKNOWN_KIND,
     };


    //for phone camera constructor
    public RingDetectionTask(Robot robot)
    {
        super(robot);

        rateLimitMs = 0;
        detectionKind = DetectionKind.EVERYTHING;
    }
    //for webcamera construtor
    public RingDetectionTask(Robot robot, String cameraName)
    {
        super(robot);
        rateLimitMs = 0;
        detectionKind = DetectionKind.EVERYTHING;
        this.cameraName = cameraName;
    }

    private void initVuforia(HardwareMap hardwareMap) {
        /*
         * Configure Vuforia by creating a Parameter object, and passing it to the Vuforia engine.
         */
        //new=your own copy of vuforia parameters
        VuforiaLocalizer.Parameters parameters = new VuforiaLocalizer.Parameters();
        parameters.cameraDirection = VuforiaLocalizer.CameraDirection.BACK;
        //if webcam
        if (cameraName != null) {
            parameters.vuforiaLicenseKey = VuforiaConstants.WEBCAM_VUFORIA_KEY;
            parameters.cameraName = hardwareMap.get(WebcamName.class, cameraName);

        } else {   // if phonecam
            parameters.vuforiaLicenseKey = VuforiaConstants.VUFORIA_KEY;
        }

        //  Instantiate the Vuforia engine
        vuforia = ClassFactory.getInstance().createVuforia(parameters);
        Vuforia.setFrameFormat(PIXEL_FORMAT.RGB565,true);
        vuforia.setFrameQueueCapacity(1);

        // Loading trackables is not necessary for the Tensor Flow Object Detection engine.
    }

    private void initTfod(HardwareMap hardwareMap)
    {
        int tfodMonitorViewId = hardwareMap.appContext.getResources().getIdentifier(
                "tfodMonitorViewId", "id", hardwareMap.appContext.getPackageName());
        TFObjectDetector.Parameters tfodParameters = new TFObjectDetector.Parameters(tfodMonitorViewId);
        //tfodParameters.minimumConfidence = 0.6; //the example in the Ultimate Goal Tensor flow example defaults to a MinimumConfidence of 0.8f
        //concept tensor flow object detection had minimum confidence
        tfod = ClassFactory.getInstance().createTFObjectDetector(tfodParameters, vuforia);
        tfod.loadModelFromAsset(TFOD_MODEL_ASSET, LABEL_QUAD_RINGS, LABEL_SINGLE_RING);
    }

    public void init(Telemetry telemetry, HardwareMap hardwareMap)
    {
        initVuforia(hardwareMap);

        // if (ClassFactory.getInstance().canCreateTFObjectDetector()) {
            initTfod(hardwareMap);
//        } else {
//            telemetry.addData("Sorry!", "This device is not compatible with TFOD");
//        }
    }

    public void rateLimit(int ms)
    {
        this.rateLimitMs = ms;
    }

    public void setDetectionKind(DetectionKind detectionKind)
    {
        this.detectionKind = detectionKind;
    }
    //this will start tfod activation and start detecting
    @Override
    public void start()
    {
        tfod.activate();

        if (rateLimitMs != 0) {
            timer = new ElapsedTime(ElapsedTime.Resolution.MILLISECONDS);
        }
    }

    @Override
    public void stop()
    {
        tfod.deactivate();
        robot.removeTask(this);
    }

    public static RingKind isRing(Recognition object)
    {
        if (object.getLabel().equals(LABEL_QUAD_RINGS)) {
            return RingKind.QUAD_KIND;
        } else if (object.getLabel().equals(LABEL_SINGLE_RING)) {
            return RingKind.SINGLE_KIND;
        } else {
            return RingKind.UNKNOWN_KIND;
        }
    }
    //if recognize anything will add to que
    protected void processEverything(List<Recognition> objects)
    {
        if (objects.size() > 0) {
            robot.queueEvent(new RingDetectionEvent(this, EventKind.OBJECTS_DETECTED, objects));
        }
    }
    //only adds rings which will make event and add to que
    protected void processQuadRing(List<Recognition> objects)
    {
        List<Recognition> rings = new ArrayList<>();
        for (Recognition object : objects) {
            if (isRing(object) == RingKind.QUAD_KIND) {
                rings.add(object);
            }
        }

        if (!rings.isEmpty()) {
            robot.queueEvent(new RingDetectionEvent(this, EventKind.OBJECTS_DETECTED, rings));
        }
    }

    protected void processSingleRing(List<Recognition> objects)
    {
        List<Recognition> singlerings = new ArrayList<>();
        for (Recognition object : objects) {
            if (isRing(object) == RingKind.SINGLE_KIND) {
                singlerings.add(object);
            }
        }

        if (!singlerings.isEmpty()) {
            robot.queueEvent(new RingDetectionEvent(this, EventKind.OBJECTS_DETECTED, singlerings));
        }
    }

    //timeslice calls to get information from recognition
    protected void processDetectedObjects(List<Recognition> objects)
    {
        if (objects == null || objects.isEmpty()) {
            return;
        }

        switch (detectionKind) {
            case EVERYTHING:
                processEverything(objects);
                break;
            case SINGLE_RING_DETECTED:
                processSingleRing(objects);
                break;
            case QUAD_RING_DETECTED:
                processQuadRing(objects);
                break;
        }
    }

    @Override
    public boolean timeslice()
    {
     //timeslice set to 0 do when it gets called
        if (rateLimitMs != 0) {
            if (timer.time() < rateLimitMs) {
                return false;
            }
        }
        //shows location of stone
        processDetectedObjects(tfod.getUpdatedRecognitions());

        if (rateLimitMs != 0) {
            timer.reset();
        }
        return false;
    }
}
