package it.trade.android.sdk.manager;

import android.util.Log;

import java.io.IOException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.SingleEmitter;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.exceptions.UndeliverableException;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.internal.operators.single.SingleCache;
import io.reactivex.observers.DisposableObserver;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.schedulers.Schedulers;
import it.trade.android.sdk.exceptions.TradeItDeleteLinkedLoginException;
import it.trade.android.sdk.exceptions.TradeItRetrieveLinkedLoginException;
import it.trade.android.sdk.exceptions.TradeItSaveLinkedLoginException;
import it.trade.android.sdk.exceptions.TradeItUpdateLinkedLoginException;
import it.trade.android.sdk.internal.TradeItKeystoreService;
import it.trade.android.sdk.model.TradeItApiClientParcelable;
import it.trade.android.sdk.model.TradeItCallBackCompletion;
import it.trade.android.sdk.model.TradeItCallbackWithSecurityQuestionAndCompletion;
import it.trade.android.sdk.model.TradeItErrorResultParcelable;
import it.trade.android.sdk.model.TradeItLinkedBrokerAccountParcelable;
import it.trade.android.sdk.model.TradeItLinkedBrokerCache;
import it.trade.android.sdk.model.TradeItLinkedBrokerParcelable;
import it.trade.android.sdk.model.TradeItLinkedLoginParcelable;
import it.trade.model.TradeItErrorResult;
import it.trade.model.TradeItSecurityQuestion;
import it.trade.model.callback.TradeItCallback;
import it.trade.model.callback.TradeItCallbackWithSecurityQuestionImpl;
import it.trade.model.reponse.TradeItAvailableBrokersResponse;
import it.trade.model.reponse.TradeItResponse;
import it.trade.model.request.TradeItLinkedLogin;

public class TradeItLinkedBrokerManager {

    private List<TradeItLinkedBrokerParcelable> linkedBrokers = new ArrayList<>();
    private TradeItKeystoreService keystoreService;
    private TradeItLinkedBrokerCache linkedBrokerCache;
    private TradeItApiClientParcelable apiClient;
    private static final String TAG = TradeItLinkedBrokerManager.class.getName();
    private SingleCache<List<TradeItAvailableBrokersResponse.Broker>> availableBrokersSingleCache = null;
    private Queue<TradeItCallback<List<TradeItAvailableBrokersResponse.Broker>>> availableBrokersCallbackQueue = new LinkedList<>();
    private final Object availableBrokersLock = new Object();

    public TradeItLinkedBrokerManager(TradeItApiClientParcelable apiClient, TradeItLinkedBrokerCache linkedBrokerCache, TradeItKeystoreService keystoreService) throws TradeItRetrieveLinkedLoginException {
        this.keystoreService = keystoreService;
        this.linkedBrokerCache = linkedBrokerCache;
        this.apiClient = apiClient;
        this.loadLinkedBrokersFromSharedPreferences();

        // Start fetching available brokers asap so that it is cached
        this.availableBrokersSingleCache = this.getAvailableBrokersSingleCache();

    }

    public synchronized void syncLinkedBrokers(List<TradeItLinkedLoginParcelable> linkedLoginParcelables) throws TradeItSaveLinkedLoginException, TradeItDeleteLinkedLoginException {
        List<TradeItLinkedBrokerParcelable> linkedBrokers = this.linkedBrokers;

        // Add missing linkedBrokers
        for (TradeItLinkedLoginParcelable linkedLoginParcelable: linkedLoginParcelables) {
            TradeItLinkedBrokerParcelable linkedBrokerParcelable = createNewLinkedBroker(linkedLoginParcelable);
            if (!linkedBrokers.contains(linkedBrokerParcelable)) {
                linkedBrokerCache.cache(linkedBrokerParcelable);
                linkedBrokers.add(linkedBrokerParcelable);
                keystoreService.saveLinkedLogin(linkedLoginParcelable, linkedLoginParcelable.label);
            }
        }

        // Remove non existing linkedBrokers
        for (TradeItLinkedBrokerParcelable linkedBroker: new ArrayList<>(linkedBrokers)) {
            if (!linkedLoginParcelables.contains(linkedBroker.getLinkedLogin())) {
                linkedBrokerCache.removeFromCache(linkedBroker);
                linkedBrokers.remove(linkedBroker);
                keystoreService.deleteLinkedLogin(linkedBroker.getLinkedLogin());
            }
        }

    }
    private void loadLinkedBrokersFromSharedPreferences() throws TradeItRetrieveLinkedLoginException {
        List<TradeItLinkedLoginParcelable> linkedLoginList = keystoreService.getLinkedLogins();
        for (TradeItLinkedLoginParcelable linkedLogin : linkedLoginList) {
            TradeItLinkedBrokerParcelable linkedBroker = createNewLinkedBroker(linkedLogin);
            linkedBrokerCache.syncFromCache(linkedBroker);
            linkedBrokers.add(linkedBroker);
        }
    }

