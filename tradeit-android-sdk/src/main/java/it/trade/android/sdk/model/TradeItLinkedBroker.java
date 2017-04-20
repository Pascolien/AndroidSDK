package it.trade.android.sdk.model;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import it.trade.android.sdk.internal.AuthenticationCallback;
import it.trade.tradeitapi.API.TradeItApiClient;
import it.trade.tradeitapi.model.TradeItAuthenticateResponse;
import it.trade.tradeitapi.model.TradeItBrokerAccount;
import it.trade.tradeitapi.model.TradeItErrorCode;
import it.trade.tradeitapi.model.TradeItLinkedLogin;
import retrofit2.Response;

public class TradeItLinkedBroker implements Parcelable {
    private transient TradeItApiClient apiClient;
    private transient TradeItLinkedLogin linkedLogin;
    private List<TradeItLinkedBrokerAccount> accounts = new ArrayList<>();
    private Date accountsLastUpdated;
    private TradeItErrorResult error;
    private transient TradeItLinkedBrokerCache linkedBrokerCache;

    public TradeItLinkedBroker(TradeItApiClient apiClient, TradeItLinkedLogin linkedLogin, TradeItLinkedBrokerCache linkedBrokerCache) {
        this.apiClient = apiClient;
        this.linkedLogin = linkedLogin;
        this.linkedBrokerCache = linkedBrokerCache;
        setUnauthenticated();
    }

    protected void cache() {
        linkedBrokerCache.cache(this);
    }

//    public void refreshBalancesForAllAccounts() {
//
//    }

    public void authenticate(final TradeItCallbackWithSecurityQuestion<List<TradeItLinkedBrokerAccount>> callback) {
        final TradeItLinkedBroker linkedBroker = this;
        this.apiClient.authenticate(this.linkedLogin, new AuthenticationCallback<TradeItAuthenticateResponse, List<TradeItLinkedBrokerAccount>>(callback, apiClient) {
            @Override
            public void onSuccessResponse(Response<TradeItAuthenticateResponse> response) {
                linkedBroker.error = null;
                TradeItAuthenticateResponse authResponse = response.body();
                List<TradeItBrokerAccount> accountsResult = authResponse.accounts;
                List<TradeItLinkedBrokerAccount> linkedBrokerAccounts = mapBrokerAccountsToLinkedBrokerAccounts(accountsResult);
                accounts = linkedBrokerAccounts;
                accountsLastUpdated = new Date();
                linkedBrokerCache.cache(linkedBroker);
                callback.onSuccess(linkedBrokerAccounts);
            }

            @Override
            public void onErrorResponse(TradeItErrorResult errorResult) {
                linkedBroker.error = errorResult;
                callback.onError(errorResult);
            }
        });
    }

    public void authenticateIfNeeded(final TradeItCallbackWithSecurityQuestion<List<TradeItLinkedBrokerAccount>> callback) {
        if (this.error != null && this.error.requiresAuthentication()) {
            this.authenticate(callback);
        } else if (this.error != null && (this.error.requiresRelink() || this.error.isConcurrentAuthenticationError() || this.error.isTooManyLoginAttemptsError())) {
            callback.onError(this.error);
        } else {
            callback.onSuccess(this.accounts);
        }
    }

    private void setUnauthenticated() {
        this.setError(new TradeItErrorResult(TradeItErrorCode.SESSION_EXPIRED, "Authentication required", Arrays.asList("Linked broker was not authenticated after initializing.")));
    }

    void setError(TradeItErrorResult error) {
        this.error = error;
    }

    @Override
    public String toString() {
        return "TradeItLinkedBroker{" +
                "TradeItLinkedLogin=" + this.linkedLogin.toString() +
                ", accounts=" + getAccounts().toString() +
                ", accountsLastUpdated=" + getAccountsLastUpdated() +
                '}';
    }

    public String getBrokerName() {
        return this.linkedLogin.broker;
    }

    TradeItApiClient getApiClient() {
        return this.apiClient;
    }

    public List<TradeItLinkedBrokerAccount> getAccounts() {
        return this.accounts;
    }

    public Date getAccountsLastUpdated() {
        return accountsLastUpdated;
    }

    void setAccounts(List<TradeItLinkedBrokerAccount> accounts) {
        this.accounts = accounts;
    }

    void setAccountsLastUpdated(Date accountsLastUpdated) {
        this.accountsLastUpdated = accountsLastUpdated;
    }

    private List<TradeItLinkedBrokerAccount> mapBrokerAccountsToLinkedBrokerAccounts(List<TradeItBrokerAccount> accounts) {
        List<TradeItLinkedBrokerAccount> linkedBrokerAccounts = new ArrayList<>();
        for (TradeItBrokerAccount account: accounts) {
            linkedBrokerAccounts.add(new TradeItLinkedBrokerAccount(this, account));
        }
        return linkedBrokerAccounts;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TradeItLinkedBroker that = (TradeItLinkedBroker) o;

        return linkedLogin.userId.equals(that.linkedLogin.userId);

    }

    @Override
    public int hashCode() {
        return linkedLogin.userId.hashCode();
    }

    public TradeItLinkedLogin getLinkedLogin() {
        return linkedLogin;
    }

    public void setLinkedLogin(TradeItLinkedLogin linkedLogin) {
        this.linkedLogin = linkedLogin;
    }


    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(this.apiClient, flags);
        dest.writeParcelable(this.linkedLogin, flags);
        dest.writeTypedList(this.accounts);
        dest.writeLong(this.accountsLastUpdated != null ? this.accountsLastUpdated.getTime() : -1);
        dest.writeParcelable(this.error, flags);
    }

    protected TradeItLinkedBroker(Parcel in) {
        this.apiClient = in.readParcelable(TradeItApiClient.class.getClassLoader());
        this.linkedLogin = in.readParcelable(TradeItLinkedLogin.class.getClassLoader());
        this.accounts = in.createTypedArrayList(TradeItLinkedBrokerAccount.CREATOR);
        long tmpAccountsLastUpdated = in.readLong();
        this.accountsLastUpdated = tmpAccountsLastUpdated == -1 ? null : new Date(tmpAccountsLastUpdated);
        this.error = in.readParcelable(TradeItErrorResult.class.getClassLoader());
    }

    public static final Parcelable.Creator<TradeItLinkedBroker> CREATOR = new Parcelable.Creator<TradeItLinkedBroker>() {
        @Override
        public TradeItLinkedBroker createFromParcel(Parcel source) {
            return new TradeItLinkedBroker(source);
        }

        @Override
        public TradeItLinkedBroker[] newArray(int size) {
            return new TradeItLinkedBroker[size];
        }
    };
}


