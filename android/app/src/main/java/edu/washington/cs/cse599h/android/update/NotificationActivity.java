package edu.washington.cs.cse599h.android.update;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import edu.washington.cs.cse599h.android.MainActivity;

public class NotificationActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // If this activity is the root activity of the task, the app is not running
        if (isTaskRoot()) {
            // Start the app before finishing
            final Intent parentIntent = new Intent(this, MainActivity.class);
            parentIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            /*
            final Intent startAppIntent = new Intent(this, DfuActivity.class);
			startAppIntent.putExtras(getIntent().getExtras());
			startActivities(new Intent[] { parentIntent, startAppIntent });
			*/
            startActivity(parentIntent);
        }

        // Now finish, which will drop the user in to the activity that was at the top
        //  of the task stack
        finish();
    }
}