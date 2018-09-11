package ee.ria.sso.flow.action;

import ee.ria.sso.AbstractTest;
import ee.ria.sso.Constants;
import ee.ria.sso.config.TaraResourceBundleMessageSource;
import ee.ria.sso.flow.AuthenticationFlowExecutionException;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.pac4j.core.context.Pac4jConstants;
import org.springframework.webflow.execution.Event;
import org.springframework.webflow.execution.RequestContext;

public abstract class AbstractAuthenticationActionTest {


    @Mock
    private TaraResourceBundleMessageSource messageSource;

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    RequestContext requestContext;

    abstract AbstractAuthenticationAction getAction();

    @Before
    public void setUp() {
        requestContext = AbstractTest.getRequestContext();
        requestContext.getExternalContext().getSessionMap().put(Pac4jConstants.REQUESTED_URL, "http://someurl");
    }

    @Test
    public void exceptionOccursDuringAuthentication() throws Exception {
        Mockito.when(messageSource.getMessage(Constants.MESSAGE_KEY_SESSION_EXPIRED)).thenReturn("Session expired");

        expectedEx.expect(AuthenticationFlowExecutionException.class);
        expectedEx.expect(new ExceptionCodeMatches(500, "error", "Unexpected exception during authentication action execution"));

        new AbstractAuthenticationAction() {
            @Override
            protected Event doAuthenticationExecute(RequestContext requestContext) {
                throw new IllegalStateException("Unexpected exception during authentication action execution");
            }
        }.doExecute(requestContext);
    }

    @Test
    public void callbackUrlMissingTest() throws Exception {
        Mockito.when(messageSource.getMessage(Constants.MESSAGE_KEY_SESSION_EXPIRED)).thenReturn("Session expired");

        expectedEx.expect(AuthenticationFlowExecutionException.class);
        expectedEx.expect(new ExceptionCodeMatches(401, "error", "Session expired"));

        getAction().doExecute(AbstractTest.getRequestContext());
    }

    class ExceptionCodeMatches extends TypeSafeMatcher<AuthenticationFlowExecutionException> {
        private int code;
        private String viewName;
        private String errorMessage;

        public ExceptionCodeMatches(int code, String viewName, String errorMessage) {
            this.code = code;
            this.viewName = viewName;
            this.errorMessage = errorMessage;
        }

        @Override
        protected boolean matchesSafely(AuthenticationFlowExecutionException item) {
            return item.getModelAndView().getStatus().value() == code && item.getModelAndView().getViewName().equals(viewName) && item.getModelAndView().getModel().get(Constants.ERROR_MESSAGE) != null && item.getModelAndView().getModel().get(Constants.ERROR_MESSAGE).equals(errorMessage);
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("expects code: ")
                    .appendValue(code).appendText(" and view: ").appendValue(viewName).appendText(" and msg: ").appendValue(errorMessage);
        }

        @Override
        protected void describeMismatchSafely(AuthenticationFlowExecutionException item, Description mismatchDescription) {
            mismatchDescription.appendText("was code: ")
                    .appendValue(item.getModelAndView().getStatus().value())
                    .appendText(", view name: ")
                    .appendValue(item.getModelAndView().getViewName())
                    .appendText(", msg: ")
                    .appendValue(item.getModelAndView().getModel().containsKey(Constants.ERROR_MESSAGE) ? item.getModelAndView().getModel().get(Constants.ERROR_MESSAGE) : "<missing error message in model!>");
        }
    }

}
