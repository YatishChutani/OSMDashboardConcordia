package de.storchp.opentracks.osmplugin.compass;

import android.content.Context;

import de.storchp.opentracks.osmplugin.utils.MapUtils;
import de.storchp.opentracks.osmplugin.utils.PreferencesUtils;

/**
 * Derived from <a href="https://github.com/kylecorry31/Trail-Sense/blob/master/app/src/main/java/com/kylecorry/trail_sense/shared/sensors/VectorCompass.kt">...</a>
 */
public class Compass extends AbstractSensor {

    private final AbstractLowPassSensor accelerometer;
    private final Magnetometer magnetometer;

    private final MovingAverageFilter filter;

    private float filteredBearing = 0f;
    private float bearing = 0f;

    private boolean gotMag = false;
    private boolean gotAccel = false;

    public Compass(Context context) {
        super();
        accelerometer = SensorChecker.hasGravity(context) ? new GravitySensor(context) : new LowPassAccelerometer(context);
        magnetometer = new Magnetometer(context);
        filter = new MovingAverageFilter(PreferencesUtils.getCompassSmoothing() * 2 * 2);
    }

    private void updateBearing(float newBearing) {
        bearing += MapUtils.deltaAngle(bearing, newBearing);
        filteredBearing = (float)filter.filter(bearing);
    }

    public Bearing getBearing() {
        return new Bearing(filteredBearing);
    }

    private boolean updateSensor() {
        if (!gotAccel || !gotMag) {
            return true;
        }

        // Gravity
        var normGravity = accelerometer.getValue().normalize();
        var normMagField = magnetometer.getValue().normalize();

        // East vector
        var  east = normMagField.cross(normGravity);
        var  normEast = east.normalize();

        // Magnitude check
        float eastMagnitude = east.magnitude();
        float gravityMagnitude = accelerometer.getValue().magnitude();
        float magneticMagnitude = magnetometer.getValue().magnitude();
        if (gravityMagnitude * magneticMagnitude * eastMagnitude < 0.1f) {
            return true;
        }

        // North vector
        var north = normMagField.minus(normGravity.times(normGravity.dot(normMagField)));
        var normNorth = north.normalize();

        // Azimuth
        // NB: see https://math.stackexchange.com/questions/381649/whats-the-best-3d-angular-co-ordinate-system-for-working-with-smartfone-apps
        float sin = normEast.getY() - normNorth.getX();
        float cos = normEast.getX() + normNorth.getY();
        float azimuth = (sin != 0f && cos != 0f) ? (float) Math.atan2(sin, cos) : 0f;

        if (Float.isNaN(azimuth)){
            return true;
        }

        updateBearing((float) Math.toDegrees(azimuth));
        notifyListeners();
        return true;
    }

    private boolean updateAccel() {
        gotAccel = true;
        return updateSensor();
    }

    private boolean updateMag() {
        gotMag = true;
        return updateSensor();
    }

    @Override
    public void startImpl() {
        accelerometer.start(this::updateAccel);
        magnetometer.start(this::updateMag);
    }

    @Override
    public void stopImpl() {
        accelerometer.stop(this::updateAccel);
        magnetometer.stop(this::updateMag);
    }

}
