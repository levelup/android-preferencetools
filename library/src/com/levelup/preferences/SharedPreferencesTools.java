/*
 * Copyright (C) 2013 LevelUp Studio
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.levelup.preferences;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CopyOnWriteArrayList;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

/**
 * A convenient tools to manipulate {@link SharedPreferences} with a fast copy in memory
 * 
 * @author <a href="http://levelupstudio.com/">LevelUp Studio</a>
 * 
 */
public class SharedPreferencesTools<K extends SharedPreferencesTools.PreferenceKey> implements android.content.SharedPreferences.OnSharedPreferenceChangeListener {

	private static final String LOG_TAG = "PrefTool";
	
	/**
	 * Interface defining the format of a key use to save/restore data from the {@link SharedPreferencesTools}
	 * <p>The type of the value is deduced from the type of the {@link #defaultValue()}, if it's null it's assumed to be a {@link String}
	 */
	public interface PreferenceKey {
		/**
		 * Get the storage name of the preference object
		 * <p>It must be unique per {@link PreferenceKey}
		 * @return
		 */
		String getStorageName();

		/**
		 * Get the default value for this preference
		 * @return null only possible for String values
		 */
		<T> T defaultValue();
	}

	/**
	 * Interface for the storage to {@link PreferenceKey} mapping
	 * <p>Used on the {@link SharedPreferencesTools} constructor and if you are using {@link OnSharedPreferenceChangeListener}
	 * @param <K> The type of key returned
	 */
	public interface PreferenceKeyStringMapping<K> {
		/**
		 * 
		 * @param storageName The name of the key used for storage
		 * @return a {@link PreferenceKey} for the specified storage name of the key
		 */
		K storageToKey(String storageName);
	}

	/**
	 * Interface definition for a callback to be invoked when a shared
	 * preference is changed.
	 */
	public interface OnSharedPreferenceChangeListener {
		/**
		 * Called when a shared preference is changed, added, or removed. This
		 * may be called even if a preference is set to its existing value.
		 *
		 * <p>This callback will be run on your main thread.
		 *
		 * @param sharedPreferences The {@link SharedPreferencesTools} that received
		 *            the change.
		 * @param key The key of the preference that was changed, added, or
		 *            removed.
		 */
		<K extends PreferenceKey> void onSharedPreferenceChanged(SharedPreferencesTools<K> sharedPreferences, K key);
	}

	/**
	 * Interface to make enums safe to save/restore in persistent storage as an integer
	 *
	 * @param <E> the type of the enum, ie itself
	 */
	public interface StorableEnumInteger<E extends Enum<?>> {
		/**
		 * Turn the storage value into an enum value
		 * @param storedValue The stored value to transform
		 * @return the enum value corresponding to the storage, can be null
		 */
		E storageToValue(int storedValue);

		/**
		 * Turn an enum value into a value suitable for storage
		 * @param item The item to transform
		 * @return An integer that can be used by {@link #storageToValue(int)} later
		 */
		int storageValue(E item);
	}

	/**
	 * Interface to make enums safe to save/restore in persistent storage as a string
	 *
	 * @param <E> the type of the enum, ie itself
	 */
	public interface StorableEnumString<E extends Enum<?>> {
		/**
		 * Turn the storage value into an enum value
		 * @param storedValue The stored value to transform
		 * @return the enum value corresponding to the storage, can be null
		 */
		E storageToValue(String storedValue);

		/**
		 * Turn an enum value into a value suitable for storage
		 * @param item The item to transform
		 * @return A string that can be used by {@link #storageToValue(String)} later
		 */
		String storageValue(E item);
	}

	private enum AsyncMessages {
		MSG_STORE_STRING,
		MSG_STORE_BOOLEAN,
		MSG_STORE_INT,
		MSG_STORE_LONG,
		MSG_STORE_FLOAT,
		MSG_REMOVE_KEY
	}