    private TradeItLinkedBrokerParcelable createNewLinkedBroker(TradeItLinkedLoginParcelable linkedLoginParcelable) {
        TradeItApiClientParcelable
                apiClientParcelable = new TradeItApiClientParcelable(this.apiClient.getApiKey(), this.apiClient.getEnvironment(), this.apiClient.getRequestInterceptorParcelable());
        //provides a default token, so if the user doesn't authenticate before an other call, it will pass an expired token in order to get the session expired error
        apiClientParcelable.setSessionToken("invalid-default-token");

        return new TradeItLinkedBrokerParcelable(apiClientParcelable, linkedLoginParcelable, linkedBrokerCache);

    }

    public void authenticateAll(final TradeItCallbackWithSecurityQuestionAndCompletion callback) {
        RxJavaPlugins.setErrorHandler(new Consumer<Throwable>() {
            @Override
            public void accept(@NonNull Throwable e) throws Exception {
                if (e instanceof UndeliverableException) {
                    e = e.getCause();
                }
                if ((e instanceof IOException) || (e instanceof SocketException)) {
                    // fine, irrelevant network problem or API that throws on cancellation
                    return;
                }
                if (e instanceof InterruptedException) {
                    // fine, some blocking code was interrupted by a dispose call
                    return;
                }
                if ((e instanceof NullPointerException) || (e instanceof IllegalArgumentException)) {
                    // that's likely a bug in the application
                    Thread.currentThread().getUncaughtExceptionHandler()
                            .uncaughtException(Thread.currentThread(), e);
                    return;
                }
                if (e instanceof IllegalStateException) {
                    // that's a bug in RxJava or in a custom operator
                    Thread.currentThread().getUncaughtExceptionHandler()
                            .uncaughtException(Thread.currentThread(), e);
                    return;
                }
                Log.w(TAG, "Undeliverable exception received, not sure what to do", e);
            }
        });

        Observable.fromIterable(this.getLinkedBrokers())
            .observeOn(AndroidSchedulers.mainThread(), true)
            .subscribeOn(Schedulers.io())
            .flatMapSingle(new Function<TradeItLinkedBrokerParcelable, Single<TradeItLinkedBrokerParcelable>>() {
                @Override
                public Single<TradeItLinkedBrokerParcelable> apply(@NonNull final TradeItLinkedBrokerParcelable linkedBrokerParcelable) throws Exception {
                    return Single.create(new SingleOnSubscribe<TradeItLinkedBrokerParcelable>() {
                        @Override
                        public void subscribe(@NonNull final SingleEmitter<TradeItLinkedBrokerParcelable> emmiter) throws Exception {
                            linkedBrokerParcelable.authenticateIfNeeded(new TradeItCallbackWithSecurityQuestionImpl<List<TradeItLinkedBrokerAccountParcelable>>() {
                                @Override
                                public void onSecurityQuestion(TradeItSecurityQuestion securityQuestion) {
                                    Log.d(TAG, "Single onSecurityQuestion");
                                    callback.onSecurityQuestion(securityQuestion, this);
                                }

                                @Override
                                public void onSuccess(List<TradeItLinkedBrokerAccountParcelable> type) {
                                    Log.d(TAG, "Single onSuccess");
                                    emmiter.onSuccess(linkedBrokerParcelable);
                                }

                                @Override
                                public void onError(TradeItErrorResult error) {
                                    Log.d(TAG, "Single onError" + error.toString());
                                    emmiter.onError(new RuntimeException(error.toString()));
                                }

                                @Override
                                public void cancelSecurityQuestion() {
                                    Log.d(TAG, "Single cancelSecurityQuestion");
                                    emmiter.onSuccess(linkedBrokerParcelable);
                                }
                            });
                        }
                    });
                }
            }, true)
            .subscribe(new DisposableObserver() {
                @Override
                public void onNext(@NonNull Object linkedBrokerParcelable) {
                    Log.d(TAG, "authenticateAll - onNext: " + linkedBrokerParcelable);
                }

                @Override
                public void onError(@NonNull Throwable e) {
                    Log.d(TAG, "authenticateAll - onError " + e.getMessage());
                    callback.onFinished();
                }

                @Override
                public void onComplete() {
                    Log.d(TAG, "authenticateAll - Oncomplete");
                    callback.onFinished();
                }
            });
    }

