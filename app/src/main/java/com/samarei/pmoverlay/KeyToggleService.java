package com.samarei.pmoverlay;

import android.accessibilityservice.AccessibilityService;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

/**
 * Lets you show or hide the overlay from inside any app: hold the remote's Menu (≡) button for over half a
 * second. It only looks at key presses, never consumes them (the app still gets the button) and reads
 * nothing on screen (res/xml/key_toggle.xml).
 *
 * Turned on with: settings put secure enabled_accessibility_services
 *   com.samarei.pmoverlay/com.samarei.pmoverlay.KeyToggleService (appended to any existing ones)
 */
public class KeyToggleService extends AccessibilityService {

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        // Remotes differ on auto-repeat, so time the hold from press to release.
        if (event.getKeyCode() == KeyEvent.KEYCODE_MENU && event.getAction() == KeyEvent.ACTION_UP
                && event.getEventTime() - event.getDownTime() >= 600) {
            boolean on = !OverlayService.isOn(this);
            OverlayService.setOn(this, on);
            Toast.makeText(this, on ? "Polymarket on" : "Polymarket off", Toast.LENGTH_SHORT).show();
        }
        return false;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