	private final HashMap<K, Object> values = new HashMap<K, Object>();
	private final CopyOnWriteArrayList<WeakReference<OnSharedPreferenceChangeListener>> changeListeners = new CopyOnWriteArrayList<WeakReference<OnSharedPreferenceChangeListener>>();
	private final PreferenceKeyStringMapping<K> keySource;
	private final Handler saveStoreHandler;

	/**
	 * Constructor using the default application preference file for storage
	 * @param context Used to get the shared preference object
	 * @param keySource The interface used to map the storage names to the {@link PreferenceKey}, not null
	 */
	public SharedPreferencesTools(Context context, PreferenceKeyStringMapping<K> keySource) {
		this(context, context.getPackageName()+"_preferences", keySource);
	}

	/**
	 * Constructor
	 * @param context Used to get the shared preference object
	 * @param prefName The name of the preference file
	 * @param keySource The interface used to map the storage names to the {@link PreferenceKey}, not null
	 */
	@SuppressLint("HandlerLeak")
	public SharedPreferencesTools(Context context, String prefName, PreferenceKeyStringMapping<K> keySource) {
		this.keySource = keySource;
		final SharedPreferences prefs = context.getSharedPreferences(prefName, 0);
		prefs.registerOnSharedPreferenceChangeListener(this);
		final Map<String, ?> allValues = prefs.getAll();
		if (allValues!=null) {
			for (Entry<String, ?> entry : allValues.entrySet()) {
				K key = keySource.storageToKey(entry.getKey());
				values.put(key, entry.getValue());
			}
		}
		
		HandlerThread handlerThread = new HandlerThread("Prefs_"+prefName, android.os.Process.THREAD_PRIORITY_BACKGROUND);
		handlerThread.start();

		saveStoreHandler = new Handler(handlerThread.getLooper()) {
			public void handleMessage(Message msg) {
				final SharedPreferences.Editor editor;

				switch (AsyncMessages.values()[msg.what]) {
				case MSG_STORE_STRING:
					@SuppressWarnings("unchecked")
					Entry<K, String> stringData = (Entry<K, String>) msg.obj;
					editor = prefs.edit();
					editor.putString(stringData.getKey().getStorageName(), stringData.getValue());
					editor.commit();
					break;

				case MSG_STORE_BOOLEAN:
					@SuppressWarnings("unchecked")
					Entry<K, Boolean> boolData = (Entry<K, Boolean>) msg.obj;
					editor = prefs.edit();
					editor.putBoolean(boolData.getKey().getStorageName(), boolData.getValue());
					editor.commit();
					break;

				case MSG_STORE_INT:
					@SuppressWarnings("unchecked")
					Entry<K, Integer> intData = (Entry<K, Integer>) msg.obj;
					editor = prefs.edit();
					editor.putInt(intData.getKey().getStorageName(), intData.getValue());
					editor.commit();
					break;

				case MSG_STORE_LONG:
					@SuppressWarnings("unchecked")
					Entry<K, Long> longData = (Entry<K, Long>) msg.obj;
					editor = prefs.edit();
					editor.putLong(longData.getKey().getStorageName(), longData.getValue());
					editor.commit();
					break;

				case MSG_STORE_FLOAT:
					@SuppressWarnings("unchecked")
					Entry<K, Float> floatData = (Entry<K, Float>) msg.obj;
					editor = prefs.edit();
					editor.putFloat(floatData.getKey().getStorageName(), floatData.getValue());
					editor.commit();
					break;

				case MSG_REMOVE_KEY:
					editor = prefs.edit();
					editor.remove(((K) msg.obj).getStorageName());
					editor.commit();
					break;
				}
			}
		};
	}

	// ===========================================================
	// GETTER
	// ===========================================================

	/**
	 * Get the {@link Boolean} preference value for the specified key 
	 * @param key The key corresponding to the preference
	 * @return The value for the key (never null)
	 * @throws IllegalArgumentException if the key doesn't correspond to a boolean value
	 */
	public boolean getBoolean(K key) {
		if (!(key.defaultValue() instanceof Boolean)) throw new IllegalArgumentException(key+" is not a Boolean");
		Boolean value = (Boolean) values.get(key);
		if (value==null) {
			value = (Boolean) key.defaultValue();
		}
		return value.booleanValue();
	}