    public void refreshAccountBalances(final TradeItCallBackCompletion callback) {
        Observable.fromIterable(this.getLinkedBrokers())
                .observeOn(AndroidSchedulers.mainThread(), true)
                .subscribeOn(Schedulers.io())
                .flatMapSingle(new Function<TradeItLinkedBrokerParcelable, Single<TradeItLinkedBrokerParcelable>>() {
                    @Override
                    public Single<TradeItLinkedBrokerParcelable> apply(@NonNull final TradeItLinkedBrokerParcelable linkedBrokerParcelable) throws Exception {
                        return Single.create(new SingleOnSubscribe<TradeItLinkedBrokerParcelable>() {
                            @Override
                            public void subscribe(@NonNull final SingleEmitter<TradeItLinkedBrokerParcelable> emmiter) throws Exception {
                                linkedBrokerParcelable.refreshAccountBalances(new TradeItCallBackCompletion() {
                                    @Override
                                    public void onFinished() {
                                        emmiter.onSuccess(linkedBrokerParcelable);
                                    }
                                });
                            }
                        });
                    }
                }, true)
                .subscribe(new DisposableObserver() {
                @Override
                public void onNext(@NonNull Object linkedBrokerParcelable) {
                    Log.d(TAG, "refreshAccountBalances onNext: " + linkedBrokerParcelable);
                }

                @Override
                public void onError(@NonNull Throwable e) {
                    Log.d(TAG, "refreshAccountBalances onError: " + e);
                    callback.onFinished();
                }

                @Override
                public void onComplete() {
                    Log.d(TAG, "refreshAccountBalances oncomplete");
                    callback.onFinished();
                }
            });
    }

    public void getAvailableBrokers(final TradeItCallback<List<TradeItAvailableBrokersResponse.Broker>> callback) {
        this.getAvailableBrokersSingleCache().subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(
                new Consumer<List<TradeItAvailableBrokersResponse.Broker>>() {
                    @Override
                    public void accept(@NonNull List<TradeItAvailableBrokersResponse.Broker> brokers) throws Exception {
                        callback.onSuccess(brokers);
                    }
                },
                new Consumer<Throwable>() {
                    @Override
                    public void accept(@NonNull Throwable throwable) throws Exception {
                        Log.e(TAG, "getAvailableBrokers error: " + throwable.getMessage());
                        availableBrokersSingleCache = null;
                        callback.onError(new TradeItErrorResultParcelable(throwable));
                    }
                }
        );
    }

    private SingleCache<List<TradeItAvailableBrokersResponse.Broker>> getAvailableBrokersSingleCache() {
        if (this.availableBrokersSingleCache != null) {
            return availableBrokersSingleCache;
        } else {
            SingleCache singleCache =  new SingleCache<>(Single.create(new SingleOnSubscribe<List<TradeItAvailableBrokersResponse.Broker>>() {
                @Override
                public void subscribe(@NonNull final SingleEmitter<List<TradeItAvailableBrokersResponse.Broker>> emitter) throws Exception {
                    RxJavaPlugins.setErrorHandler(new Consumer<Throwable>() {
                        @Override
                        public void accept(@NonNull Throwable e) throws Exception {
                            if (e instanceof UndeliverableException) {
                                e = e.getCause();
                            }
                            if ((e instanceof IOException) || (e instanceof SocketException)) {
                                // fine, irrelevant network problem or API that throws on cancellation
                                return;
                            }
                            if (e instanceof InterruptedException) {
                                // fine, some blocking code was interrupted by a dispose call
                                return;
                            }
                            if ((e instanceof NullPointerException) || (e instanceof IllegalArgumentException)) {
                                // that's likely a bug in the application
                                Thread.currentThread().getUncaughtExceptionHandler()
                                        .uncaughtException(Thread.currentThread(), e);
                                return;
                            }
                            if (e instanceof IllegalStateException) {
                                // that's a bug in RxJava or in a custom operator
                                Thread.currentThread().getUncaughtExceptionHandler()
                                        .uncaughtException(Thread.currentThread(), e);
                                return;
                            }
                            Log.w(TAG, "Undeliverable exception received, not sure what to do", e);
                        }
                    });
                    apiClient.getAvailableBrokers(new TradeItCallback<List<TradeItAvailableBrokersResponse.Broker>>() {
                        @Override
                        public void onSuccess(List<TradeItAvailableBrokersResponse.Broker> brokersList) {
                            emitter.onSuccess(brokersList);
                        }

                        @Override
                        public void onError(TradeItErrorResult error) {
                            Log.e(TAG, error.toString());
                            emitter.onError(new RuntimeException(error.toString()));
                        }
                    });
                }
            }));
            singleCache.subscribe();
            return singleCache;
        }
    }

