package net.myegotrip.egotrip.profile;


import net.myegotrip.egotrip.R;
import android.os.Bundle;
import android.preference.PreferenceActivity;

public class ProfilePrefActivity extends PreferenceActivity {
	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.profile_preferences);
    }
}