	/**
	 * Get the {@link Integer} preference value for the specified key
	 * @param key The key corresponding to the preference
	 * @return The value for the key (never null)
	 * @throws IllegalArgumentException if the key doesn't correspond to an integer value
	 */
	public int getInt(K key) {
		if (!(key.defaultValue() instanceof Integer)) throw new IllegalArgumentException(key+" is not a Integer");
		Integer value = (Integer) values.get(key);
		if (value==null) {
			value = (Integer) key.defaultValue();
		}
		return value.intValue();
	}

	/**
	 * Get the {@link Long} preference value for the specified key
	 * @param key The key corresponding to the preference
	 * @return The value for the key (never null)
	 * @throws IllegalArgumentException if the key doesn't correspond to a long value
	 */
	public long getLong(K key) {
		if (!(key.defaultValue() instanceof Long)) throw new IllegalArgumentException(key+" is not a Long");
		Long value = (Long) values.get(key);
		if (value==null) {
			value = (Long) key.defaultValue();
		}
		return value.longValue();
	}

	/**
	 * Get the {@link Float} preference value for the specified key
	 * @param key The key corresponding to the preference
	 * @return The value for the key, may be null
	 * @throws IllegalArgumentException if the key doesn't correspond to a float value
	 */
	public float getFloat(K key) {
		if (!(key.defaultValue() instanceof Float)) throw new IllegalArgumentException(key+" is not a Float");
		Float value = (Float) values.get(key);
		if (value==null) {
			value = (Float) key.defaultValue();
		}
		return value.floatValue();
	}

	/**
	 * Get the {@link String} preference value for the specified key
	 * @param key The key corresponding to the preference
	 * @return The value for the key, may be null
	 * @throws IllegalArgumentException if the key doesn't correspond to a String value
	 */
	public String getString(K key) {
		if (key.defaultValue()!=null && !(key.defaultValue() instanceof String)) throw new IllegalArgumentException(key+" is not a String");
		String value = (String) values.get(key);
		if (value==null) {
			value = (String) key.defaultValue();
		}
		return value;
	}

	/**
	 * Get the {@link StorableEnumInteger} preference value for the specified key
	 * @param key The key corresponding to the preference
	 * @return The value for the key
	 * @throws IllegalArgumentException if the key doesn't correspond to a StorableEnum value
	 */
	public <E extends Enum<? extends StorableEnumInteger<?>>> E getIntEnum(K key) {
		if (!(key.defaultValue() instanceof StorableEnumInteger)) throw new IllegalArgumentException(key+" is not a StorableEnumInteger");
		Integer value = (Integer) values.get(key);
		StorableEnumInteger<E> storage = key.defaultValue();
		E result = null;
		if (value!=null)
			result = storage.storageToValue(value.intValue());

		if (result == null)
			result = key.defaultValue();
		return result;
	}


	/**
	 * Get the {@link StorableEnumString} preference value for the specified key
	 * @param key The key corresponding to the preference
	 * @return The value for the key
	 * @throws IllegalArgumentException if the key doesn't correspond to a StorableEnum value
	 */
	public <E extends Enum<? extends StorableEnumString<?>>> E getStringEnum(K key) {
		if (!(key.defaultValue() instanceof StorableEnumString)) throw new IllegalArgumentException(key+" is not a StorableEnumString");
		String value = (String) values.get(key);
		StorableEnumString<E> storage = key.defaultValue();
		E result = null;
		if (value!=null)
			result = storage.storageToValue(value);

		if (result == null)
			result = key.defaultValue();
		return result;
	}



	// ===========================================================
	// SETTER
	// ===========================================================