    public void getOAuthLoginPopupUrl(String broker, String deepLinkCallback, final TradeItCallback<String> callback) {
        apiClient.getOAuthLoginPopupUrlForMobile(broker, deepLinkCallback, new TradeItCallback<String>() {
            @Override
            public void onSuccess(String oAuthURL) {
                callback.onSuccess(oAuthURL);
            }

            @Override
            public void onError(TradeItErrorResult error) {
                TradeItErrorResultParcelable errorResultParcelable = new TradeItErrorResultParcelable(error);
                callback.onError(errorResultParcelable);
            }
        });
    }

    public void getOAuthLoginPopupForTokenUpdateUrl(TradeItLinkedBrokerParcelable linkedBroker, String deepLinkCallback, final TradeItCallback<String> callback) {
        apiClient.getOAuthLoginPopupUrlForTokenUpdate(linkedBroker.getBrokerName(), linkedBroker.getLinkedLogin().userId, deepLinkCallback, new TradeItCallback<String>() {
            @Override
            public void onSuccess(String oAuthUrl) {
                callback.onSuccess(oAuthUrl);
            }

            @Override
            public void onError(TradeItErrorResult error) {
                TradeItErrorResultParcelable errorResultParcelable = new TradeItErrorResultParcelable(error);
                callback.onError(errorResultParcelable);
            }
        });
    }

    public void getOAuthLoginPopupForTokenUpdateUrlByUserId(String userId, String deepLinkCallback, final TradeItCallback<String> callback) {
        TradeItLinkedBrokerParcelable linkedBroker = getLinkedBrokerByUserId(userId);
        if (linkedBroker == null) {
            callback.onError(new TradeItErrorResultParcelable("getOAuthLoginPopupForTokenUpdateUrlByUserId error", "No linked broker was found for userId " + userId));
        } else {
            getOAuthLoginPopupForTokenUpdateUrl(linkedBroker, deepLinkCallback, callback);
        }
    }

    public void linkBrokerWithOauthVerifier(final String accountLabel, String oAuthVerifier, final TradeItCallback<TradeItLinkedBrokerParcelable> callback) {
        apiClient.linkBrokerWithOauthVerifier(oAuthVerifier, new TradeItCallback<TradeItLinkedLogin>() {
            @Override
            public void onSuccess(TradeItLinkedLogin linkedLogin) {
                try {
                    TradeItLinkedLoginParcelable linkedLoginParcelable = new TradeItLinkedLoginParcelable(linkedLogin);
                    TradeItApiClientParcelable
                            apiClientParcelable = new TradeItApiClientParcelable(apiClient.getApiKey(), apiClient.getEnvironment(), apiClient.getRequestInterceptorParcelable());
                    TradeItLinkedBrokerParcelable linkedBroker = new TradeItLinkedBrokerParcelable(apiClientParcelable, linkedLoginParcelable, linkedBrokerCache);
                    int indexOfLinkedBroker = linkedBrokers.indexOf(linkedBroker);
                    if (indexOfLinkedBroker != -1) { //linked broker for this user id already exists, this is a token update
                        TradeItLinkedBrokerParcelable linkedBrokerToUpdate = linkedBrokers.get(indexOfLinkedBroker);
                        linkedBrokerToUpdate.setLinkedLogin(linkedLoginParcelable);
                        linkedBroker = linkedBrokerToUpdate;
                        keystoreService.updateLinkedLogin(linkedLoginParcelable);
                    } else {
                        keystoreService.saveLinkedLogin(linkedLoginParcelable, accountLabel);
                        linkedBrokers.add(linkedBroker);
                    }
                    callback.onSuccess(linkedBroker);
                } catch (TradeItSaveLinkedLoginException e) {
                    Log.e(this.getClass().getName(), e.getMessage(), e);
                    callback.onError(new TradeItErrorResultParcelable("Failed to link broker", e.getMessage()));
                } catch (TradeItUpdateLinkedLoginException e) {
                    Log.e(this.getClass().getName(), e.getMessage(), e);
                    callback.onError(new TradeItErrorResultParcelable("Failed to update link broker", e.getMessage()));
                }
            }

            @Override
            public void onError(TradeItErrorResult error) {
                TradeItErrorResultParcelable errorResultParcelable = new TradeItErrorResultParcelable(error);
                callback.onError(errorResultParcelable);
            }
        });
    }

