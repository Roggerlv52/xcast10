package com.rogger.xcast10.util;

import android.content.Context;
import android.content.SharedPreferences;

public class SharedPreference {

    private static final String PREF_NAME = "Xcast10Prefs";

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Salva uma String nas preferências.
     */
    public static void setString(Context context, String key, String value) {
        SharedPreferences.Editor editor = getPrefs(context).edit();
        editor.putString(key, value);
        editor.apply(); // apply() é assíncrono e mais seguro que commit()
    }

    /**
     * Recupera uma String das preferências.
     * Retorna null se a chave não existir.
     */
    public static String getString(Context context, String key) {
        return getPrefs(context).getString(key, null);
    }

    /**
     * Recupera uma String das preferências com um valor padrão customizado.
     */
    public static String getString(Context context, String key, String defaultValue) {
        return getPrefs(context).getString(key, defaultValue);
    }

    /**
     * Exclui um valor específico baseado na chave.
     */
    public static void remove(Context context, String key) {
        SharedPreferences.Editor editor = getPrefs(context).edit();
        editor.remove(key);
        editor.apply();
    }

    /**
     * Limpa todos os dados salvos no SharedPreferences do app.
     */
    public static void clearAll(Context context) {
        SharedPreferences.Editor editor = getPrefs(context).edit();
        editor.clear();
        editor.apply();
    }
}