	/**
	 * Set a String value in the preferences
	 * @param key
	 *            The name of the preference to modify.
	 * @param value
	 *            The new value for the preference.
	 * @throws IllegalArgumentException if the key doesn't correspond to a String value
	 */
	public void putString(final K key, final String value) {
		if (key.defaultValue()!=null && !(key.defaultValue() instanceof String)) throw new IllegalArgumentException(key+" is not a String");

		String oldValue = (String) values.put(key, value);

		if (oldValue==null || !oldValue.equals(value)) {
			Entry<K, ?> data = new SavedEntry<K, String>(key, value);
			saveStoreHandler.sendMessage(Message.obtain(saveStoreHandler, AsyncMessages.MSG_STORE_STRING.ordinal(), data));
			onPreferenceChanged(key);
		}
	}

	/**
	 * Set a boolean value in the preferences editor
	 * @param key
	 *            The name of the preference to modify.
	 * @param value
	 *            The new value for the preference.
	 * @throws IllegalArgumentException if the key doesn't correspond to a boolean value
	 */
	public void putBoolean(final K key, final boolean value) {
		if (!(key.defaultValue() instanceof Boolean)) throw new IllegalArgumentException(key+" is not a Boolean");

		Boolean oldValue = (Boolean) values.put(key, value);

		if (oldValue==null || !oldValue.equals(value)) {
			Entry<K, ?> data = new SavedEntry<K, Boolean>(key, value);
			saveStoreHandler.sendMessage(Message.obtain(saveStoreHandler, AsyncMessages.MSG_STORE_BOOLEAN.ordinal(), data));
			onPreferenceChanged(key);
		}
	}

	/**
	 * Set a int value in the preferences
	 * @param key
	 *            The name of the preference to modify.
	 * @param value
	 *            The new value for the preference.
	 * @throws IllegalArgumentException if the key doesn't correspond to an integer value
	 */
	public void putInt(final K key, final int value) {
		if (!(key.defaultValue() instanceof Integer)) throw new IllegalArgumentException(key+" is not a Integer");

		Integer oldValue = (Integer) values.put(key, value);

		if (oldValue==null || !oldValue.equals(value)) {
			Entry<K, ?> data = new SavedEntry<K, Integer>(key, value);
			saveStoreHandler.sendMessage(Message.obtain(saveStoreHandler, AsyncMessages.MSG_STORE_INT.ordinal(), data));
			onPreferenceChanged(key);
		}
	}

	/**
	 * Set a long value in the preferences
	 * @param key
	 *            The name of the preference to modify.
	 * @param value
	 *            The new value for the preference.
	 * @throws IllegalArgumentException if the key doesn't correspond to a long value
	 */
	public void putLong(final K key, final long value) {
		if (!(key.defaultValue() instanceof Long)) throw new IllegalArgumentException(key+" is not a Long");

		Long oldValue = (Long) values.put(key, value);

		if (oldValue==null || !oldValue.equals(value)) {
			Entry<K, ?> data = new SavedEntry<K, Long>(key, value);
			saveStoreHandler.sendMessage(Message.obtain(saveStoreHandler, AsyncMessages.MSG_STORE_LONG.ordinal(), data));
			onPreferenceChanged(key);
		}
	}

	/**
	 * Set a float value in the preferences
	 * @param key
	 *            The name of the preference to modify.
	 * @param value
	 *            The new value for the preference.
	 * @throws IllegalArgumentException if the key doesn't correspond to a float value
	 */
	public void putFloat(final K key, final float value) {
		if (!(key.defaultValue() instanceof Float)) throw new IllegalArgumentException(key+" is not a Float");

		Float oldValue = (Float) values.put(key, value);

		if (oldValue==null || Float.compare(oldValue, value)!=0) {
			Entry<K, ?> data = new SavedEntry<K, Float>(key, value);
			saveStoreHandler.sendMessage(Message.obtain(saveStoreHandler, AsyncMessages.MSG_STORE_FLOAT.ordinal(), data));
			onPreferenceChanged(key);
		}
	}