    public void unlinkBroker(final TradeItLinkedBrokerParcelable linkedBroker, final TradeItCallback callback) {
        try {
            keystoreService.deleteLinkedLogin(linkedBroker.getLinkedLogin());
            linkedBrokers.remove(linkedBroker);
            linkedBrokerCache.removeFromCache(linkedBroker);
            apiClient.unlinkBrokerAccount(linkedBroker.getLinkedLogin(), new TradeItCallback<TradeItResponse>() {
                @Override
                public void onSuccess(TradeItResponse response) {
                    callback.onSuccess(response);
                }

                @Override
                public void onError(TradeItErrorResult error) {
                    TradeItErrorResultParcelable errorResultParcelable = new TradeItErrorResultParcelable(error);
                    callback.onError(errorResultParcelable);
                }
            });
        } catch (TradeItDeleteLinkedLoginException e) {
            Log.e(this.getClass().getName(), e.getMessage(), e);
            callback.onError(new TradeItErrorResultParcelable("Unlink broker error", "An error occured while unlinking the broker, please try again later"));
        }
    }

    public TradeItLinkedBrokerParcelable getLinkedBrokerByUserId(String userId) {
        TradeItLinkedBrokerParcelable linkedBrokerParcelable = null;
        for (TradeItLinkedBrokerParcelable linkedBroker : this.getLinkedBrokers()) {
            if (linkedBroker.getLinkedLogin().userId.equals(userId)) {
                linkedBrokerParcelable = linkedBroker;
                break;
            }
        }
        return linkedBrokerParcelable;
    }

    public void unlinkBrokerByUserId(String userId, final TradeItCallback callback) {
        TradeItLinkedBrokerParcelable linkedBroker = getLinkedBrokerByUserId(userId);
        if (linkedBroker == null) {
            callback.onError(new TradeItErrorResultParcelable("Unlink broker error", "No linked broker was found for userId " + userId));
        } else {
            unlinkBroker(linkedBroker, callback);
        }
    }

    /**
     * @deprecated Use the new OAuth flow and the #linkBrokerWithOauthVerifier(String, String, String, TradeItCallback) method instead
     */
    @Deprecated
    public void linkBroker(final String accountLabel, String broker, String username, String password, final TradeItCallback<TradeItLinkedBrokerParcelable> callback) {
        apiClient.linkBrokerAccount(username, password, broker, new TradeItCallback<TradeItLinkedLogin>() {
            @Override
            public void onSuccess(TradeItLinkedLogin linkedLogin) {
                TradeItLinkedLoginParcelable linkedLoginParcelable = new TradeItLinkedLoginParcelable(linkedLogin);
                try {
                    keystoreService.saveLinkedLogin(linkedLoginParcelable, accountLabel);
                    TradeItApiClientParcelable
                            apiClientParcelable = new TradeItApiClientParcelable(apiClient.getApiKey(), apiClient.getEnvironment(), apiClient.getRequestInterceptorParcelable());
                    TradeItLinkedBrokerParcelable linkedBroker = new TradeItLinkedBrokerParcelable(apiClientParcelable, linkedLoginParcelable, linkedBrokerCache);
                    linkedBrokers.add(linkedBroker);
                    callback.onSuccess(linkedBroker);
                } catch (TradeItSaveLinkedLoginException e) {
                    Log.e(this.getClass().getName(), e.getMessage(), e);
                    callback.onError(new TradeItErrorResult("Failed to link broker", e.getMessage()));
                }
            }

            @Override
            public void onError(TradeItErrorResult error) {
                TradeItErrorResultParcelable errorResultParcelable = new TradeItErrorResultParcelable(error);
                callback.onError(errorResultParcelable);
            }
        });
    }

    public List<TradeItLinkedBrokerParcelable> getLinkedBrokers() {
        return new ArrayList<>(linkedBrokers);
    }
}
