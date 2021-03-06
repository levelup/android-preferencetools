package com.levelup.preferences.sample;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;

import com.levelup.preferences.SharedPreferencesTools;

public class MainActivity extends Activity {

	private SharedPreferencesTools<InvisiblePreferences> mCachedPrefs;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		mCachedPrefs = InvisiblePreferences.getPrefs(this);

		// read the counter, increment it, rewrite it
		int counter = mCachedPrefs.getInt(InvisiblePreferences.NotificationWarningCounter);
		mCachedPrefs.putInt(InvisiblePreferences.NotificationWarningCounter, ++counter);

		TextView counterView = (TextView) findViewById(R.id.counter);
		counterView.setText(String.valueOf(counter));

		// read/write a boolean preference
		CheckBox again = (CheckBox) findViewById(R.id.checkBoxLater);
		again.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				mCachedPrefs.putBoolean(InvisiblePreferences.ShowNotificationWarningAgain, isChecked);
			}
		});

		findViewById(R.id.button1).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				startActivity(new Intent(getBaseContext(), InvisiblePreferencesActivity.class));
			}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();

		CheckBox again = (CheckBox) findViewById(R.id.checkBoxLater);
		again.setChecked(mCachedPrefs.getBoolean(InvisiblePreferences.ShowNotificationWarningAgain));
		
		TextView stringPref = (TextView) findViewById(R.id.textViewStringPref);
		stringPref.setText(mCachedPrefs.getString(InvisiblePreferences.LastNotificationText));
	}


	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

}
