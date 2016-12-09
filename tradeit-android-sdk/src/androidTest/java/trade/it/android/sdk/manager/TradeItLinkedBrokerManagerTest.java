package trade.it.android.sdk.manager;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.LargeTest;
import android.util.Log;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import it.trade.tradeitapi.API.TradeItAccountLinker;
import it.trade.tradeitapi.exception.TradeItKeystoreServiceCreateKeyException;
import it.trade.tradeitapi.model.TradeItAuthenticateResponse;
import it.trade.tradeitapi.model.TradeItAvailableBrokersResponse;
import it.trade.tradeitapi.model.TradeItEnvironment;
import trade.it.android.sdk.model.TradeItCallBackImpl;
import trade.it.android.sdk.model.TradeItCallbackWithSecurityQuestionImpl;
import trade.it.android.sdk.model.TradeItErrorResult;
import trade.it.android.sdk.model.TradeItLinkedBroker;
import trade.it.android.sdk.model.TradeItSecurityQuestion;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class TradeItLinkedBrokerManagerTest {

    private CountDownLatch lock = new CountDownLatch(1);
    private TradeItLinkedBrokerManager linkedBrokerManager;
    private Context instrumentationCtx;

    @Before
    public void createTradeItLinkedBrokerManager() throws TradeItKeystoreServiceCreateKeyException {
        instrumentationCtx = InstrumentationRegistry.getContext();
        linkedBrokerManager = new TradeItLinkedBrokerManager(instrumentationCtx, new TradeItAccountLinker("tradeit-test-api-key", TradeItEnvironment.QA));
    }

    @Test
    public void getAvailableBrokers() throws InterruptedException {
        linkedBrokerManager.getAvailableBrokers(new TradeItCallBackImpl<List<TradeItAvailableBrokersResponse.Broker>>() {
            @Override
            public void onSuccess(List<TradeItAvailableBrokersResponse.Broker> brokerList) {
                assertThat("The broker list is not empty", brokerList.isEmpty(), is(false));
                lock.countDown();
            }

            @Override
            public void onError(TradeItErrorResult error) {
                assertThat("fails to get the broker list", error, nullValue());
                lock.countDown();
            }
        });
        boolean notExpired = lock.await(5000, TimeUnit.MILLISECONDS);
        assertThat("The call to getAvailableBrokers is not expired", notExpired, is(true));
    }

    @Test
    public void linkBrokerOldMethodAndAuthentication() throws InterruptedException {
        linkedBrokerManager.linkBroker("My accountLabel 1", "Dummy", "dummy", "dummy",  new TradeItCallBackImpl<TradeItLinkedBroker>() {
            @Override
            public void onSuccess(TradeItLinkedBroker linkedBroker) {
                assertThat("The linkedAccount userId is not null", linkedBroker.getLinkedAccount().userId , notNullValue());
                assertThat("The linkedAccount userToken is not null", linkedBroker.getLinkedAccount().userId , notNullValue());
                linkedBroker.authenticate(new TradeItCallbackWithSecurityQuestionImpl<List<TradeItAuthenticateResponse.Account>>() {
                    @Override
                    public void onSuccess(List<TradeItAuthenticateResponse.Account> accounts) {
                        assertThat("The authentication is successful",  accounts, notNullValue());
                        lock.countDown();
                    }

                    @Override
                    public void onSecurityQuestion(TradeItSecurityQuestion securityQuestion) {
                        assertThat("fails to authenticate",  securityQuestion, nullValue());
                        lock.countDown();
                    }

                    @Override
                    public void onError(TradeItErrorResult error) {
                        assertThat("fails to authenticate",  error, nullValue());
                        lock.countDown();
                    }
                });
            }

            @Override
            public void onError(TradeItErrorResult error) {
                Log.e(this.getClass().getName(), error.toString());
                assertThat("fails to link broker", error, nullValue());
                lock.countDown();
            }
        });
        boolean notExpired = lock.await(5000, TimeUnit.MILLISECONDS);
        assertThat("The call to linkBroker is not expired", notExpired, is(true));
    }

    @Test
    public void linkBrokerOldMethodAndSecurityQuestion() throws InterruptedException {
        linkedBrokerManager.linkBroker("My accountLabel 1", "Dummy", "dummySecurity", "dummy",  new TradeItCallBackImpl<TradeItLinkedBroker>() {
            @Override
            public void onSuccess(TradeItLinkedBroker linkedBroker) {
                assertThat("The linkedAccount userId is not null", linkedBroker.getLinkedAccount().userId , notNullValue());
                assertThat("The linkedAccount userToken is not null", linkedBroker.getLinkedAccount().userToken , notNullValue());
                linkedBroker.authenticate(new TradeItCallbackWithSecurityQuestionImpl<List<TradeItAuthenticateResponse.Account>>() {
                    @Override
                    public void onSuccess(List<TradeItAuthenticateResponse.Account> accounts) {
                        assertThat("fails to get security question",  accounts, nullValue());
                        lock.countDown();
                    }

                    @Override
                    public void onSecurityQuestion(TradeItSecurityQuestion securityQuestion) {
                        assertThat("security question is not null",  securityQuestion, notNullValue());
                        lock.countDown();
                    }

                    @Override
                    public void onError(TradeItErrorResult error) {
                        assertThat("fails to get security question",  error, nullValue());
                        lock.countDown();
                    }
                });
            }

            @Override
            public void onError(TradeItErrorResult error) {
                Log.e(this.getClass().getName(), error.toString());
                assertThat("fails to link broker", error, nullValue());
                lock.countDown();
            }
        });
        boolean notExpired = lock.await(5000, TimeUnit.MILLISECONDS);
        assertThat("The call to linkBroker is not expired", notExpired, is(true));
    }

    @Test
    public void getOAuthLoginPopupUrlForMobile() throws InterruptedException {
        linkedBrokerManager.getOAuthLoginPopupUrlForMobile("Dummy", "myinternalappcallback", new TradeItCallBackImpl<String>() {

            @Override
            public void onSuccess(String oAuthUrl) {
                assertThat("oAuthUrl is not null", oAuthUrl , notNullValue());
                lock.countDown();
            }

            @Override
            public void onError(TradeItErrorResult error) {
                Log.e(this.getClass().getName(), error.toString());
                assertThat("fails to get the Oauth login popup url", error, nullValue());
                lock.countDown();
            }
        });

        boolean notExpired = lock.await(5000, TimeUnit.MILLISECONDS);
        assertThat("The call to getOAuthLoginPopupUrlForMobile is not expired", notExpired, is(true));
    }
}
