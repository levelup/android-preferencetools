package com.levelup.preferences.sample;

import android.content.Context;
import android.content.SharedPreferences;

import com.levelup.preferences.SharedPreferencesTools;
import com.levelup.preferences.SharedPreferencesTools.PreferenceKey;
import com.levelup.preferences.SharedPreferencesTools.PreferenceKeyStringMapping;

/**
 * example of a list of preferences that are never shown to the user but cached in memory
 */
public enum InvisiblePreferences implements PreferenceKey {
	/**
	 * a {@link Boolean} preference that can be read/written quickly
	 * <p>read using {@link SharedPreferencesTools#getBoolean(PreferenceKey)}, write using {@link SharedPreferencesTools#putBoolean(PreferenceKey, boolean)} 
	 */
	ShowNotificationWarningAgain(true),
	
	/**
	 * a {@link Integer} preference that can be read/written quickly
	 * <p>read using {@link SharedPreferencesTools#getInt(PreferenceKey)}, write using {@link SharedPreferencesTools#putInt(PreferenceKey, int)} 
	 */
	NotificationWarningCounter((int) 0),

	/**
	 * a {@link Long} preference that can be read/written quickly
	 * <p>read using {@link SharedPreferencesTools#getLong(PreferenceKey)}, write using {@link SharedPreferencesTools#putLong(PreferenceKey, long)} 
	 */
	LastTimeNotificationShown((long) 0),

	/**
	 * a {@link String} preference that can be read/written quickly
	 * 
	 * <p>read using {@link SharedPreferencesTools#getString(PreferenceKey)}, write using {@link SharedPreferencesTools#putString(PreferenceKey, String)}
	 * <p>Warning: only a String preference can have a null default value 
	 */
	LastNotificationText(null);
	

	/**
	 * the object keeping the cached values in memory
	 */
	private static SharedPreferencesTools<InvisiblePreferences> mCachedPrefs;
	/**
	 * name of the stored {@link SharedPreferences} file
	 */
	private static final String PREFS_NAME = "HiddenPrefs";
	
	/**
	 * helper method to get access to the cached values, creates the object if it doesn't exist
	 * @param context used to open the SharedPreferences
	 * @return the object that contains all the cached values
	 */
	public static synchronized SharedPreferencesTools<InvisiblePreferences> getPrefs(Context context) {
		if (mCachedPrefs==null) {
			mCachedPrefs = new SharedPreferencesTools<InvisiblePreferences>(context, PREFS_NAME,
					new PreferenceKeyStringMapping<InvisiblePreferences>() {
				@Override
				public InvisiblePreferences storageToKey(String storageName) {
					for (InvisiblePreferences v : values())
						if (v.getStorageName().equals(storageName))
							return v;
					return null;
				}
			});
		}
		return mCachedPrefs;
	}
	
	private final Object defaultValue;

	/**
	 * internal constructor with a boolean default value
	 * @param defaultValue
	 */
	private InvisiblePreferences(boolean defaultValue) {
		this.defaultValue = Boolean.valueOf(defaultValue);
	}
	
	/**
	 * internal constructor with an int default value
	 * @param defaultValue
	 */
	private InvisiblePreferences(int defaultValue) {
		this.defaultValue = Integer.valueOf(defaultValue);
	}
	
	/**
	 * internal constructor with a long default value
	 * @param defaultValue
	 */
	private InvisiblePreferences(long defaultValue) {
		this.defaultValue = Long.valueOf(defaultValue);
	}
	
	/**
	 * internal constructor with a String default value
	 * @param defaultValue
	 */
	private InvisiblePreferences(String defaultValue) {
		this.defaultValue = defaultValue;
	}
	
	@Override
	public <T> T defaultValue() {
		return (T) defaultValue;
	}

	@Override
	public String getStorageName() {
		return name();
	}
}