	/**
	 * Set a {@link StorableEnumInteger} value in the preferences
	 * @param key The name of the preference to modify.
	 * @param value The new value for the preference.
	 * @throws IllegalArgumentException if the key doesn't correspond to a {@link StorableEnumInteger} value
	 */
	@SuppressWarnings("unchecked")
	public <E extends Enum<? extends StorableEnumInteger<?>>> void putIntEnum(K key, E value) {
		if (!(key.defaultValue() instanceof StorableEnumInteger)) throw new IllegalArgumentException(key+" is not a StorableEnumInteger");

		StorableEnumInteger<E> storage = key.defaultValue();
		E oldValue;
		if (value==null) {
			oldValue = (E) values.remove(key);
		} else {
			Object v = values.put(key, storage.storageValue(value));
			if (v instanceof Integer)
				oldValue = ((StorableEnumInteger<E>) value).storageToValue(((Integer) v).intValue());
			else
				oldValue = (E) v; 
		}

		if (oldValue!=value) {
			if (value==null)
				saveStoreHandler.sendMessage(Message.obtain(saveStoreHandler, AsyncMessages.MSG_REMOVE_KEY.ordinal(), key));
			else {
				Entry<K, ?> data = new SavedEntry<K, Integer>(key, storage.storageValue(value));
				saveStoreHandler.sendMessage(Message.obtain(saveStoreHandler, AsyncMessages.MSG_STORE_INT.ordinal(), data));
			}
			onPreferenceChanged(key);
		}
	}

	/**
	 * Set a {@link StorableEnumString} value in the preferences
	 * @param key The name of the preference to modify.
	 * @param value The new value for the preference.
	 * @throws IllegalArgumentException if the key doesn't correspond to a {@link StorableEnumString} value
	 */
	@SuppressWarnings("unchecked")
	public <E extends Enum<? extends StorableEnumString<?>>> void putStringEnum(K key, E value) {
		if (!(key.defaultValue() instanceof StorableEnumString)) throw new IllegalArgumentException(key+" is not a StorableEnumString");

		StorableEnumString<E> storage = key.defaultValue();
		E oldValue;
		if (value==null) {
			oldValue = (E) values.remove(key);
		} else {
			Object v = values.put(key, storage.storageValue(value));
			if (v instanceof String)
				oldValue = ((StorableEnumString<E>) value).storageToValue((String) v);
			else
				oldValue = (E) v;
		}

		if (oldValue!=value) {
			if (value==null)
				saveStoreHandler.sendMessage(Message.obtain(saveStoreHandler, AsyncMessages.MSG_REMOVE_KEY.ordinal(), key));
			else {
				Entry<K, ?> data = new SavedEntry<K, String>(key, storage.storageValue(value));
				saveStoreHandler.sendMessage(Message.obtain(saveStoreHandler, AsyncMessages.MSG_STORE_STRING.ordinal(), data));
			}
			onPreferenceChanged(key);
		}
	}

	/**
	 * Check if a preference has a defined value
	 * @param key The name of the preference to check
	 * @return true if the preference has a value stored
	 */
	public boolean contains(K key) {
		return values.containsKey(key);
	}

	/**
	 * Remove the value of a preference, it will revert to its default value
	 * @param key The name of the preference to remove
	 */
	public void remove(final K key) {
		values.remove(key);

		saveStoreHandler.sendMessage(Message.obtain(saveStoreHandler, AsyncMessages.MSG_REMOVE_KEY.ordinal(), key));
	}

