package com.levelup.preferences.sample;

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class InvisiblePreferencesActivity extends PreferenceActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		getPreferenceManager().setSharedPreferencesName(InvisiblePreferences.PREFS_NAME);
		addPreferencesFromResource(R.xml.invisible_prefs);
	}
}
