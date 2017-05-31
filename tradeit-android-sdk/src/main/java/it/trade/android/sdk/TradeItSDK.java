package it.trade.android.sdk;


import android.content.Context;
import android.util.Log;

import it.trade.android.sdk.exceptions.TradeItSDKConfigurationException;
import it.trade.android.sdk.manager.TradeItLinkedBrokerManager;
import it.trade.android.sdk.model.RequestCookieProviderParcelable;
import it.trade.android.sdk.model.TradeItLinkedBrokerCache;
import it.trade.model.request.TradeItEnvironment;


public class TradeItSDK {
    private static TradeItSdkInstance instance;

    public static void configure(Context context, String apiKey, TradeItEnvironment environment) {
        if (instance == null) {
            instance = new TradeItSdkInstance(context, apiKey, environment);
        } else {
            Log.w("TradeItSDK", "Warning: TradeItSDK.configure() called multiple times. Ignoring.");
        }
    }

    public static void configure(Context context, String apiKey, TradeItEnvironment environment, RequestCookieProviderParcelable requestCookieProviderParcelable) {
        if (instance == null) {
            instance = new TradeItSdkInstance(context, apiKey, environment, requestCookieProviderParcelable);
        } else {
            Log.w("TradeItSDK", "Warning: TradeItSDK.configure() called multiple times. Ignoring.");
        }
    }

    public static void configure(Context context, String apiKey, TradeItEnvironment environment, String baseUrl) {
        if (instance == null) {
            instance = new TradeItSdkInstance(context, apiKey, environment, baseUrl);
        } else {
            Log.w("TradeItSDK", "Warning: TradeItSDK.configure() called multiple times. Ignoring.");
        }
    }

    public static void configure(Context context, String apiKey, TradeItEnvironment environment, String baseUrl, RequestCookieProviderParcelable requestCookieProviderParcelable) {
        if (instance == null) {
            instance = new TradeItSdkInstance(context, apiKey, environment, baseUrl, requestCookieProviderParcelable);
        } else {
            Log.w("TradeItSDK", "Warning: TradeItSDK.configure() called multiple times. Ignoring.");
        }
    }

    public static void clearConfig() {
        instance = null;
    }

    public static TradeItLinkedBrokerManager getLinkedBrokerManager() throws TradeItSDKConfigurationException {
        if (instance == null) {
            throw new TradeItSDKConfigurationException("ERROR: TradeItSDK.linkedBrokerManager referenced before calling TradeItSDK.configure()!");
        }
        return instance.getLinkedBrokerManager();
    }

    public static TradeItEnvironment getEnvironment() {
        if (instance == null) {
            throw new TradeItSDKConfigurationException("ERROR: TradeItSDK.linkedBrokerManager referenced before calling TradeItSDK.configure()!");
        }
        return instance.getEnvironment();
    }

    public static String getApiKey() {
        if (instance == null) {
            throw new TradeItSDKConfigurationException("ERROR: TradeItSDK.apiKey referenced before calling TradeItSDK.configure()!");
        }
        return instance.getApiKey();
    }

    public static TradeItLinkedBrokerCache getLinkedBrokerCache() {
        if (instance == null) {
            throw new TradeItSDKConfigurationException("ERROR: TradeItSDK.linkedBrokerCache referenced before calling TradeItSDK.configure()!");
        }
        return instance.getLinkedBrokerCache();
    }

    public static Context getContext() {
        if (instance == null) {
            throw new TradeItSDKConfigurationException("ERROR: TradeItSDK.context referenced before calling TradeItSDK.configure()!");
        }
        return instance.getContext();
    }
}