	/**
	 * Registers a callback to be invoked when a change happens to a preference.
	 * <p>Only a weak reference of the listener is kept.
	 * @param listener The callback that will run.
	 */
	public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
		registerOnSharedPreferenceChangeListener(listener, null);
	}

	/**
	 * Registers a callback to be invoked when a change happens to a preference and call it for all the preference listed in initValues.
	 * <p>Only a weak reference of the listener is kept.
	 * @param listener The callback that will run.
	 * @param initValues The list of preferences that should be notified on registration, can be null
	 */
	public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener, List<K> initValues) {
		if (initValues!=null) {
			for (K key : initValues) {
				listener.onSharedPreferenceChanged(this, key);
			}
		}
		for (WeakReference<OnSharedPreferenceChangeListener> wlisteners : changeListeners) {
			if (wlisteners.get()==null)
				changeListeners.remove(wlisteners);
			else if (wlisteners.get()==listener)
				return;
		}
		changeListeners.add(new WeakReference<SharedPreferencesTools.OnSharedPreferenceChangeListener>(listener));
	}

	/**
	 * Unregisters a previous callback.
	 * @param listener The callback that should be unregistered.
	 */
	public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
		for (WeakReference<OnSharedPreferenceChangeListener> wlisteners : changeListeners) {
			if (wlisteners.get()==null)
				changeListeners.remove(wlisteners);
			else if (wlisteners.get()==listener)
				changeListeners.remove(wlisteners);
		}
	}

	@Override
	public final void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (keySource!=null) {
			K prefKey = keySource.storageToKey(key);
			if (prefKey==null) {
				Log.e(LOG_TAG, "unknown preference key "+key);
			} else if (prefKey.defaultValue() instanceof Integer) {
				int newValue = sharedPreferences.getInt(key, (Integer) prefKey.defaultValue());
				putInt(prefKey, newValue);
			}
			else if (prefKey.defaultValue() instanceof Boolean) {
				boolean newValue = sharedPreferences.getBoolean(key, (Boolean) prefKey.defaultValue());
				putBoolean(prefKey, newValue);
			}
			else if (prefKey.defaultValue() instanceof Long) {
				long newValue = sharedPreferences.getLong(key, (Long) prefKey.defaultValue());
				putLong(prefKey, newValue);
			}
			else if (prefKey.defaultValue() instanceof Float) {
				float newValue = sharedPreferences.getFloat(key, (Float) prefKey.defaultValue());
				putFloat(prefKey, newValue);
			}
			else if (prefKey.defaultValue() instanceof StorableEnumString<?>) {
				String newValue = sharedPreferences.getString(key, ((StorableEnumString) prefKey.defaultValue()).storageValue((Enum) prefKey.defaultValue()));
				Enum newVal  =((StorableEnumString<?>) prefKey.defaultValue()).storageToValue(newValue);
				putStringEnum(prefKey, newVal);
			}
			else if (prefKey.defaultValue() instanceof StorableEnumInteger<?>) {
				int newValue = sharedPreferences.getInt(key, ((StorableEnumInteger) prefKey.defaultValue()).storageValue((Enum) prefKey.defaultValue()));
				Enum newVal  =((StorableEnumInteger<?>) prefKey.defaultValue()).storageToValue(newValue);
				putIntEnum(prefKey, newVal);
			}
			else if (prefKey.defaultValue()==null || prefKey.defaultValue() instanceof String) {
				String newValue = sharedPreferences.getString(key, (String) prefKey.defaultValue());
				putString(prefKey, newValue);
			} else {
				Log.e(LOG_TAG, "unknown preference type for "+key+" K:"+prefKey);
			}
		}
	}

	private void onPreferenceChanged(K key) {
		for (WeakReference<OnSharedPreferenceChangeListener> wlisteners : changeListeners) {
			if (wlisteners.get()==null)
				changeListeners.remove(wlisteners);
			else
				wlisteners.get().onSharedPreferenceChanged(this, key);
		}
	}
	
	private static class SavedEntry<K,V> implements Map.Entry<K, V> {

		private final K key;
		private V value;
		
		public SavedEntry(K key, V value) {
			this.key = key;
			this.value = value;
		}

		@Override
		public K getKey() {
			return key;
		}

		@Override
		public V getValue() {
			return value;
		}

		@Override
		public V setValue(V object) {
			value = object;
			return value;
		}
	}
}
